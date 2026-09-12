package hub

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"foolcardgame/server/internal/ids"
	"foolcardgame/server/internal/pb"

	"github.com/google/uuid"
)

// Config — параметры QuickMatch и логов.
type Config struct {
	QuickMinPlayers   int
	QuickMaxPlayers   int
	QuickFillWindow   time.Duration
	QuickQueueTimeout time.Duration
	LogDir            string
}

func (c Config) withDefaults() Config {
	if c.QuickMinPlayers < 2 {
		c.QuickMinPlayers = 2
	}
	if c.QuickMaxPlayers < c.QuickMinPlayers {
		c.QuickMaxPlayers = 4
	}
	if c.QuickFillWindow <= 0 {
		c.QuickFillWindow = 5 * time.Second
	}
	if c.QuickQueueTimeout <= 0 {
		c.QuickQueueTimeout = 120 * time.Second
	}
	if c.LogDir == "" {
		c.LogDir = "./logs"
	}
	return c
}

type queueEntry struct {
	playerID   string
	accountID  string
	username   string
	avatarID   int32
	ch         chan *pb.ServerMessage
	enqueuedAt time.Time
}

// Hub — глобальный реестр сессий и очередь QuickMatch.
type Hub struct {
	cfg Config

	mu        sync.Mutex
	sessions  map[string]*Session // gameID
	byCode    map[string]*Session // access code → session
	queue     []*queueEntry
	queuePhase pb.QueuePhase
	fillTimer  *time.Timer
	queueTimer *time.Timer

	// accountBusy: accountID → playerID (очередь или waiting/in-progress)
	accountBusy map[string]string
}

// New создаёт Hub.
func New(cfg Config) *Hub {
	cfg = cfg.withDefaults()
	return &Hub{
		cfg:         cfg,
		sessions:    make(map[string]*Session),
		byCode:      make(map[string]*Session),
		queue:       nil,
		queuePhase:  pb.QueuePhase_SEARCHING,
		accountBusy: make(map[string]string),
	}
}

// PlayerChannel возвращает канал игрока в сессии (для тестов hub_*).
func (h *Hub) PlayerChannel(gameID, playerID string) (<-chan *pb.ServerMessage, bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	s, ok := h.sessions[gameID]
	if !ok {
		return nil, false
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	c, ok := s.players[playerID]
	if !ok || c == nil {
		return nil, false
	}
	return c.ch, true
}

// SessionSnapshot для тестов.
func (h *Hub) SessionRoom(gameID string) *RoomMeta {
	h.mu.Lock()
	defer h.mu.Unlock()
	s := h.sessions[gameID]
	if s == nil {
		return nil
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	if s.room == nil {
		return nil
	}
	cp := *s.room
	cp.Players = append([]RoomPlayer(nil), s.room.Players...)
	return &cp
}

func (h *Hub) queueCountLocked() int { return len(h.queue) }

func (h *Hub) sendQueueStateLocked() {
	n := int32(len(h.queue))
	for _, e := range h.queue {
		msg := &pb.ServerMessage{
			Payload: &pb.ServerMessage_QueueState{QueueState: &pb.QueueState{
				PlayerId:     e.playerID,
				WaitingCount: n,
				Phase:        h.queuePhase,
			}},
		}
		select {
		case e.ch <- msg:
		default:
		}
	}
}

func (h *Hub) sendQueueErrorLocked(e *queueEntry, code, message string) {
	msg := &pb.ServerMessage{
		Payload: &pb.ServerMessage_Error{Error: &pb.Error{Code: code, Message: message}},
	}
	select {
	case e.ch <- msg:
	default:
	}
}

// EnqueueQuickMatch ставит игрока в очередь; ch — playerChannel стрима.
func (h *Hub) EnqueueQuickMatch(accountID, username string, avatarID int32, ch chan *pb.ServerMessage) (playerID string, err error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if accountID == "" {
		return "", fmt.Errorf("пустой account_id")
	}
	if _, busy := h.accountBusy[accountID]; busy {
		return "", fmt.Errorf("аккаунт уже в очереди или матче")
	}
	if len(h.queue) >= h.cfg.QuickMaxPlayers && h.queuePhase == pb.QueuePhase_FILLING {
		return "", fmt.Errorf("очередь заполнена")
	}

	playerID = uuid.NewString()
	e := &queueEntry{
		playerID:   playerID,
		accountID:  accountID,
		username:   username,
		avatarID:   avatarID,
		ch:         ch,
		enqueuedAt: time.Now(),
	}
	h.queue = append(h.queue, e)
	h.accountBusy[accountID] = playerID
	h.recalcQueuePhaseLocked()
	h.sendQueueStateLocked()
	return playerID, nil
}

func (h *Hub) recalcQueuePhaseLocked() {
	n := len(h.queue)
	if n == 0 {
		h.queuePhase = pb.QueuePhase_SEARCHING
		h.stopFillTimerLocked()
		h.stopQueueTimerLocked()
		return
	}
	if n < h.cfg.QuickMinPlayers {
		wasFilling := h.queuePhase == pb.QueuePhase_FILLING
		h.queuePhase = pb.QueuePhase_SEARCHING
		h.stopFillTimerLocked()
		if wasFilling || h.queueTimer == nil {
			h.resetQueueTimerLocked()
		}
		return
	}
	// ≥ min
	if n >= h.cfg.QuickMaxPlayers {
		h.queuePhase = pb.QueuePhase_FILLING
		h.stopQueueTimerLocked()
		h.stopFillTimerLocked()
		// Старт сразу при max (вне lock через go).
		go h.startQuickMatch()
		return
	}
	if h.queuePhase != pb.QueuePhase_FILLING {
		h.queuePhase = pb.QueuePhase_FILLING
		h.stopQueueTimerLocked()
		h.startFillTimerLocked()
	}
}

func (h *Hub) startFillTimerLocked() {
	h.stopFillTimerLocked()
	h.fillTimer = time.AfterFunc(h.cfg.QuickFillWindow, func() {
		h.startQuickMatch()
	})
}

func (h *Hub) stopFillTimerLocked() {
	if h.fillTimer != nil {
		h.fillTimer.Stop()
		h.fillTimer = nil
	}
}

func (h *Hub) resetQueueTimerLocked() {
	h.stopQueueTimerLocked()
	h.queueTimer = time.AfterFunc(h.cfg.QuickQueueTimeout, func() {
		h.onQueueTimeout()
	})
}

func (h *Hub) stopQueueTimerLocked() {
	if h.queueTimer != nil {
		h.queueTimer.Stop()
		h.queueTimer = nil
	}
}

func (h *Hub) onQueueTimeout() {
	h.mu.Lock()
	defer h.mu.Unlock()
	if len(h.queue) >= h.cfg.QuickMinPlayers {
		return
	}
	// Всех в SEARCHING с timeout.
	left := h.queue
	h.queue = nil
	h.queuePhase = pb.QueuePhase_SEARCHING
	h.stopFillTimerLocked()
	h.stopQueueTimerLocked()
	for _, e := range left {
		delete(h.accountBusy, e.accountID)
		h.sendQueueErrorLocked(e, "QUEUE_TIMEOUT", "не набралось достаточно игроков")
		closeQuiet(e.ch)
	}
}

func (h *Hub) startQuickMatch() {
	h.mu.Lock()
	defer h.mu.Unlock()

	if len(h.queue) < h.cfg.QuickMinPlayers {
		return
	}
	h.stopFillTimerLocked()
	h.stopQueueTimerLocked()

	n := len(h.queue)
	if n > h.cfg.QuickMaxPlayers {
		n = h.cfg.QuickMaxPlayers
	}
	batch := append([]*queueEntry(nil), h.queue[:n]...)
	h.queue = h.queue[n:]
	if len(h.queue) == 0 {
		h.queuePhase = pb.QueuePhase_SEARCHING
	} else {
		h.recalcQueuePhaseLocked()
		h.sendQueueStateLocked()
	}

	gameID := ids.NewGameID()
	players := make([]RoomPlayer, 0, len(batch))
	for _, e := range batch {
		players = append(players, RoomPlayer{
			PlayerID:  e.playerID,
			AccountID: e.accountID,
			Username:  e.username,
			AvatarID:  e.avatarID,
			IsHost:    false,
			Status:    pb.PlayerStatus_WAITING,
		})
	}
	room := &RoomMeta{
		GameID:     gameID,
		AccessCode: "",
		HostID:     "",
		Started:    false,
		Players:    players,
	}
	sess := newSession(room)
	for _, e := range batch {
		sess.players[e.playerID] = &playerConn{
			playerID:  e.playerID,
			accountID: e.accountID,
			ch:        e.ch,
		}
	}
	path, _ := PrepareGameLog(h.cfg.LogDir, gameID)
	sess.gameLogPath = path
	h.sessions[gameID] = sess

	// Краткий RoomState, затем старт.
	sess.mu.Lock()
	sess.broadcastRoomLocked()
	h.beginMatchLocked(sess)
	sess.mu.Unlock()
}

// beginMatchLocked стартует engine-заглушку; sess.mu уже Lock, h.mu Lock.
func (h *Hub) beginMatchLocked(sess *Session) {
	if sess.room == nil || sess.room.Started {
		return
	}
	sess.room.Started = true
	for i := range sess.room.Players {
		if sess.room.Players[i].Status != pb.PlayerStatus_LEFT {
			sess.room.Players[i].Status = pb.PlayerStatus_PLAYING
		}
	}
	seats := make([]MatchSeat, 0, len(sess.room.Players))
	for _, p := range sess.room.Players {
		if p.Status == pb.PlayerStatus_LEFT {
			continue
		}
		seats = append(seats, MatchSeat{
			PlayerID:  p.PlayerID,
			AccountID: p.AccountID,
			Username:  p.Username,
			AvatarID:  p.AvatarID,
		})
	}
	sess.log = newMatchLog(sess.room.GameID, sess.room.AccessCode, seats)
	if sess.gameLogPath == "" {
		path, _ := PrepareGameLog(h.cfg.LogDir, sess.room.GameID)
		sess.gameLogPath = path
	}
	sess.state = stubGameState(sess.room.GameID, sess.room)

	started := &pb.ServerMessage{
		Payload: &pb.ServerMessage_MatchStarted{MatchStarted: &pb.MatchStarted{GameId: sess.room.GameID}},
	}
	sess.broadcastAllLocked(started)
	sess.broadcastPersonalGameLocked()
}

// LeaveQueue снимает из очереди QuickMatch.
func (h *Hub) LeaveQueue(accountID, playerID string) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.leaveQueueLocked(accountID, playerID, true)
}

func (h *Hub) leaveQueueLocked(accountID, playerID string, sendAck bool) bool {
	for i, e := range h.queue {
		if e.playerID == playerID && e.accountID == accountID {
			h.queue = append(h.queue[:i], h.queue[i+1:]...)
			delete(h.accountBusy, accountID)
			if sendAck {
				ack := &pb.ServerMessage{Payload: &pb.ServerMessage_LeftAck{LeftAck: &pb.LeftAck{}}}
				select {
				case e.ch <- ack:
				default:
				}
			}
			closeQuiet(e.ch)
			h.recalcQueuePhaseLocked()
			h.sendQueueStateLocked()
			return true
		}
	}
	return false
}

// DisconnectQueue — обрыв стрима в очереди (без LeftAck).
func (h *Hub) DisconnectQueue(accountID, playerID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.leaveQueueLocked(accountID, playerID, false)
}

// --- Private ---

// CreatePrivate создаёт waiting-комнату (game id сразу).
func (h *Hub) CreatePrivate(accountID, username string, avatarID int32) (*pb.CreateGameResponse, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if accountID == "" {
		return nil, fmt.Errorf("пустой account_id")
	}
	if _, busy := h.accountBusy[accountID]; busy {
		return nil, fmt.Errorf("аккаунт уже в очереди или матче")
	}

	code, err := ids.NewAccessCode()
	if err != nil {
		return nil, err
	}
	// Уникальность кода в памяти.
	for h.byCode[code] != nil {
		code, err = ids.NewAccessCode()
		if err != nil {
			return nil, err
		}
	}

	gameID := ids.NewGameID()
	playerID := uuid.NewString()
	room := &RoomMeta{
		GameID:     gameID,
		AccessCode: code,
		HostID:     playerID,
		Started:    false,
		Players: []RoomPlayer{{
			PlayerID:  playerID,
			AccountID: accountID,
			Username:  username,
			AvatarID:  avatarID,
			IsHost:    true,
			Status:    pb.PlayerStatus_WAITING,
		}},
	}
	sess := newSession(room)
	h.sessions[gameID] = sess
	h.byCode[code] = sess
	h.accountBusy[accountID] = playerID

	return &pb.CreateGameResponse{
		GameId:     gameID,
		AccessCode: code,
		HostId:     playerID,
		PlayerId:   playerID,
	}, nil
}

// JoinPrivate по коду.
func (h *Hub) JoinPrivate(accountID, code, username string, avatarID int32) (*pb.JoinGameResponse, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if accountID == "" {
		return nil, fmt.Errorf("пустой account_id")
	}
	code = NormalizeAccessCode(code)
	if code == "" {
		return nil, fmt.Errorf("пустой код")
	}
	if _, busy := h.accountBusy[accountID]; busy {
		return nil, fmt.Errorf("аккаунт уже в очереди или матче")
	}

	sess := h.byCode[code]
	if sess == nil {
		return nil, fmt.Errorf("комната не найдена")
	}
	sess.mu.Lock()
	defer sess.mu.Unlock()

	if sess.room == nil || sess.room.Started {
		return nil, fmt.Errorf("игра уже началась или недоступна")
	}
	active := 0
	for _, p := range sess.room.Players {
		if p.Status != pb.PlayerStatus_LEFT {
			active++
		}
	}
	if active >= h.cfg.QuickMaxPlayers {
		return nil, fmt.Errorf("комната заполнена")
	}

	playerID := uuid.NewString()
	sess.room.Players = append(sess.room.Players, RoomPlayer{
		PlayerID:  playerID,
		AccountID: accountID,
		Username:  username,
		AvatarID:  avatarID,
		IsHost:    false,
		Status:    pb.PlayerStatus_WAITING,
	})
	h.accountBusy[accountID] = playerID
	sess.broadcastRoomLocked()

	return &pb.JoinGameResponse{
		GameId:   sess.room.GameID,
		PlayerId: playerID,
	}, nil
}

// Subscribe привязывает stream-канал к игроку в сессии; сразу RoomState или GameState.
func (h *Hub) Subscribe(accountID, gameID, playerID string, ch chan *pb.ServerMessage) error {
	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		return fmt.Errorf("сессия не найдена")
	}

	sess.mu.Lock()
	defer sess.mu.Unlock()

	p := sess.room.findPlayer(playerID)
	if p == nil || p.AccountID != accountID {
		return fmt.Errorf("игрок не принадлежит сессии")
	}
	if p.Status == pb.PlayerStatus_LEFT {
		return fmt.Errorf("игрок уже вышел")
	}

	conn := &playerConn{playerID: playerID, accountID: accountID, ch: ch}
	sess.attachConnLocked(conn)
	if p.Status == pb.PlayerStatus_DISCONNECTED {
		p.Status = pb.PlayerStatus_WAITING
		if sess.room.Started {
			p.Status = pb.PlayerStatus_PLAYING
		}
	}

	if sess.room.Started && sess.state != nil {
		// Resnapshot.
		sess.sendToLocked(playerID, &pb.ServerMessage{
			Payload: &pb.ServerMessage_MatchStarted{MatchStarted: &pb.MatchStarted{GameId: gameID}},
		})
		sess.sendToLocked(playerID, personalGameMsg(sess.state, playerID))
	} else {
		sess.sendToLocked(playerID, &pb.ServerMessage{
			Payload: &pb.ServerMessage_RoomState{RoomState: sess.room.toProto()},
		})
	}
	return nil
}

// Kick только host.
func (h *Hub) Kick(accountID, gameID, actorPlayerID, targetPlayerID string) error {
	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		return fmt.Errorf("сессия не найдена")
	}

	sess.mu.Lock()
	defer sess.mu.Unlock()

	if sess.room == nil || sess.room.Started {
		return fmt.Errorf("кик только в waiting")
	}
	if actorPlayerID != sess.room.HostID {
		return fmt.Errorf("кик только хостом")
	}
	if targetPlayerID == sess.room.HostID {
		return fmt.Errorf("нельзя кикнуть хоста")
	}
	target := sess.room.findPlayer(targetPlayerID)
	if target == nil {
		return fmt.Errorf("игрок не найден")
	}

	sess.sendToLocked(targetPlayerID, &pb.ServerMessage{
		Payload: &pb.ServerMessage_Kicked{Kicked: &pb.Kicked{Reason: "кикнут хостом"}},
	})
	sess.detachConnLocked(targetPlayerID)
	acc := target.AccountID
	sess.room.removePlayer(targetPlayerID)

	h.mu.Lock()
	delete(h.accountBusy, acc)
	h.mu.Unlock()

	sess.broadcastRoomLocked()
	return nil
}

// StartGame только host, ≥ 2 игрока.
func (h *Hub) StartGame(accountID, gameID, actorPlayerID string) error {
	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		return fmt.Errorf("сессия не найдена")
	}

	sess.mu.Lock()
	defer sess.mu.Unlock()

	if sess.room == nil {
		return fmt.Errorf("нет комнаты")
	}
	if sess.room.Started {
		return fmt.Errorf("уже запущено")
	}
	if actorPlayerID != sess.room.HostID {
		return fmt.Errorf("старт только хостом")
	}
	actor := sess.room.findPlayer(actorPlayerID)
	if actor == nil || actor.AccountID != accountID {
		return fmt.Errorf("игрок не принадлежит сессии")
	}
	active := 0
	for _, p := range sess.room.Players {
		if p.Status != pb.PlayerStatus_LEFT {
			active++
		}
	}
	if active < h.cfg.QuickMinPlayers {
		return fmt.Errorf("нужно минимум %d игрока", h.cfg.QuickMinPlayers)
	}

	h.beginMatchLocked(sess)
	return nil
}

// Leave из сессии (явный выход).
func (h *Hub) Leave(accountID, gameID, playerID string) error {
	// Сначала очередь.
	if gameID == "" {
		if h.LeaveQueue(accountID, playerID) {
			return nil
		}
		return fmt.Errorf("не в очереди")
	}

	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		if h.LeaveQueue(accountID, playerID) {
			return nil
		}
		return fmt.Errorf("сессия не найдена")
	}

	sess.mu.Lock()
	p := sess.room.findPlayer(playerID)
	if p == nil || p.AccountID != accountID {
		sess.mu.Unlock()
		return fmt.Errorf("игрок не найден")
	}
	p.Status = pb.PlayerStatus_LEFT
	if sess.log != nil {
		sess.log.VoluntaryLeaves[playerID] = time.Now().UTC()
	}
	sess.sendToLocked(playerID, &pb.ServerMessage{
		Payload: &pb.ServerMessage_LeftAck{LeftAck: &pb.LeftAck{}},
	})
	sess.detachConnLocked(playerID)

	started := sess.room.Started
	if !started {
		sess.room.removePlayer(playerID)
		sess.broadcastRoomLocked()
		empty := len(sess.room.Players) == 0
		code := sess.room.AccessCode
		gid := sess.room.GameID
		sess.mu.Unlock()

		h.mu.Lock()
		delete(h.accountBusy, accountID)
		if empty {
			delete(h.sessions, gid)
			if code != "" {
				delete(h.byCode, code)
			}
		}
		h.mu.Unlock()
		return nil
	}

	// IN_PROGRESS: обновляем state и broadcast.
	if sess.state != nil {
		for _, ps := range sess.state.Players {
			if ps != nil && ps.Id == playerID {
				ps.Status = pb.PlayerStatus_LEFT
				ps.IsConnected = false
			}
		}
		sess.broadcastPersonalGameLocked()
	}
	sess.mu.Unlock()

	h.mu.Lock()
	delete(h.accountBusy, accountID)
	h.mu.Unlock()
	return nil
}

// Disconnect — обрыв стрима: DISCONNECTED, без VoluntaryLeaves.
func (h *Hub) Disconnect(accountID, gameID, playerID string) {
	if gameID == "" || playerID == "" {
		h.DisconnectQueue(accountID, playerID)
		return
	}

	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		h.DisconnectQueue(accountID, playerID)
		return
	}

	sess.mu.Lock()
	defer sess.mu.Unlock()

	p := sess.room.findPlayer(playerID)
	if p == nil || p.AccountID != accountID {
		return
	}
	if p.Status == pb.PlayerStatus_LEFT {
		return
	}
	p.Status = pb.PlayerStatus_DISCONNECTED
	sess.detachConnLocked(playerID)

	if !sess.room.Started {
		sess.broadcastRoomLocked()
		return
	}
	if sess.state != nil {
		for _, ps := range sess.state.Players {
			if ps != nil && ps.Id == playerID {
				ps.Status = pb.PlayerStatus_DISCONNECTED
				ps.IsConnected = false
			}
		}
		sess.broadcastPersonalGameLocked()
	}
}

// LeaveByPlayer — Leave из очереди или сессии по playerID.
func (h *Hub) LeaveByPlayer(accountID, playerID string) error {
	if h.LeaveQueue(accountID, playerID) {
		return nil
	}
	gameID := h.findGameID(accountID, playerID)
	if gameID == "" {
		return fmt.Errorf("игрок не в очереди и не в сессии")
	}
	return h.Leave(accountID, gameID, playerID)
}

// OnStreamEnd — обрыв стрима: очередь или DISCONNECTED в сессии.
func (h *Hub) OnStreamEnd(accountID, gameID, playerID string) {
	if playerID == "" {
		return
	}
	h.mu.Lock()
	for i, e := range h.queue {
		if e.playerID == playerID && e.accountID == accountID {
			h.queue = append(h.queue[:i], h.queue[i+1:]...)
			delete(h.accountBusy, accountID)
			closeQuiet(e.ch)
			h.recalcQueuePhaseLocked()
			h.sendQueueStateLocked()
			h.mu.Unlock()
			return
		}
	}
	if gameID == "" {
		gameID = h.findGameIDLocked(accountID, playerID)
	}
	h.mu.Unlock()
	if gameID != "" {
		h.Disconnect(accountID, gameID, playerID)
	}
}

func (h *Hub) findGameID(accountID, playerID string) string {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.findGameIDLocked(accountID, playerID)
}

func (h *Hub) findGameIDLocked(accountID, playerID string) string {
	for gid, sess := range h.sessions {
		if sess == nil || sess.room == nil {
			continue
		}
		sess.mu.RLock()
		p := sess.room.findPlayer(playerID)
		ok := p != nil && p.AccountID == accountID && p.Status != pb.PlayerStatus_LEFT
		sess.mu.RUnlock()
		if ok {
			return gid
		}
	}
	return ""
}

// FindGameIDPublic — для Session handler (Kick/Start после QuickMatch).
func (h *Hub) FindGameIDPublic(accountID, playerID string) string {
	return h.findGameID(accountID, playerID)
}

// BusyPlayerID — account → playerID если занят.
func (h *Hub) BusyPlayerID(accountID string) (playerID string, ok bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	id, ok := h.accountBusy[accountID]
	return id, ok
}

// GameLogPath путь к файлу лога матча.
func (h *Hub) GameLogPath(gameID string) string {
	h.mu.Lock()
	defer h.mu.Unlock()
	s := h.sessions[gameID]
	if s == nil {
		return ""
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.gameLogPath
}

// MatchLogFor тест: VoluntaryLeaves.
func (h *Hub) MatchLogFor(gameID string) *MatchLog {
	h.mu.Lock()
	defer h.mu.Unlock()
	s := h.sessions[gameID]
	if s == nil {
		return nil
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.log
}

// NormalizeAccessCode — uppercase + алфавит без I/O/0/1.
func NormalizeAccessCode(code string) string {
	code = strings.ToUpper(strings.TrimSpace(code))
	var b strings.Builder
	for _, r := range code {
		if strings.ContainsRune(accessCodeAlphabet, r) {
			b.WriteRune(r)
		}
	}
	return b.String()
}

const accessCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// PrepareGameLog создаёт/открывает logs/game-{id}.log (запись содержимого — этап 5).
func PrepareGameLog(logDir, gameID string) (string, error) {
	if err := os.MkdirAll(logDir, 0o755); err != nil {
		return "", err
	}
	path := filepath.Join(logDir, fmt.Sprintf("game-%s.log", gameID))
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return path, err
	}
	_ = f.Close()
	return path, nil
}

// QueuePhaseForTest возвращает текущую фазу очереди.
func (h *Hub) QueuePhaseForTest() pb.QueuePhase {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.queuePhase
}

// QueueLenForTest длина очереди.
func (h *Hub) QueueLenForTest() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.queue)
}
