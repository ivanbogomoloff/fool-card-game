package tests

import (
	"context"
	"database/sql"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"foolcardgame/server/internal/engine"
	"foolcardgame/server/internal/logging"
	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"
)

func TestStatsFlush_InsertOnlyAfterFinished(t *testing.T) {
	db := openTestDB(t)
	ensureMigrated(t, db)
	dir := t.TempDir()
	appLog, err := logging.New(dir, true)
	if err != nil {
		t.Fatal(err)
	}
	defer appLog.Close()

	gameID := "flush-test-" + time.Now().Format("150405.000")
	gameStore := &store.Games{DB: db}

	// До flush строки нет.
	var n int
	if err := db.QueryRow(`SELECT COUNT(*) FROM games WHERE id=?`, gameID).Scan(&n); err != nil {
		t.Fatal(err)
	}
	if n != 0 {
		t.Fatal("games должна быть пуста")
	}

	err = gameStore.InsertFinished(context.Background(), store.FinishedGame{
		ID:             gameID,
		StartedAt:      time.Now().Add(-time.Minute),
		FinishedAt:     time.Now(),
		AccessCode:     sql.NullString{},
		PlayersAtStart: 2,
		Result:         "HAS_FOOL",
		FoolAccountID:  sql.NullString{},
		Players: []store.GamePlayerRow{
			{AccountID: mustAccount(t, db, "FlushA"), PlayerResult: "WIN"},
			{AccountID: mustAccount(t, db, "FlushB"), PlayerResult: "FOOL"},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := db.QueryRow(`SELECT COUNT(*) FROM games WHERE id=?`, gameID).Scan(&n); err != nil {
		t.Fatal(err)
	}
	if n != 1 {
		t.Fatalf("want 1 game row, got %d", n)
	}
	var gp int
	if err := db.QueryRow(`SELECT COUNT(*) FROM game_players WHERE game_id=?`, gameID).Scan(&gp); err != nil {
		t.Fatal(err)
	}
	if gp != 2 {
		t.Fatalf("want 2 game_players, got %d", gp)
	}
}

func TestStatsFlush_LEFT(t *testing.T) {
	db := openTestDB(t)
	ensureMigrated(t, db)
	acc1 := mustAccount(t, db, "LeftA")
	acc2 := mustAccount(t, db, "LeftB")
	gameID := "left-test-" + time.Now().Format("150405.000")
	err := (&store.Games{DB: db}).InsertFinished(context.Background(), store.FinishedGame{
		ID: gameID, StartedAt: time.Now(), FinishedAt: time.Now(),
		PlayersAtStart: 2, Result: "HAS_FOOL",
		FoolAccountID: sql.NullString{String: acc2, Valid: true},
		Players: []store.GamePlayerRow{
			{AccountID: acc1, PlayerResult: "LEFT"},
			{AccountID: acc2, PlayerResult: "FOOL"},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	var pr string
	if err := db.QueryRow(`SELECT player_result FROM game_players WHERE game_id=? AND account_id=?`,
		gameID, acc1).Scan(&pr); err != nil {
		t.Fatal(err)
	}
	if pr != "LEFT" {
		t.Fatalf("want LEFT, got %s", pr)
	}
}

func TestLogging_GameINOutAndFullFlag(t *testing.T) {
	dir := t.TempDir()
	full, err := logging.New(dir, true)
	if err != nil {
		t.Fatal(err)
	}
	defer full.Close()

	gid := "log-game-1"
	path, err := full.OpenGame(gid)
	if err != nil {
		t.Fatal(err)
	}
	full.Game(gid, "IN Session/PlayCard account=a1 game=%s action=playCard card=♠A", gid)
	full.Game(gid, "OUT account=a2 game=%s msg=GameState phase=IN_PROGRESS", gid)
	full.Matchmaking("enqueue account=a1 count=1")
	full.Error("test error line")

	time.Sleep(10 * time.Millisecond)
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	s := string(data)
	if !strings.Contains(s, "IN Session/PlayCard") || !strings.Contains(s, "OUT account=") {
		t.Fatalf("game log missing IN/OUT: %s", s)
	}
	mm, err := os.ReadFile(filepath.Join(dir, "matchmaking.log"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(mm), "enqueue") {
		t.Fatal("matchmaking.log empty")
	}
	errLog, _ := os.ReadFile(filepath.Join(dir, "errors.log"))
	if !strings.Contains(string(errLog), "test error line") {
		t.Fatal("errors.log missing")
	}

	dir2 := t.TempDir()
	off, err := logging.New(dir2, false)
	if err != nil {
		t.Fatal(err)
	}
	defer off.Close()
	p2, _ := off.OpenGame("g2")
	off.Game("g2", "IN should not write")
	off.Error("always")
	if _, err := os.Stat(p2); !os.IsNotExist(err) {
		// OpenGame при !full не создаёт файл
		if b, e := os.ReadFile(p2); e == nil && len(b) > 0 {
			t.Fatal("FULL_LOGGING=false не должен писать game log")
		}
	}
	eb, _ := os.ReadFile(filepath.Join(dir2, "errors.log"))
	if !strings.Contains(string(eb), "always") {
		t.Fatal("errors должны писаться при FULL_LOGGING=false")
	}
}

func TestHub_MatchStartedDealsHands(t *testing.T) {
	h := testHub(t)
	created, err := h.CreatePrivate("deal1", "A", 0)
	if err != nil {
		t.Fatal(err)
	}
	joined, err := h.JoinPrivate("deal2", created.AccessCode, "B", 0)
	if err != nil {
		t.Fatal(err)
	}
	ch1 := make(chan *pb.ServerMessage, 16)
	ch2 := make(chan *pb.ServerMessage, 16)
	if err := h.Subscribe("deal1", created.GameId, created.PlayerId, ch1); err != nil {
		t.Fatal(err)
	}
	if err := h.Subscribe("deal2", joined.GameId, joined.PlayerId, ch2); err != nil {
		t.Fatal(err)
	}
	if err := h.StartGame("deal1", created.GameId, created.PlayerId); err != nil {
		t.Fatal(err)
	}

	var gs *pb.GameState
	deadline := time.After(2 * time.Second)
	for gs == nil {
		select {
		case m := <-ch1:
			if m.GetGameState() != nil {
				gs = m.GetGameState()
			}
		case <-deadline:
			t.Fatal("нет GameState")
		}
	}
	if gs.Phase != pb.GamePhase_LOBBY_WAITING {
		t.Fatalf("phase=%v want LOBBY_WAITING", gs.Phase)
	}
	if !gs.CanReady {
		t.Fatal("can_ready expected in lobby")
	}
	if len(gs.LocalHand) != 6 {
		t.Fatalf("hand=%d want 6", len(gs.LocalHand))
	}
	if gs.DeckCount != 24 { // 36-12
		t.Fatalf("deck=%d want 24", gs.DeckCount)
	}
	if gs.Trump == nil {
		t.Fatal("trump required")
	}

	if err := h.Ready("deal1", created.GameId, created.PlayerId); err != nil {
		t.Fatal(err)
	}
	if err := h.Ready("deal2", created.GameId, joined.PlayerId); err != nil {
		t.Fatal(err)
	}
	var progress *pb.GameState
	deadline2 := time.After(2 * time.Second)
	for progress == nil {
		select {
		case m := <-ch1:
			if g := m.GetGameState(); g != nil && g.Phase == pb.GamePhase_IN_PROGRESS {
				progress = g
			}
		case <-deadline2:
			t.Fatal("нет IN_PROGRESS после Ready")
		}
	}

	// До FINISHED в БД нет этой игры.
	db := openTestDB(t)
	var n int
	_ = db.QueryRow(`SELECT COUNT(*) FROM games WHERE id=?`, created.GameId).Scan(&n)
	if n != 0 {
		t.Fatalf("games row before FINISHED: %d", n)
	}
}

func TestEngine_UnitPackageExists(t *testing.T) {
	// sanity: типы engine доступны тестам интеграционного пакета.
	_ = engine.TurnTimeoutMs
}

func mustAccount(t *testing.T, db *sql.DB, prefix string) string {
	t.Helper()
	accounts := &store.Accounts{DB: db}
	name := uniqName(prefix)
	res, err := accounts.Login(context.Background(), name, "")
	if err != nil {
		t.Fatal(err)
	}
	return res.Account.ID
}
