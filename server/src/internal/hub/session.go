package hub

import (
	"sync"

	"foolcardgame/server/internal/engine"
	"foolcardgame/server/internal/pb"
)

const playerChannelBuf = 32

// playerConn — привязка стрима к игроку через playerChannel.
type playerConn struct {
	playerID  string
	accountID string
	ch        chan *pb.ServerMessage
}

// Session — in-memory матч/комната с RWMutex и каналами игроков.
type Session struct {
	mu      sync.RWMutex
	state   *pb.GameState // устаревший кэш; предпочтительно match.Project
	room    *RoomMeta
	log     *MatchLog
	match   *engine.Match
	flushed bool
	players map[string]*playerConn
	// gameLogPath — путь к logs/game-{id}.log после появления game id.
	gameLogPath string
}

func newSession(room *RoomMeta) *Session {
	return &Session{
		room:    room,
		players: make(map[string]*playerConn),
	}
}

func newPlayerConn(playerID, accountID string) *playerConn {
	return &playerConn{
		playerID:  playerID,
		accountID: accountID,
		ch:        make(chan *pb.ServerMessage, playerChannelBuf),
	}
}

// attachConn регистрирует или заменяет conn (reconnect); вызывающий держит s.mu.
func (s *Session) attachConnLocked(conn *playerConn) {
	if old, ok := s.players[conn.playerID]; ok && old != nil && old.ch != nil && old != conn {
		closeQuiet(old.ch)
	}
	s.players[conn.playerID] = conn
}

// detachConn снимает канал игрока; вызывающий держит s.mu.
func (s *Session) detachConnLocked(playerID string) {
	if c, ok := s.players[playerID]; ok && c != nil {
		closeQuiet(c.ch)
		delete(s.players, playerID)
	}
}

func closeQuiet(ch chan *pb.ServerMessage) {
	defer func() { _ = recover() }()
	close(ch)
}

func (s *Session) sendToLocked(playerID string, msg *pb.ServerMessage) {
	c, ok := s.players[playerID]
	if !ok || c == nil {
		return
	}
	select {
	case c.ch <- msg:
	default:
	}
}

func (s *Session) broadcastAllLocked(msg *pb.ServerMessage) {
	for id := range s.players {
		s.sendToLocked(id, msg)
	}
}

func (s *Session) broadcastRoomLocked() {
	if s.room == nil {
		return
	}
	msg := &pb.ServerMessage{
		Payload: &pb.ServerMessage_RoomState{RoomState: s.room.toProto()},
	}
	s.broadcastAllLocked(msg)
}
