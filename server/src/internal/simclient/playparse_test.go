package simclient_test

import (
	"bytes"
	"strings"
	"testing"

	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/simclient"
)

func TestFormatPair(t *testing.T) {
	p := &pb.TablePair{
		Id:      1,
		Attack:  &pb.Card{Suit: pb.Suit_SPADES, Rank: pb.Rank_QUEEN},
		Defense: &pb.Card{Suit: pb.Suit_SPADES, Rank: pb.Rank_ACE},
	}
	got := simclient.FormatPair(p)
	if !strings.Contains(got, "Дама") || !strings.Contains(got, "Туз") {
		t.Fatalf("got %q", got)
	}
	open := &pb.TablePair{Id: 2, Attack: &pb.Card{Suit: pb.Suit_SPADES, Rank: pb.Rank_TEN}}
	if !strings.Contains(simclient.FormatPair(open), "?") {
		t.Fatal(simclient.FormatPair(open))
	}
}

func TestParsePlayInput_Defend(t *testing.T) {
	hand := []*pb.Card{
		{Suit: pb.Suit_SPADES, Rank: pb.Rank_JACK},
		{Suit: pb.Suit_SPADES, Rank: pb.Rank_KING},
	}
	pairs := []*pb.TablePair{{Id: 7, Attack: &pb.Card{Suit: pb.Suit_SPADES, Rank: pb.Rank_TEN}}}
	intent, err := simclient.ParsePlayInput("2>1", hand, pairs)
	if err != nil {
		t.Fatal(err)
	}
	if intent.Kind != "play" || intent.HandIndex != 1 || intent.TargetPairID == nil || *intent.TargetPairID != 7 {
		t.Fatalf("%+v", intent)
	}
}

func TestParsePlayInput_AttackOrAdd(t *testing.T) {
	hand := []*pb.Card{{Suit: pb.Suit_HEARTS, Rank: pb.Rank_SIX}}
	intent, err := simclient.ParsePlayInput("1", hand, nil)
	if err != nil || intent.Kind != "play" {
		t.Fatalf("%v %+v", err, intent)
	}
	pairs := []*pb.TablePair{{Id: 1, Attack: hand[0]}}
	intent2, err := simclient.ParsePlayInput("1", hand, pairs)
	if err != nil || intent2.Kind != "add" {
		t.Fatalf("%v %+v", err, intent2)
	}
}

func TestParsePlayInput_PassBito(t *testing.T) {
	p, err := simclient.ParsePlayInput("p", nil, nil)
	if err != nil || p.Kind != "pass" {
		t.Fatal(err, p)
	}
	b, err := simclient.ParsePlayInput("b", nil, nil)
	if err != nil || b.Kind != "bito" {
		t.Fatal(err, b)
	}
}

func TestRenderTable(t *testing.T) {
	var buf bytes.Buffer
	st := &pb.GameState{
		TablePairs: []*pb.TablePair{{
			Id: 1, Attack: &pb.Card{Suit: pb.Suit_SPADES, Rank: pb.Rank_QUEEN},
			Defense: &pb.Card{Suit: pb.Suit_SPADES, Rank: pb.Rank_ACE},
		}},
		LocalHand: []*pb.Card{{Suit: pb.Suit_SPADES, Rank: pb.Rank_JACK}},
		CanPass:   true,
	}
	simclient.RenderTable(&buf, st)
	s := buf.String()
	if !strings.Contains(s, "Стол:") || !strings.Contains(s, "Валет") {
		t.Fatal(s)
	}
}
