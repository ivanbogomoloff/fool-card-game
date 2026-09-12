package httpapi

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"foolcardgame/server/internal/hub"
)

type stubActive struct {
	games []hub.ActiveGameInfo
}

func (s stubActive) ActiveGames() []hub.ActiveGameInfo { return s.games }

func TestGamesActive_Unauthorized(t *testing.T) {
	h := GamesActiveHandler(stubActive{}, "secret-key")
	req := httptest.NewRequest(http.MethodGet, "/games/active", nil)
	rr := httptest.NewRecorder()
	h.ServeHTTP(rr, req)
	if rr.Code != http.StatusUnauthorized {
		t.Fatalf("status=%d want 401", rr.Code)
	}

	req2 := httptest.NewRequest(http.MethodGet, "/games/active", nil)
	req2.Header.Set("X-Secret-Key", "wrong")
	rr2 := httptest.NewRecorder()
	h.ServeHTTP(rr2, req2)
	if rr2.Code != http.StatusUnauthorized {
		t.Fatalf("wrong key status=%d", rr2.Code)
	}
}

func TestGamesActive_EmptySecretFailClosed(t *testing.T) {
	h := GamesActiveHandler(stubActive{games: []hub.ActiveGameInfo{{GameID: "g1", Players: 2, DeckCount: 24}}}, "")
	req := httptest.NewRequest(http.MethodGet, "/games/active", nil)
	req.Header.Set("X-Secret-Key", "anything")
	rr := httptest.NewRecorder()
	h.ServeHTTP(rr, req)
	if rr.Code != http.StatusUnauthorized {
		t.Fatalf("empty config secret must 401, got %d", rr.Code)
	}
}

func TestGamesActive_OK(t *testing.T) {
	src := stubActive{games: []hub.ActiveGameInfo{
		{GameID: "aaa", Players: 3, DeckCount: 18},
	}}
	h := GamesActiveHandler(src, "secret-key")
	req := httptest.NewRequest(http.MethodGet, "/games/active", nil)
	req.Header.Set("X-Secret-Key", "secret-key")
	rr := httptest.NewRecorder()
	h.ServeHTTP(rr, req)
	if rr.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", rr.Code, rr.Body.String())
	}
	var body struct {
		Count int `json:"count"`
		Games []struct {
			GameID    string `json:"game_id"`
			Players   int    `json:"players"`
			DeckCount int    `json:"deck_count"`
		} `json:"games"`
	}
	if err := json.Unmarshal(rr.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body.Count != 1 || len(body.Games) != 1 {
		t.Fatalf("body=%+v", body)
	}
	if body.Games[0].GameID != "aaa" || body.Games[0].Players != 3 || body.Games[0].DeckCount != 18 {
		t.Fatalf("game=%+v", body.Games[0])
	}
	if ct := rr.Header().Get("Content-Type"); ct == "" {
		t.Fatal("Content-Type missing")
	}
}
