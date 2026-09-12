package simclient

import (
	"foolcardgame/server/internal/pb"
)

// chooseLegalAction выбирает легальный ход (как BotAI на Android: сначала отбивка, потом беру).
func chooseLegalAction(st *pb.GameState) *pb.ClientMessage {
	if st == nil || st.Phase != pb.GamePhase_IN_PROGRESS {
		return nil
	}

	trump := st.Trump
	localID := st.LocalPlayerId
	isDefender := st.DefenderId != nil && *st.DefenderId == localID
	isAttacker := st.AttackerId != nil && *st.AttackerId == localID

	var undefended *pb.TablePair
	for _, p := range st.TablePairs {
		if p != nil && p.Defense == nil {
			undefended = p
			break
		}
	}

	// Атакующий: «Бито», когда всё отбито.
	if st.CanBito {
		return &pb.ClientMessage{Payload: &pb.ClientMessage_Bito{Bito: &pb.Bito{}}}
	}

	// Защитник: сначала попытка отбить, иначе «беру».
	if isDefender && undefended != nil {
		beating := lowestBeating(st.LocalHand, undefended.Attack, trump)
		if beating != nil {
			pid := undefended.Id
			return &pb.ClientMessage{Payload: &pb.ClientMessage_PlayCard{PlayCard: &pb.PlayCard{
				Card: beating, TargetPairId: &pid,
			}}}
		}
		if st.CanTake {
			return &pb.ClientMessage{Payload: &pb.ClientMessage_Pass{Pass: &pb.Pass{}}}
		}
		return nil
	}

	// Помощник: подтверждение бито (Pass), если нельзя/нечего подкинуть.
	ranks := tableRanks(st)
	if !isDefender && len(st.TablePairs) > 0 {
		for _, c := range st.LocalHand {
			if c == nil {
				continue
			}
			if _, ok := ranks[c.Rank]; ok {
				return &pb.ClientMessage{Payload: &pb.ClientMessage_AddCard{AddCard: &pb.AddCard{Card: c}}}
			}
		}
	}
	if st.CanPass {
		return &pb.ClientMessage{Payload: &pb.ClientMessage_Pass{Pass: &pb.Pass{}}}
	}

	// Первая атака
	if isAttacker && len(st.TablePairs) == 0 && st.CurrentPlayerId != nil && *st.CurrentPlayerId == localID {
		if c := lowestCard(st.LocalHand, trump); c != nil {
			return &pb.ClientMessage{Payload: &pb.ClientMessage_PlayCard{PlayCard: &pb.PlayCard{Card: c}}}
		}
	}

	return nil
}

func lowestBeating(hand []*pb.Card, attack, trump *pb.Card) *pb.Card {
	var best *pb.Card
	for _, c := range hand {
		if c == nil || !beats(c, attack, trump) {
			continue
		}
		if best == nil || cardLess(c, best, trump) {
			best = c
		}
	}
	return best
}

func tableRanks(st *pb.GameState) map[pb.Rank]struct{} {
	m := make(map[pb.Rank]struct{})
	for _, p := range st.TablePairs {
		if p == nil || p.Attack == nil {
			continue
		}
		m[p.Attack.Rank] = struct{}{}
		if p.Defense != nil {
			m[p.Defense.Rank] = struct{}{}
		}
	}
	return m
}

func beats(defense, attack, trump *pb.Card) bool {
	if defense == nil || attack == nil {
		return false
	}
	if defense.Suit == attack.Suit {
		return defense.Rank > attack.Rank
	}
	if trump != nil && defense.Suit == trump.Suit && attack.Suit != trump.Suit {
		return true
	}
	if trump != nil && defense.Suit == trump.Suit && attack.Suit == trump.Suit {
		return defense.Rank > attack.Rank
	}
	return false
}

func lowestCard(hand []*pb.Card, trump *pb.Card) *pb.Card {
	var best *pb.Card
	for _, c := range hand {
		if c == nil {
			continue
		}
		if best == nil || cardLess(c, best, trump) {
			best = c
		}
	}
	return best
}

func cardLess(a, b, trump *pb.Card) bool {
	aTrump := trump != nil && a.Suit == trump.Suit
	bTrump := trump != nil && b.Suit == trump.Suit
	if aTrump != bTrump {
		return !aTrump && bTrump
	}
	return a.Rank < b.Rank
}
