package hub

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"foolcardgame/server/internal/engine"
	"foolcardgame/server/internal/logging"
	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"
)

// applyFinishedLocked готовит flush, если партия завершена. sess.mu должна быть Lock.
func (h *Hub) applyFinishedLocked(sess *Session) {
	if sess == nil || sess.match == nil || sess.flushed {
		return
	}
	st := sess.match.State()
	if st.Phase != engine.PhaseFinished {
		return
	}
	sess.flushed = true

	foolAccount := ""
	foolPlayer := ""
	if st.LoserID != nil {
		foolPlayer = *st.LoserID
		if p := st.Player(*st.LoserID); p != nil {
			foolAccount = p.AccountID
		}
	}
	if h.cfg.Logger != nil {
		if foolAccount != "" {
			h.cfg.Logger.Game(sess.room.GameID,
				"DOMAIN конец партии: дурак account_id=%s player_id=%s", foolAccount, foolPlayer)
		} else {
			h.cfg.Logger.Game(sess.room.GameID, "DOMAIN конец партии: ничья")
		}
	}

	payload := h.buildFinishedLocked(sess, st)
	gameID := sess.room.GameID
	go func() {
		if err := h.flushFinished(payload); err != nil && h.cfg.Logger != nil {
			h.cfg.Logger.Error("flush game=%s: %v", gameID, err)
		}
		if h.cfg.Logger != nil {
			h.cfg.Logger.CloseGame(gameID)
		}
	}()
}

func (h *Hub) buildFinishedLocked(sess *Session, st engine.State) store.FinishedGame {
	finishedAt := time.Now().UTC()
	ml := sess.log
	started := finishedAt
	access := sql.NullString{}
	playersAtStart := len(st.Players)
	if ml != nil {
		started = ml.StartedAt
		playersAtStart = ml.PlayersAtStart
		if ml.AccessCode != "" {
			access = sql.NullString{String: ml.AccessCode, Valid: true}
		}
	}

	result := "DRAW"
	var foolAcc sql.NullString
	if st.LoserID != nil {
		result = "HAS_FOOL"
		if p := st.Player(*st.LoserID); p != nil && p.AccountID != "" {
			foolAcc = sql.NullString{String: p.AccountID, Valid: true}
		}
	}

	rows := make([]store.GamePlayerRow, 0)
	if ml != nil {
		for _, seat := range ml.Seats {
			pr := "WIN"
			if _, left := ml.VoluntaryLeaves[seat.PlayerID]; left {
				pr = "LEFT"
			} else if result == "DRAW" {
				pr = "DRAW"
			} else if st.LoserID != nil && seat.PlayerID == *st.LoserID {
				pr = "FOOL"
			}
			rows = append(rows, store.GamePlayerRow{AccountID: seat.AccountID, PlayerResult: pr})
		}
	}

	return store.FinishedGame{
		ID:             sess.room.GameID,
		StartedAt:      started,
		FinishedAt:     finishedAt,
		AccessCode:     access,
		PlayersAtStart: playersAtStart,
		Result:         result,
		FoolAccountID:  foolAcc,
		Players:        rows,
	}
}

func (h *Hub) flushFinished(m store.FinishedGame) error {
	if h.cfg.Games == nil {
		return fmt.Errorf("Games store не настроен")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return h.cfg.Games.InsertFinished(ctx, m)
}

func (h *Hub) broadcastEngineLocked(sess *Session) {
	if sess == nil || sess.match == nil {
		return
	}
	for id, conn := range sess.players {
		gs := sess.match.Project(id)
		msg := &pb.ServerMessage{Payload: &pb.ServerMessage_GameState{GameState: gs}}
		sess.sendToLocked(id, msg)
		if h.cfg.Logger != nil && conn != nil {
			h.cfg.Logger.Game(sess.room.GameID,
				"OUT account=%s game=%s msg=GameState phase=%s колода=%d рука=%d стол=%d",
				conn.accountID, sess.room.GameID, gs.Phase.String(), gs.DeckCount, len(gs.LocalHand), len(gs.TablePairs))
		}
	}
}

func deadlineLeftMs(st engine.State) (leftMs int64, totalMs int64, ok bool) {
	if st.TurnDeadlineAtMs == nil || st.TurnStartedAtMs == nil {
		return 0, 0, false
	}
	now := time.Now().UnixMilli()
	left := *st.TurnDeadlineAtMs - now
	total := *st.TurnDeadlineAtMs - *st.TurnStartedAtMs
	if total < 0 {
		total = 0
	}
	return left, total, true
}

func formatTimer(st engine.State) string {
	left, total, ok := deadlineLeftMs(st)
	if !ok {
		return "timer=-"
	}
	if left < 0 {
		left = 0
	}
	return fmt.Sprintf("timer_left_ms=%d timer_total_ms=%d", left, total)
}

func accountOf(st engine.State, playerID string) string {
	if p := st.Player(playerID); p != nil {
		return p.AccountID
	}
	return ""
}

func (h *Hub) logIN(sess *Session, accountID, action, detail string) {
	if h.cfg.Logger == nil || sess == nil || sess.room == nil {
		return
	}
	timer := ""
	if sess.match != nil {
		timer = " " + formatTimer(sess.match.State())
	}
	h.cfg.Logger.Game(sess.room.GameID, "IN Session/%s account=%s game=%s %s%s",
		action, accountID, sess.room.GameID, detail, timer)
}

func (h *Hub) logDomain(sess *Session, format string, args ...any) {
	if h.cfg.Logger == nil || sess == nil || sess.room == nil {
		return
	}
	h.cfg.Logger.Game(sess.room.GameID, "DOMAIN "+format, args...)
}

// logDomainEventsLocked пишет русские доменные события после хода (по Last*Event).
func (h *Hub) logDomainEventsLocked(sess *Session) {
	if sess == nil || sess.match == nil {
		return
	}
	st := sess.match.State()
	timer := formatTimer(st)

	if ev := st.LastRoundEvent; ev != nil {
		acc := accountOf(st, ev.PlayerID)
		switch ev.Kind {
		case engine.RoundEventTook:
			h.logDomain(sess, "взял карты account=%s player_id=%s tick=%d %s",
				acc, ev.PlayerID, ev.AtTick, timer)
		case engine.RoundEventBito:
			h.logDomain(sess, "бито — стол в отбой account=%s player_id=%s tick=%d %s",
				acc, ev.PlayerID, ev.AtTick, timer)
		}
	}
	if ev := st.LastActionEvent; ev != nil {
		acc := accountOf(st, ev.PlayerID)
		switch ev.Kind {
		case engine.ActionAttack:
			h.logDomain(sess, "походил (атака) account=%s player_id=%s tick=%d %s",
				acc, ev.PlayerID, ev.AtTick, timer)
		case engine.ActionDefend:
			h.logDomain(sess, "отбил account=%s player_id=%s tick=%d %s",
				acc, ev.PlayerID, ev.AtTick, timer)
		case engine.ActionThrowIn:
			h.logDomain(sess, "подкинул account=%s player_id=%s tick=%d %s",
				acc, ev.PlayerID, ev.AtTick, timer)
		case engine.ActionPass:
			h.logDomain(sess, "подтвердил бито account=%s player_id=%s tick=%d %s",
				acc, ev.PlayerID, ev.AtTick, timer)
		case engine.ActionBito:
			// RoundEventBito может дублировать закрытие; объявление атакующего логируем отдельно.
			if st.LastRoundEvent == nil || st.LastRoundEvent.Kind != engine.RoundEventBito {
				h.logDomain(sess, "объявил бито account=%s player_id=%s tick=%d %s",
					acc, ev.PlayerID, ev.AtTick, timer)
			}
		case engine.ActionTook:
			// уже покрыто RoundEventTook
		}
	}
}

// PlayCard применяет атаку/отбивку.
func (h *Hub) PlayCard(accountID, gameID, playerID string, card *pb.Card, targetPairID *int32) error {
	return h.withMatch(accountID, gameID, playerID, "PlayCard", func(sess *Session) error {
		c, err := engine.CardFromProto(card)
		if err != nil {
			return err
		}
		hand := ""
		if st := sess.match.State(); st.Player(playerID) != nil {
			hand = engine.FormatHandUTF(st.Player(playerID).Hand)
		}
		h.logIN(sess, accountID, "PlayCard", fmt.Sprintf("action=playCard card=%s hand=%s", engine.FormatCardUTF(c), hand))
		return sess.match.PlayCard(playerID, c, targetPairID)
	})
}

// AddCard — подкид.
func (h *Hub) AddCard(accountID, gameID, playerID string, card *pb.Card) error {
	return h.withMatch(accountID, gameID, playerID, "AddCard", func(sess *Session) error {
		c, err := engine.CardFromProto(card)
		if err != nil {
			return err
		}
		h.logIN(sess, accountID, "AddCard", fmt.Sprintf("action=addCard card=%s", engine.FormatCardUTF(c)))
		return sess.match.AddCard(playerID, c)
	})
}

// Pass — «беру» или подтверждение бито помощником.
func (h *Hub) Pass(accountID, gameID, playerID string) error {
	return h.withMatch(accountID, gameID, playerID, "Pass", func(sess *Session) error {
		kind := "pass"
		if sess.match != nil {
			perms := sess.match.State().PermissionsFor(playerID)
			if perms.CanTake {
				kind = "take"
			} else if perms.CanPass {
				kind = "confirm_bito"
			}
		}
		h.logIN(sess, accountID, "Pass", fmt.Sprintf("action=%s", kind))
		return sess.match.Pass(playerID)
	})
}

// Bito — атакующий объявляет бито.
func (h *Hub) Bito(accountID, gameID, playerID string) error {
	return h.withMatch(accountID, gameID, playerID, "Bito", func(sess *Session) error {
		h.logIN(sess, accountID, "Bito", "action=bito")
		return sess.match.Bito(playerID)
	})
}

// Ready — готовность в лобби; при всех ready → IN_PROGRESS.
func (h *Hub) Ready(accountID, gameID, playerID string) error {
	return h.withMatch(accountID, gameID, playerID, "Ready", func(sess *Session) error {
		h.logIN(sess, accountID, "Ready", "action=ready")
		if err := sess.match.Ready(playerID); err != nil {
			return err
		}
		// Синхронизируем room-статус с engine после Ready.
		if p := sess.room.findPlayer(playerID); p != nil && p.Status != pb.PlayerStatus_LEFT {
			p.Status = pb.PlayerStatus_PLAYING
		}
		return nil
	})
}

func (h *Hub) withMatch(accountID, gameID, playerID, _ string, fn func(*Session) error) error {
	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		return fmt.Errorf("сессия не найдена")
	}
	sess.mu.Lock()
	defer sess.mu.Unlock()
	if sess.match == nil {
		return fmt.Errorf("матч не начат")
	}
	p := sess.room.findPlayer(playerID)
	if p == nil || p.AccountID != accountID {
		return fmt.Errorf("игрок не принадлежит сессии")
	}
	if err := fn(sess); err != nil {
		return err
	}
	h.logDomainEventsLocked(sess)
	h.broadcastEngineLocked(sess)
	h.applyFinishedLocked(sess)
	return nil
}

func (h *Hub) tickSession(gameID string) bool {
	h.mu.Lock()
	sess := h.sessions[gameID]
	h.mu.Unlock()
	if sess == nil {
		return false
	}
	sess.mu.Lock()
	defer sess.mu.Unlock()
	if sess.match == nil || sess.flushed {
		return false
	}
	st := sess.match.State()
	if st.Phase == engine.PhaseFinished {
		h.applyFinishedLocked(sess)
		return false
	}
	changed := sess.match.OnTick()
	if changed {
		st2 := sess.match.State()
		curr := st2.CurrentPlayerID
		acc := accountOf(st, curr) // до скипа — кто просрочил
		if st.CurrentPlayerID != "" {
			acc = accountOf(st, st.CurrentPlayerID)
			curr = st.CurrentPlayerID
		}
		h.logDomain(sess, "таймаут хода: автопроход account=%s player_id=%s tick=%d timer_left_ms=0",
			acc, curr, st2.Tick)
		h.logDomainEventsLocked(sess)
		h.broadcastEngineLocked(sess)
		h.applyFinishedLocked(sess)
	}
	st = sess.match.State()
	return st.Phase != engine.PhaseFinished
}

func (h *Hub) runTicker(gameID string) {
	t := time.NewTicker(time.Second)
	defer t.Stop()
	for range t.C {
		if !h.tickSession(gameID) {
			return
		}
	}
}

var _ = logging.Logger{}
