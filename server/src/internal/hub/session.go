package hub

import (
	"sync"

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
	state   *pb.GameState
	room    *RoomMeta
	log     *MatchLog
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
		// Старый канал больше не используется стримом.
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
		// Переполненный буфер — не блокируем hub.
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

func (s *Session) broadcastPersonalGameLocked() {
	if s.state == nil {
		return
	}
	for id := range s.players {
		s.sendToLocked(id, personalGameMsg(s.state, id))
	}
}

func personalGameMsg(base *pb.GameState, localPlayerID string) *pb.ServerMessage {
	cp := cloneGameState(base)
	cp.LocalPlayerId = localPlayerID
	return &pb.ServerMessage{
		Payload: &pb.ServerMessage_GameState{GameState: cp},
	}
}

func cloneGameState(src *pb.GameState) *pb.GameState {
	if src == nil {
		return nil
	}
	dst := &pb.GameState{
		GameId:           src.GameId,
		Phase:            src.Phase,
		LocalPlayerId:    src.LocalPlayerId,
		DeckCount:        src.DeckCount,
		CanBito:          src.CanBito,
		CanPass:          src.CanPass,
		CanTake:          src.CanTake,
		CanReady:         src.CanReady,
		IsDraw:           src.IsDraw,
		ServerTick:       src.ServerTick,
		Trump:            src.Trump,
		CurrentPlayerId:  src.CurrentPlayerId,
		AttackerId:       src.AttackerId,
		DefenderId:       src.DefenderId,
		WinnerName:       src.WinnerName,
		LoserName:        src.LoserName,
		LoserId:          src.LoserId,
		TurnDeadlineAtMs: src.TurnDeadlineAtMs,
		RoundEvent:       src.RoundEvent,
		ActionEvent:      src.ActionEvent,
	}
	if src.Players != nil {
		dst.Players = make([]*pb.PlayerState, len(src.Players))
		for i, p := range src.Players {
			if p == nil {
				continue
			}
			cp := *p
			dst.Players[i] = &cp
		}
	}
	if src.TablePairs != nil {
		dst.TablePairs = make([]*pb.TablePair, len(src.TablePairs))
		copy(dst.TablePairs, src.TablePairs)
	}
	if src.LocalHand != nil {
		dst.LocalHand = make([]*pb.Card, len(src.LocalHand))
		copy(dst.LocalHand, src.LocalHand)
	}
	if src.RevealLoserCards != nil {
		dst.RevealLoserCards = make([]*pb.Card, len(src.RevealLoserCards))
		copy(dst.RevealLoserCards, src.RevealLoserCards)
	}
	return dst
}

// stubGameState — заглушка до engine (этап 5): игроки PLAYING, без колоды.
func stubGameState(gameID string, room *RoomMeta) *pb.GameState {
	players := make([]*pb.PlayerState, 0, len(room.Players))
	for _, p := range room.Players {
		if p.Status == pb.PlayerStatus_LEFT {
			continue
		}
		st := pb.PlayerStatus_PLAYING
		if p.Status == pb.PlayerStatus_DISCONNECTED {
			st = pb.PlayerStatus_DISCONNECTED
		}
		players = append(players, &pb.PlayerState{
			Id:          p.PlayerID,
			Username:    p.Username,
			AvatarId:    p.AvatarID,
			HandCount:   0,
			IsReady:     false,
			IsConnected: p.Status != pb.PlayerStatus_DISCONNECTED,
			Status:      st,
		})
	}
	var current *string
	if len(players) > 0 {
		id := players[0].Id
		current = &id
	}
	return &pb.GameState{
		GameId:          gameID,
		Phase:           pb.GamePhase_IN_PROGRESS,
		Players:         players,
		DeckCount:       0,
		CurrentPlayerId: current,
	}
}
