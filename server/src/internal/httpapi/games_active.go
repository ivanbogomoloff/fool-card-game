package httpapi

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"

	"foolcardgame/server/internal/hub"
)

const headerSecretKey = "X-Secret-Key"

// ActiveGamesSource — источник списка активных партий (обычно *hub.Hub).
type ActiveGamesSource interface {
	ActiveGames() []hub.ActiveGameInfo
}

// Mount регистрирует /healthz (опционально снаружи) и /games/active на mux.
func MountGamesActive(mux *http.ServeMux, src ActiveGamesSource, secret string) {
	mux.HandleFunc("/games/active", GamesActiveHandler(src, secret))
}

// GamesActiveHandler отдаёт JSON со списком IN_PROGRESS; требует X-Secret-Key.
func GamesActiveHandler(src ActiveGamesSource, secret string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if !secretMatch(r.Header.Get(headerSecretKey), secret) {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		games := src.ActiveGames()
		if games == nil {
			games = []hub.ActiveGameInfo{}
		}
		resp := struct {
			Count int                 `json:"count"`
			Games []hub.ActiveGameInfo `json:"games"`
		}{
			Count: len(games),
			Games: games,
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		enc := json.NewEncoder(w)
		enc.SetEscapeHTML(true)
		if err := enc.Encode(resp); err != nil {
			return
		}
	}
}

func secretMatch(got, want string) bool {
	if want == "" {
		return false
	}
	if len(got) != len(want) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}
