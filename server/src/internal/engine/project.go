package engine

import (
	"foolcardgame/server/internal/pb"
)

// Project — персональная проекция состояния для игрока (как LocalGameClient.toDto).
func (m *Match) Project(localPlayerID string) *pb.GameState {
	s := m.State()
	perms := s.PermissionsFor(localPlayerID)
	local := s.Player(localPlayerID)

	players := make([]*pb.PlayerState, len(s.Players))
	for i, p := range s.Players {
		players[i] = &pb.PlayerState{
			Id:          p.PlayerID,
			Username:    p.Username,
			AvatarId:    p.AvatarID,
			HandCount:   int32(len(p.Hand)),
			IsReady:     p.IsReady,
			IsConnected: p.IsConnected,
			Status:      playerStatusToProto(p.Status),
		}
	}

	var localHand []*pb.Card
	if local != nil {
		localHand = cardsToProto(local.Hand)
	}

	tick := s.Tick
	out := &pb.GameState{
		GameId:        s.GameID,
		Phase:         phaseToProto(s.Phase),
		Players:       players,
		LocalPlayerId: localPlayerID,
		ServerTick:    &tick,
		DeckCount:     int32(len(s.Deck)),
		TablePairs:    tablePairsToProto(s.TablePairs),
		LocalHand:     localHand,
		CanBito:       perms.CanBito,
		CanPass:       perms.CanPass,
		CanTake:       perms.CanTake,
		CanReady:      perms.CanReady,
		IsDraw:        s.Phase == PhaseFinished && s.LoserID == nil,
		RoundEvent:    roundEventToProto(s.LastRoundEvent),
		ActionEvent:   actionEventToProto(s.LastActionEvent),
	}

	if s.TrumpCard != nil {
		out.Trump = CardToProto(*s.TrumpCard)
	}
	if s.CurrentPlayerID != "" {
		id := s.CurrentPlayerID
		out.CurrentPlayerId = &id
	}
	if s.AttackerID != "" {
		id := s.AttackerID
		out.AttackerId = &id
	}
	if s.DefenderID != "" {
		id := s.DefenderID
		out.DefenderId = &id
	}
	if s.TurnDeadlineAtMs != nil {
		out.TurnDeadlineAtMs = s.TurnDeadlineAtMs
	}
	if s.LoserID != nil {
		out.LoserId = s.LoserID
		if lp := s.Player(*s.LoserID); lp != nil {
			name := lp.Username
			out.LoserName = &name
			out.RevealLoserCards = cardsToProto(lp.Hand)
		}
	}
	if s.Phase == PhaseFinished && s.LoserID == nil {
		// ничья
	} else if len(s.WinnerIDs) > 0 {
		if wp := s.Player(s.WinnerIDs[0]); wp != nil {
			name := wp.Username
			out.WinnerName = &name
		}
	}

	return out
}
