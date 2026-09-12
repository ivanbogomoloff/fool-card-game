package service

import (
	"io"
	"sync"

	"foolcardgame/server/internal/auth"
	"foolcardgame/server/internal/hub"
	"foolcardgame/server/internal/pb"
)

// SessionServer — bidi Session: QuickMatch, Subscribe, Kick, StartGame, Leave, ходы, Ping.
type SessionServer struct {
	pb.UnimplementedGameSessionServer
	Hub *hub.Hub
}

func NewSessionServer(h *hub.Hub) *SessionServer {
	return &SessionServer{Hub: h}
}

func (s *SessionServer) Session(stream pb.GameSession_SessionServer) error {
	ctx := stream.Context()
	accountID, ok := auth.AccountIDFromContext(ctx)
	if !ok || accountID == "" {
		return sendErr(stream, "UNAUTHENTICATED", "нет account_id")
	}
	if s.Hub == nil {
		return sendErr(stream, "INTERNAL", "hub не настроен")
	}

	ch := make(chan *pb.ServerMessage, 32)
	var (
		mu       sync.Mutex
		playerID string
		gameID   string
		inQueue  bool
	)

	setBinding := func(pid, gid string, queue bool) {
		mu.Lock()
		playerID, gameID, inQueue = pid, gid, queue
		mu.Unlock()
	}
	getBinding := func() (pid, gid string, queue bool) {
		mu.Lock()
		defer mu.Unlock()
		return playerID, gameID, inQueue
	}
	resolveGame := func(pid, gid string) string {
		if gid != "" {
			return gid
		}
		return s.Hub.FindGameIDPublic(accountID, pid)
	}

	sendDone := make(chan struct{})
	go func() {
		defer close(sendDone)
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-ch:
				if !ok {
					return
				}
				if err := stream.Send(msg); err != nil {
					return
				}
			}
		}
	}()

	defer func() {
		pid, gid, _ := getBinding()
		if pid != "" {
			s.Hub.OnStreamEnd(accountID, gid, pid)
		}
	}()

	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}

		switch payload := msg.Payload.(type) {
		case *pb.ClientMessage_Ping:
			select {
			case ch <- &pb.ServerMessage{Payload: &pb.ServerMessage_Pong{Pong: &pb.Pong{}}}:
			default:
			}

		case *pb.ClientMessage_QuickMatch:
			qm := payload.QuickMatch
			username := ""
			var avatar int32
			if qm != nil {
				username = qm.Username
				avatar = qm.AvatarId
			}
			pid, err := s.Hub.EnqueueQuickMatch(accountID, username, avatar, ch)
			if err != nil {
				_ = sendErr(stream, "BUSY", err.Error())
				continue
			}
			setBinding(pid, "", true)

		case *pb.ClientMessage_Subscribe:
			sub := payload.Subscribe
			if sub == nil {
				_ = sendErr(stream, "INVALID", "пустой Subscribe")
				continue
			}
			if err := s.Hub.Subscribe(accountID, sub.GameId, sub.PlayerId, ch); err != nil {
				_ = sendErr(stream, "SUBSCRIBE", err.Error())
				continue
			}
			setBinding(sub.PlayerId, sub.GameId, false)

		case *pb.ClientMessage_Kick:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			if gid == "" || pid == "" {
				_ = sendErr(stream, "INVALID", "нет активной сессии")
				continue
			}
			target := ""
			if payload.Kick != nil {
				target = payload.Kick.PlayerId
			}
			if err := s.Hub.Kick(accountID, gid, pid, target); err != nil {
				_ = sendErr(stream, "KICK", err.Error())
			}

		case *pb.ClientMessage_StartGame:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			if gid == "" || pid == "" {
				_ = sendErr(stream, "INVALID", "нет активной сессии")
				continue
			}
			if err := s.Hub.StartGame(accountID, gid, pid); err != nil {
				_ = sendErr(stream, "START", err.Error())
			}

		case *pb.ClientMessage_PlayCard:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			pc := payload.PlayCard
			var card *pb.Card
			var target *int32
			if pc != nil {
				card = pc.Card
				target = pc.TargetPairId
			}
			if err := s.Hub.PlayCard(accountID, gid, pid, card, target); err != nil {
				_ = sendErr(stream, "PLAY", err.Error())
			}

		case *pb.ClientMessage_AddCard:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			var card *pb.Card
			if payload.AddCard != nil {
				card = payload.AddCard.Card
			}
			if err := s.Hub.AddCard(accountID, gid, pid, card); err != nil {
				_ = sendErr(stream, "ADD", err.Error())
			}

		case *pb.ClientMessage_Pass:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			if err := s.Hub.Pass(accountID, gid, pid); err != nil {
				_ = sendErr(stream, "PASS", err.Error())
			}

		case *pb.ClientMessage_Bito:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			if err := s.Hub.Bito(accountID, gid, pid); err != nil {
				_ = sendErr(stream, "BITO", err.Error())
			}

		case *pb.ClientMessage_Ready:
			pid, gid, _ := getBinding()
			gid = resolveGame(pid, gid)
			if err := s.Hub.Ready(accountID, gid, pid); err != nil {
				_ = sendErr(stream, "READY", err.Error())
			}

		case *pb.ClientMessage_Leave:
			pid, _, _ := getBinding()
			if pid == "" {
				_ = sendErr(stream, "INVALID", "нечего покидать")
				continue
			}
			_ = s.Hub.LeaveByPlayer(accountID, pid)
			setBinding("", "", false)
			<-sendDone
			return nil

		case nil:
			_ = sendErr(stream, "EMPTY", "пустое ClientMessage")

		default:
			_ = payload
			_ = sendErr(stream, "UNKNOWN", "неизвестное сообщение")
		}
	}
}

func sendErr(stream pb.GameSession_SessionServer, code, message string) error {
	return stream.Send(&pb.ServerMessage{
		Payload: &pb.ServerMessage_Error{Error: &pb.Error{Code: code, Message: message}},
	})
}

var _ pb.GameSessionServer = (*SessionServer)(nil)
