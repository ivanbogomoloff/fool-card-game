package hub

import "time"

// MatchSeat — место игрока на старте матча (для flush на этапе 5).
type MatchSeat struct {
	PlayerID  string
	AccountID string
	Username  string
	AvatarID  int32
}

// MatchLog — in-memory лог матча; INSERT в БД только после FINISHED (этап 5).
type MatchLog struct {
	GameID          string
	AccessCode      string
	StartedAt       time.Time
	PlayersAtStart  int
	Seats           []MatchSeat
	VoluntaryLeaves map[string]time.Time // playerID → время Leave
}

func newMatchLog(gameID, accessCode string, seats []MatchSeat) *MatchLog {
	copied := make([]MatchSeat, len(seats))
	copy(copied, seats)
	return &MatchLog{
		GameID:          gameID,
		AccessCode:      accessCode,
		StartedAt:       time.Now().UTC(),
		PlayersAtStart:  len(seats),
		Seats:           copied,
		VoluntaryLeaves: make(map[string]time.Time),
	}
}
