package tests

import (
	"context"
	"net"
	"path/filepath"
	"testing"
	"time"

	"foolcardgame/server/internal/grpcserver"
	"foolcardgame/server/internal/hub"
	"foolcardgame/server/internal/logging"
	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/test/bufconn"
)

func testHub(t *testing.T) *hub.Hub {
	t.Helper()
	dir := t.TempDir()
	db := openTestDB(t)
	ensureMigrated(t, db)
	appLog, err := logging.New(dir, true)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = appLog.Close() })
	return hub.New(hub.Config{
		QuickMinPlayers:   2,
		QuickMaxPlayers:   4,
		QuickFillWindow:   80 * time.Millisecond,
		QuickQueueTimeout: 150 * time.Millisecond,
		LogDir:            dir,
		Games:             &store.Games{DB: db},
		Logger:            appLog,
	})
}

func startBufServerWithHub(t *testing.T, h *hub.Hub) (pb.AuthClient, pb.MatchmakingClient, pb.GameSessionClient, func()) {
	t.Helper()
	db := openTestDB(t)
	ensureMigrated(t, db)
	accounts := &store.Accounts{DB: db}
	if h == nil {
		logDir := t.TempDir()
		appLog, err := logging.New(logDir, true)
		if err != nil {
			t.Fatal(err)
		}
		h = hub.New(hub.Config{
			QuickMinPlayers: 2, QuickMaxPlayers: 4,
			QuickFillWindow: 80 * time.Millisecond, QuickQueueTimeout: 150 * time.Millisecond,
			LogDir: logDir, Games: &store.Games{DB: db}, Logger: appLog,
		})
	} else if h != nil {
		// подставим Games если тесты создали hub без БД
	}

	lis := bufconn.Listen(bufSize)
	srv := grpcserver.New(grpcserver.Options{Accounts: accounts, Hub: h})
	go func() { _ = srv.Serve(lis) }()

	dialer := func(context.Context, string) (net.Conn, error) {
		return lis.Dial()
	}
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(dialer),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	cleanup := func() {
		_ = conn.Close()
		srv.Stop()
		_ = lis.Close()
	}
	return pb.NewAuthClient(conn), pb.NewMatchmakingClient(conn), pb.NewGameSessionClient(conn), cleanup
}

func authCtx(token string) (context.Context, context.CancelFunc) {
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	ctx = metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer "+token))
	return ctx, cancel
}

func recvUntil(t *testing.T, stream pb.GameSession_SessionClient, timeout time.Duration, pred func(*pb.ServerMessage) bool) *pb.ServerMessage {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		_ = stream.Context().Err()
		msg, err := stream.Recv()
		if err != nil {
			t.Fatalf("Recv: %v", err)
		}
		if pred(msg) {
			return msg
		}
	}
	t.Fatal("timeout waiting for message")
	return nil
}

func TestQuickMatch_FillWindowAutostart(t *testing.T) {
	h := testHub(t)
	auth, _, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()

	tok1, _ := loginRegister(t, auth, "QM1_"+t.Name())
	tok2, _ := loginRegister(t, auth, "QM2_"+t.Name())
	tok3, _ := loginRegister(t, auth, "QM3_"+t.Name())

	ctx1, c1 := authCtx(tok1)
	defer c1()
	ctx2, c2 := authCtx(tok2)
	defer c2()
	ctx3, c3 := authCtx(tok3)
	defer c3()

	s1, err := session.Session(ctx1)
	if err != nil {
		t.Fatal(err)
	}
	s2, err := session.Session(ctx2)
	if err != nil {
		t.Fatal(err)
	}
	s3, err := session.Session(ctx3)
	if err != nil {
		t.Fatal(err)
	}

	_ = s1.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "A", AvatarId: 1}}})
	m := recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		q := m.GetQueueState()
		return q != nil && q.WaitingCount == 1 && q.Phase == pb.QueuePhase_SEARCHING
	})
	_ = m

	_ = s2.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "B", AvatarId: 2}}})
	recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		q := m.GetQueueState()
		return q != nil && q.Phase == pb.QueuePhase_FILLING && q.WaitingCount >= 2
	})

	// 3-й в окне fill.
	_ = s3.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "C", AvatarId: 3}}})

	started := recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetMatchStarted() != nil
	})
	gameID := started.GetMatchStarted().GameId
	if gameID == "" {
		t.Fatal("empty game id")
	}
	recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		gs := m.GetGameState()
		return gs != nil && gs.GameId == gameID && gs.Phase == pb.GamePhase_IN_PROGRESS
	})
	recvUntil(t, s2, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetMatchStarted() != nil || m.GetGameState() != nil
	})
	recvUntil(t, s3, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetMatchStarted() != nil || m.GetGameState() != nil
	})

	if path := h.GameLogPath(gameID); path == "" {
		t.Fatal("ожидался путь game log")
	} else if _, err := filepath.Abs(path); err != nil {
		t.Fatal(err)
	}
}

func TestQuickMatch_MaxStartsImmediately(t *testing.T) {
	h := hub.New(hub.Config{
		QuickMinPlayers:   2,
		QuickMaxPlayers:   2,
		QuickFillWindow:   5 * time.Second,
		QuickQueueTimeout: 5 * time.Second,
		LogDir:            t.TempDir(),
	})
	auth, _, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()

	tok1, _ := loginRegister(t, auth, "Max1_"+t.Name())
	tok2, _ := loginRegister(t, auth, "Max2_"+t.Name())
	ctx1, c1 := authCtx(tok1)
	defer c1()
	ctx2, c2 := authCtx(tok2)
	defer c2()
	s1, _ := session.Session(ctx1)
	s2, _ := session.Session(ctx2)

	_ = s1.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "A"}}})
	_ = s2.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "B"}}})

	recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetMatchStarted() != nil
	})
}

func TestQuickMatch_QueueTimeout(t *testing.T) {
	h := testHub(t)
	auth, _, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()

	tok, _ := loginRegister(t, auth, "TO_"+t.Name())
	ctx, cancel := authCtx(tok)
	defer cancel()
	s, _ := session.Session(ctx)
	_ = s.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "Solo"}}})
	recvUntil(t, s, 2*time.Second, func(m *pb.ServerMessage) bool {
		e := m.GetError()
		return e != nil && e.Code == "QUEUE_TIMEOUT"
	})
}

func TestQuickMatch_LeaveFromQueue(t *testing.T) {
	h := testHub(t)
	auth, _, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()

	tok1, _ := loginRegister(t, auth, "LQ1_"+t.Name())
	tok2, _ := loginRegister(t, auth, "LQ2_"+t.Name())
	ctx1, c1 := authCtx(tok1)
	defer c1()
	ctx2, c2 := authCtx(tok2)
	defer c2()
	s1, _ := session.Session(ctx1)
	s2, _ := session.Session(ctx2)

	_ = s1.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "A"}}})
	recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetQueueState() != nil && m.GetQueueState().WaitingCount == 1
	})
	_ = s1.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Leave{Leave: &pb.Leave{}}})
	recvUntil(t, s1, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetLeftAck() != nil
	})

	_ = s2.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "B"}}})
	recvUntil(t, s2, 2*time.Second, func(m *pb.ServerMessage) bool {
		q := m.GetQueueState()
		return q != nil && q.WaitingCount == 1 && q.Phase == pb.QueuePhase_SEARCHING
	})
}

func TestQuickMatch_FillingBackToSearching(t *testing.T) {
	h := hub.New(hub.Config{
		QuickMinPlayers:   2,
		QuickMaxPlayers:   4,
		QuickFillWindow:   500 * time.Millisecond,
		QuickQueueTimeout: 5 * time.Second,
		LogDir:            t.TempDir(),
	})
	ch1 := make(chan *pb.ServerMessage, 8)
	ch2 := make(chan *pb.ServerMessage, 8)
	_, err := h.EnqueueQuickMatch("a1", "A", 0, ch1)
	if err != nil {
		t.Fatal(err)
	}
	pid2, err := h.EnqueueQuickMatch("a2", "B", 0, ch2)
	if err != nil {
		t.Fatal(err)
	}
	if h.QueuePhaseForTest() != pb.QueuePhase_FILLING {
		t.Fatalf("want FILLING, got %v", h.QueuePhaseForTest())
	}
	if !h.LeaveQueue("a2", pid2) {
		t.Fatal("leave failed")
	}
	if h.QueuePhaseForTest() != pb.QueuePhase_SEARCHING {
		t.Fatalf("want SEARCHING after leave, got %v", h.QueuePhaseForTest())
	}
	if h.QueueLenForTest() != 1 {
		t.Fatalf("want 1 in queue, got %d", h.QueueLenForTest())
	}
}

func TestPrivateLobby_KickAndStartHostOnly(t *testing.T) {
	h := testHub(t)
	auth, mm, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()

	tokH, _ := loginRegister(t, auth, "Host_"+t.Name())
	tokG, _ := loginRegister(t, auth, "Guest_"+t.Name())

	ctxH, cH := authCtx(tokH)
	defer cH()
	ctxG, cG := authCtx(tokG)
	defer cG()

	created, err := mm.CreateGame(ctxH, &pb.PlayerProfile{Username: "Host", AvatarId: 1})
	if err != nil {
		t.Fatal(err)
	}
	joined, err := mm.JoinGame(ctxG, &pb.JoinGameRequest{Code: created.AccessCode, Username: "Guest", AvatarId: 2})
	if err != nil {
		t.Fatal(err)
	}
	if joined.GameId != created.GameId {
		t.Fatal("game id mismatch")
	}

	sh, _ := session.Session(ctxH)
	sg, _ := session.Session(ctxG)
	_ = sh.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Subscribe{Subscribe: &pb.Subscribe{
		GameId: created.GameId, PlayerId: created.PlayerId,
	}}})
	_ = sg.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Subscribe{Subscribe: &pb.Subscribe{
		GameId: joined.GameId, PlayerId: joined.PlayerId,
	}}})
	recvUntil(t, sh, 2*time.Second, func(m *pb.ServerMessage) bool { return m.GetRoomState() != nil })
	recvUntil(t, sg, 2*time.Second, func(m *pb.ServerMessage) bool { return m.GetRoomState() != nil })

	// Non-host StartGame → Error.
	_ = sg.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_StartGame{StartGame: &pb.StartGame{}}})
	recvUntil(t, sg, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetError() != nil
	})

	_ = sh.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_StartGame{StartGame: &pb.StartGame{}}})
	recvUntil(t, sh, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetMatchStarted() != nil
	})
	recvUntil(t, sg, 2*time.Second, func(m *pb.ServerMessage) bool {
		return m.GetMatchStarted() != nil || m.GetGameState() != nil
	})
}

func TestPrivateLobby_Kick(t *testing.T) {
	h := testHub(t)
	auth, mm, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()

	tokH, _ := loginRegister(t, auth, "KickH_"+t.Name())
	tokG, _ := loginRegister(t, auth, "KickG_"+t.Name())
	ctxH, cH := authCtx(tokH)
	defer cH()
	ctxG, cG := authCtx(tokG)
	defer cG()

	created, _ := mm.CreateGame(ctxH, &pb.PlayerProfile{Username: "H"})
	joined, _ := mm.JoinGame(ctxG, &pb.JoinGameRequest{Code: created.AccessCode, Username: "G"})

	sh, _ := session.Session(ctxH)
	sg, _ := session.Session(ctxG)
	_ = sh.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Subscribe{Subscribe: &pb.Subscribe{
		GameId: created.GameId, PlayerId: created.PlayerId,
	}}})
	_ = sg.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Subscribe{Subscribe: &pb.Subscribe{
		GameId: joined.GameId, PlayerId: joined.PlayerId,
	}}})
	recvUntil(t, sh, 2*time.Second, func(m *pb.ServerMessage) bool { return m.GetRoomState() != nil })
	recvUntil(t, sg, 2*time.Second, func(m *pb.ServerMessage) bool { return m.GetRoomState() != nil })

	_ = sh.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Kick{Kick: &pb.Kick{PlayerId: joined.PlayerId}}})
	recvUntil(t, sg, 2*time.Second, func(m *pb.ServerMessage) bool { return m.GetKicked() != nil })
}

func TestHub_BroadcastPlayerChannel(t *testing.T) {
	h := testHub(t)
	ch1 := make(chan *pb.ServerMessage, 8)
	ch2 := make(chan *pb.ServerMessage, 8)

	created, err := h.CreatePrivate("acc1", "Host", 1)
	if err != nil {
		t.Fatal(err)
	}
	joined, err := h.JoinPrivate("acc2", created.AccessCode, "Guest", 2)
	if err != nil {
		t.Fatal(err)
	}
	if err := h.Subscribe("acc1", created.GameId, created.PlayerId, ch1); err != nil {
		t.Fatal(err)
	}
	if err := h.Subscribe("acc2", joined.GameId, joined.PlayerId, ch2); err != nil {
		t.Fatal(err)
	}

	// Join уже сделал broadcast RoomState — ch1 мог получить обновление при Join до Subscribe.
	// Kick гостя → broadcast RoomState хосту.
	tok3 := "acc3"
	j3, err := h.JoinPrivate(tok3, created.AccessCode, "G3", 3)
	if err != nil {
		t.Fatal(err)
	}
	ch3 := make(chan *pb.ServerMessage, 8)
	_ = h.Subscribe(tok3, j3.GameId, j3.PlayerId, ch3)

	drain := func(ch <-chan *pb.ServerMessage) {
		for {
			select {
			case <-ch:
			default:
				return
			}
		}
	}
	drain(ch1)
	drain(ch2)

	if err := h.Kick("acc1", created.GameId, created.PlayerId, j3.PlayerId); err != nil {
		t.Fatal(err)
	}

	sawRoom := false
	deadline := time.After(2 * time.Second)
	for !sawRoom {
		select {
		case m := <-ch1:
			if m.GetRoomState() != nil {
				sawRoom = true
			}
		case <-deadline:
			t.Fatal("host не получил RoomState broadcast")
		}
	}
	_ = ch2
}

func TestLeave_VsDisconnect(t *testing.T) {
	h := testHub(t)
	created, _ := h.CreatePrivate("a1", "H", 0)
	joined, _ := h.JoinPrivate("a2", created.AccessCode, "G", 0)
	ch1 := make(chan *pb.ServerMessage, 4)
	ch2 := make(chan *pb.ServerMessage, 4)
	_ = h.Subscribe("a1", created.GameId, created.PlayerId, ch1)
	_ = h.Subscribe("a2", joined.GameId, joined.PlayerId, ch2)
	_ = h.StartGame("a1", created.GameId, created.PlayerId)

	// Drain start messages.
	time.Sleep(20 * time.Millisecond)
	for {
		select {
		case <-ch1:
		case <-ch2:
		default:
			goto doneDrain
		}
	}
doneDrain:

	h.Disconnect("a2", created.GameId, joined.PlayerId)
	room := h.SessionRoom(created.GameId)
	var st pb.PlayerStatus
	for _, p := range room.Players {
		if p.PlayerID == joined.PlayerId {
			st = p.Status
		}
	}
	if st != pb.PlayerStatus_DISCONNECTED {
		t.Fatalf("want DISCONNECTED, got %v", st)
	}
	if ml := h.MatchLogFor(created.GameId); ml != nil && len(ml.VoluntaryLeaves) != 0 {
		t.Fatal("disconnect не должен писать VoluntaryLeaves")
	}

	ch2b := make(chan *pb.ServerMessage, 4)
	if err := h.Subscribe("a2", created.GameId, joined.PlayerId, ch2b); err != nil {
		t.Fatal(err)
	}
	if err := h.Leave("a2", created.GameId, joined.PlayerId); err != nil {
		t.Fatal(err)
	}
	ml := h.MatchLogFor(created.GameId)
	if ml == nil || ml.VoluntaryLeaves[joined.PlayerId].IsZero() {
		t.Fatal("Leave должен попасть в VoluntaryLeaves")
	}
}

func TestLeave_QueueNoGamesInsert(t *testing.T) {
	h := testHub(t)
	auth, _, session, cleanup := startBufServerWithHub(t, h)
	defer cleanup()
	db := openTestDB(t)

	var before int
	if err := db.QueryRow(`SELECT COUNT(*) FROM games`).Scan(&before); err != nil {
		t.Fatal(err)
	}

	tok, _ := loginRegister(t, auth, "NoIns_"+t.Name())
	ctx, cancel := authCtx(tok)
	defer cancel()
	s, _ := session.Session(ctx)
	_ = s.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{Username: "X"}}})
	recvUntil(t, s, 2*time.Second, func(m *pb.ServerMessage) bool { return m.GetQueueState() != nil })
	_ = s.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Leave{Leave: &pb.Leave{}}})
	_, _ = s.Recv() // LeftAck or EOF

	var after int
	if err := db.QueryRow(`SELECT COUNT(*) FROM games`).Scan(&after); err != nil {
		t.Fatal(err)
	}
	if after != before {
		t.Fatalf("Leave из очереди не должен INSERT games: before=%d after=%d", before, after)
	}
}