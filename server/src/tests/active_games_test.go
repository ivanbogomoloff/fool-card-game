package tests

import (
	"testing"

	"foolcardgame/server/internal/pb"
)

func TestHub_ActiveGames_OnlyInProgress(t *testing.T) {
	h := testHub(t)

	// Waiting-лобби без матча — не попадает в ActiveGames.
	waiting, err := h.CreatePrivate("wait1", "WaitHost", 0)
	if err != nil {
		t.Fatal(err)
	}
	if got := h.ActiveGames(); len(got) != 0 {
		t.Fatalf("waiting lobby in ActiveGames: %+v", got)
	}

	created, err := h.CreatePrivate("act1", "A", 0)
	if err != nil {
		t.Fatal(err)
	}
	joined, err := h.JoinPrivate("act2", created.AccessCode, "B", 0)
	if err != nil {
		t.Fatal(err)
	}
	ch1 := make(chan *pb.ServerMessage, 16)
	ch2 := make(chan *pb.ServerMessage, 16)
	if err := h.Subscribe("act1", created.GameId, created.PlayerId, ch1); err != nil {
		t.Fatal(err)
	}
	if err := h.Subscribe("act2", joined.GameId, joined.PlayerId, ch2); err != nil {
		t.Fatal(err)
	}
	if err := h.StartGame("act1", created.GameId, created.PlayerId); err != nil {
		t.Fatal(err)
	}

	active := h.ActiveGames()
	if len(active) != 1 {
		t.Fatalf("active=%+v want 1 (waiting=%s)", active, waiting.GameId)
	}
	if active[0].GameID != created.GameId {
		t.Fatalf("game_id=%s want %s", active[0].GameID, created.GameId)
	}
	if active[0].Players != 2 {
		t.Fatalf("players=%d want 2", active[0].Players)
	}
	if active[0].DeckCount != 24 {
		t.Fatalf("deck_count=%d want 24", active[0].DeckCount)
	}
}
