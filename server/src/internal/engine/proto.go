package engine

import (
	"fmt"
	"strings"

	"foolcardgame/server/internal/pb"
)

func suitToProto(s Suit) pb.Suit {
	switch s {
	case SuitSpades:
		return pb.Suit_SPADES
	case SuitHearts:
		return pb.Suit_HEARTS
	case SuitDiamonds:
		return pb.Suit_DIAMONDS
	case SuitClubs:
		return pb.Suit_CLUBS
	default:
		return pb.Suit_SUIT_UNSPECIFIED
	}
}

func rankToProto(r Rank) pb.Rank {
	switch r {
	case RankSix:
		return pb.Rank_SIX
	case RankSeven:
		return pb.Rank_SEVEN
	case RankEight:
		return pb.Rank_EIGHT
	case RankNine:
		return pb.Rank_NINE
	case RankTen:
		return pb.Rank_TEN
	case RankJack:
		return pb.Rank_JACK
	case RankQueen:
		return pb.Rank_QUEEN
	case RankKing:
		return pb.Rank_KING
	case RankAce:
		return pb.Rank_ACE
	default:
		return pb.Rank_RANK_UNSPECIFIED
	}
}

// CardToProto конвертирует карту в protobuf.
func CardToProto(c Card) *pb.Card {
	return &pb.Card{Suit: suitToProto(c.Suit), Rank: rankToProto(c.Rank)}
}

func cardsToProto(cards []Card) []*pb.Card {
	out := make([]*pb.Card, len(cards))
	for i, c := range cards {
		out[i] = CardToProto(c)
	}
	return out
}

func playerStatusToProto(st PlayerStatus) pb.PlayerStatus {
	switch st {
	case PlayerStatusWaiting:
		return pb.PlayerStatus_WAITING
	case PlayerStatusPlaying:
		return pb.PlayerStatus_PLAYING
	case PlayerStatusDisconnected:
		return pb.PlayerStatus_DISCONNECTED
	case PlayerStatusLeft:
		return pb.PlayerStatus_LEFT
	default:
		return pb.PlayerStatus_PLAYER_STATUS_UNSPECIFIED
	}
}

func phaseToProto(p Phase) pb.GamePhase {
	switch p {
	case PhaseLobbyWaiting:
		return pb.GamePhase_LOBBY_WAITING
	case PhaseInProgress:
		return pb.GamePhase_IN_PROGRESS
	case PhaseFinished:
		return pb.GamePhase_FINISHED
	default:
		return pb.GamePhase_GAME_PHASE_UNSPECIFIED
	}
}

func roundEventToProto(e *RoundEvent) *pb.RoundEvent {
	if e == nil {
		return nil
	}
	kind := pb.RoundEventKind_ROUND_EVENT_KIND_UNSPECIFIED
	switch e.Kind {
	case RoundEventTook:
		kind = pb.RoundEventKind_TOOK
	case RoundEventBito:
		kind = pb.RoundEventKind_BITO
	}
	return &pb.RoundEvent{Kind: kind, PlayerId: e.PlayerID, AtTick: e.AtTick}
}

func actionEventToProto(e *GameActionEvent) *pb.GameActionEvent {
	if e == nil {
		return nil
	}
	kind := pb.GameActionKind_GAME_ACTION_KIND_UNSPECIFIED
	switch e.Kind {
	case ActionAttack:
		kind = pb.GameActionKind_ATTACK
	case ActionDefend:
		kind = pb.GameActionKind_DEFEND
	case ActionThrowIn:
		kind = pb.GameActionKind_THROW_IN
	case ActionPass:
		kind = pb.GameActionKind_PASS
	case ActionTook:
		kind = pb.GameActionKind_TOOK_ACTION
	case ActionBito:
		kind = pb.GameActionKind_BITO_ACTION
	}
	return &pb.GameActionEvent{Kind: kind, PlayerId: e.PlayerID, AtTick: e.AtTick}
}

func tablePairsToProto(pairs []TablePair) []*pb.TablePair {
	out := make([]*pb.TablePair, len(pairs))
	for i, p := range pairs {
		tp := &pb.TablePair{
			Id:     p.ID,
			Attack: CardToProto(p.Attack),
		}
		if p.Defense != nil {
			tp.Defense = CardToProto(*p.Defense)
		}
		out[i] = tp
	}
	return out
}

// CardFromProto — карта из protobuf (клиентский ход).
func CardFromProto(c *pb.Card) (Card, error) {
	if c == nil {
		return Card{}, fmt.Errorf("пустая карта")
	}
	suit, err := suitFromProto(c.Suit)
	if err != nil {
		return Card{}, err
	}
	rank, err := rankFromProto(c.Rank)
	if err != nil {
		return Card{}, err
	}
	return Card{Suit: suit, Rank: rank}, nil
}

func suitFromProto(s pb.Suit) (Suit, error) {
	switch s {
	case pb.Suit_SPADES:
		return SuitSpades, nil
	case pb.Suit_HEARTS:
		return SuitHearts, nil
	case pb.Suit_DIAMONDS:
		return SuitDiamonds, nil
	case pb.Suit_CLUBS:
		return SuitClubs, nil
	default:
		return 0, fmt.Errorf("неизвестная масть")
	}
}

func rankFromProto(r pb.Rank) (Rank, error) {
	switch r {
	case pb.Rank_SIX:
		return RankSix, nil
	case pb.Rank_SEVEN:
		return RankSeven, nil
	case pb.Rank_EIGHT:
		return RankEight, nil
	case pb.Rank_NINE:
		return RankNine, nil
	case pb.Rank_TEN:
		return RankTen, nil
	case pb.Rank_JACK:
		return RankJack, nil
	case pb.Rank_QUEEN:
		return RankQueen, nil
	case pb.Rank_KING:
		return RankKing, nil
	case pb.Rank_ACE:
		return RankAce, nil
	default:
		return 0, fmt.Errorf("неизвестный ранг")
	}
}

// FormatCardUTF — ♠A / ♥K для логов.
func FormatCardUTF(c Card) string {
	suits := map[Suit]string{
		SuitSpades: "♠", SuitHearts: "♥", SuitDiamonds: "♦", SuitClubs: "♣",
	}
	ranks := map[Rank]string{
		RankSix: "6", RankSeven: "7", RankEight: "8", RankNine: "9", RankTen: "10",
		RankJack: "J", RankQueen: "Q", RankKing: "K", RankAce: "A",
	}
	return suits[c.Suit] + ranks[c.Rank]
}

// FormatHandUTF — [♠6 ♥K].
func FormatHandUTF(cards []Card) string {
	if len(cards) == 0 {
		return "[]"
	}
	parts := make([]string, len(cards))
	for i, c := range cards {
		parts[i] = FormatCardUTF(c)
	}
	return "[" + strings.Join(parts, " ") + "]"
}
