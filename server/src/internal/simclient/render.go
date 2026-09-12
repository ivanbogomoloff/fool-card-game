package simclient

import (
	"fmt"
	"io"
	"strings"

	"foolcardgame/server/internal/pb"
)

var suitUTF = map[pb.Suit]string{
	pb.Suit_SPADES:   "♠",
	pb.Suit_HEARTS:   "♥",
	pb.Suit_DIAMONDS: "♦",
	pb.Suit_CLUBS:    "♣",
}

var rankRU = map[pb.Rank]string{
	pb.Rank_SIX:   "6",
	pb.Rank_SEVEN: "7",
	pb.Rank_EIGHT: "8",
	pb.Rank_NINE:  "9",
	pb.Rank_TEN:   "10",
	pb.Rank_JACK:  "Валет",
	pb.Rank_QUEEN: "Дама",
	pb.Rank_KING:  "Король",
	pb.Rank_ACE:   "Туз",
}

// FormatCard русское имя + масть.
func FormatCard(c *pb.Card) string {
	if c == nil {
		return "?"
	}
	r := rankRU[c.Rank]
	if r == "" {
		r = c.Rank.String()
	}
	s := suitUTF[c.Suit]
	if s == "" {
		s = "?"
	}
	return r + " " + s
}

// FormatPair атака > защита или ?
func FormatPair(p *pb.TablePair) string {
	if p == nil {
		return "?"
	}
	def := "?"
	if p.Defense != nil {
		def = FormatCard(p.Defense)
	}
	return FormatCard(p.Attack) + " > " + def
}

// RenderTable печатает козырь, стол и руку.
func RenderTable(out io.Writer, st *pb.GameState) {
	if st == nil {
		fmt.Fprintln(out, "(нет GameState)")
		return
	}
	if t := st.GetTrump(); t != nil {
		fmt.Fprintf(out, "Козырь: %s\n", FormatCard(t))
	} else {
		fmt.Fprintln(out, "Козырь: (нет)")
	}
	fmt.Fprintln(out, "Стол:")
	if len(st.TablePairs) == 0 {
		fmt.Fprintln(out, "  (пусто)")
	} else {
		for i, p := range st.TablePairs {
			fmt.Fprintf(out, "%d. %s\n", i+1, FormatPair(p))
		}
	}
	fmt.Fprintln(out, "\nВаши карты:")
	if len(st.LocalHand) == 0 {
		fmt.Fprintln(out, "  (пусто)")
	} else {
		for i, c := range st.LocalHand {
			fmt.Fprintf(out, "%d. %s\n", i+1, FormatCard(c))
		}
	}
	flags := []string{}
	if st.CanPass {
		flags = append(flags, "can_pass")
	}
	if st.CanBito {
		flags = append(flags, "can_bito")
	}
	if st.CanTake {
		flags = append(flags, "can_take")
	}
	if st.CanReady {
		flags = append(flags, "can_ready")
	}
	if len(flags) > 0 {
		fmt.Fprintf(out, "\n[%s]\n", strings.Join(flags, ", "))
	}
}

// PrintServerMessage краткий лог входящего сообщения.
func PrintServerMessage(out io.Writer, msg *pb.ServerMessage) {
	if msg == nil {
		return
	}
	switch p := msg.Payload.(type) {
	case *pb.ServerMessage_Pong:
		fmt.Fprintln(out, "<< Pong")
	case *pb.ServerMessage_Error:
		fmt.Fprintf(out, "<< Error %s: %s\n", p.Error.GetCode(), p.Error.GetMessage())
	case *pb.ServerMessage_QueueState:
		fmt.Fprintf(out, "<< QueueState player=%s count=%d phase=%s\n",
			p.QueueState.GetPlayerId(), p.QueueState.GetWaitingCount(), p.QueueState.GetPhase())
	case *pb.ServerMessage_RoomState:
		fmt.Fprintf(out, "<< RoomState game=%s code=%s players=%d started=%v\n",
			p.RoomState.GetGameId(), p.RoomState.GetAccessCode(), len(p.RoomState.GetPlayers()), p.RoomState.GetStarted())
	case *pb.ServerMessage_GameState:
		fmt.Fprintf(out, "<< GameState game=%s phase=%s hand=%d table=%d\n",
			p.GameState.GetGameId(), p.GameState.GetPhase(), len(p.GameState.GetLocalHand()), len(p.GameState.GetTablePairs()))
	case *pb.ServerMessage_MatchStarted:
		fmt.Fprintf(out, "<< MatchStarted game=%s\n", p.MatchStarted.GetGameId())
	case *pb.ServerMessage_LeftAck:
		fmt.Fprintln(out, "<< LeftAck")
	case *pb.ServerMessage_Kicked:
		fmt.Fprintf(out, "<< Kicked: %s\n", p.Kicked.GetReason())
	default:
		fmt.Fprintf(out, "<< %T\n", msg.Payload)
	}
}
