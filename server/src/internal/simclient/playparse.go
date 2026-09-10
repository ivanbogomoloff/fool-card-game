package simclient

import (
	"fmt"
	"strconv"
	"strings"

	"foolcardgame/server/internal/pb"
)

// PlayIntent разобранный ввод хода.
type PlayIntent struct {
	Kind          string // play, add, pass, bito
	HandIndex     int    // 0-based; -1 если нет
	TargetPairID  *int32 // id пары на столе (из proto), не индекс
	HandCard      *pb.Card
}

// ParsePlayInput разбирает "2>1", "3", "p", "b".
// hand/table индексы 1-based как в UI; pairID берётся из pairs[tableIdx-1].Id.
func ParsePlayInput(line string, hand []*pb.Card, pairs []*pb.TablePair) (PlayIntent, error) {
	line = strings.TrimSpace(strings.ToLower(line))
	switch line {
	case "p", "pass":
		return PlayIntent{Kind: "pass", HandIndex: -1}, nil
	case "b", "bito":
		return PlayIntent{Kind: "bito", HandIndex: -1}, nil
	}
	if line == "" {
		return PlayIntent{}, fmt.Errorf("пустой ввод")
	}
	if strings.Contains(line, ">") {
		parts := strings.Split(line, ">")
		if len(parts) != 2 {
			return PlayIntent{}, fmt.Errorf("ожидается N>M")
		}
		hi, err := strconv.Atoi(strings.TrimSpace(parts[0]))
		if err != nil || hi < 1 || hi > len(hand) {
			return PlayIntent{}, fmt.Errorf("номер карты руки: 1..%d", len(hand))
		}
		ti, err := strconv.Atoi(strings.TrimSpace(parts[1]))
		if err != nil || ti < 1 || ti > len(pairs) {
			return PlayIntent{}, fmt.Errorf("номер пары стола: 1..%d", len(pairs))
		}
		pid := pairs[ti-1].GetId()
		return PlayIntent{
			Kind:         "play",
			HandIndex:    hi - 1,
			TargetPairID: &pid,
			HandCard:     hand[hi-1],
		}, nil
	}
	hi, err := strconv.Atoi(line)
	if err != nil || hi < 1 || hi > len(hand) {
		return PlayIntent{}, fmt.Errorf("номер карты руки: 1..%d", len(hand))
	}
	kind := "play"
	if len(pairs) > 0 {
		kind = "add" // подкид, если стол не пуст
	}
	return PlayIntent{
		Kind:      kind,
		HandIndex: hi - 1,
		HandCard:  hand[hi-1],
	}, nil
}

// ToClientMessage строит protobuf сообщение.
func (p PlayIntent) ToClientMessage() *pb.ClientMessage {
	switch p.Kind {
	case "pass":
		return &pb.ClientMessage{Payload: &pb.ClientMessage_Pass{Pass: &pb.Pass{}}}
	case "bito":
		return &pb.ClientMessage{Payload: &pb.ClientMessage_Bito{Bito: &pb.Bito{}}}
	case "add":
		return &pb.ClientMessage{Payload: &pb.ClientMessage_AddCard{AddCard: &pb.AddCard{Card: p.HandCard}}}
	default:
		msg := &pb.PlayCard{Card: p.HandCard}
		if p.TargetPairID != nil {
			msg.TargetPairId = p.TargetPairID
		}
		return &pb.ClientMessage{Payload: &pb.ClientMessage_PlayCard{PlayCard: msg}}
	}
}
