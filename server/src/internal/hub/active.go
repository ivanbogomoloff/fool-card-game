package hub

import (
	"sort"

	"foolcardgame/server/internal/engine"
)

// ActiveGameInfo — краткая live-статистика партии IN_PROGRESS.
type ActiveGameInfo struct {
	GameID    string `json:"game_id"`
	Players   int    `json:"players"`
	DeckCount int    `json:"deck_count"`
}

// ActiveGames возвращает партии в фазе IN_PROGRESS (для GET /games/active).
func (h *Hub) ActiveGames() []ActiveGameInfo {
	h.mu.Lock()
	defer h.mu.Unlock()

	out := make([]ActiveGameInfo, 0)
	for _, sess := range h.sessions {
		if sess == nil {
			continue
		}
		sess.mu.RLock()
		m := sess.match
		sess.mu.RUnlock()
		if m == nil {
			continue
		}
		st := m.State()
		if st.Phase != engine.PhaseInProgress && st.Phase != engine.PhaseLobbyWaiting {
			continue
		}
		out = append(out, ActiveGameInfo{
			GameID:    st.GameID,
			Players:   len(st.Players),
			DeckCount: len(st.Deck),
		})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].GameID < out[j].GameID })
	return out
}
