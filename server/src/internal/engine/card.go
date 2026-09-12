package engine

import "fmt"

// Suit — масть карты.
type Suit int

const (
	SuitSpades Suit = iota + 1
	SuitHearts
	SuitDiamonds
	SuitClubs
)

// Rank — достоинство (порядок сравнения: SIX < … < ACE).
type Rank int

const (
	RankSix Rank = iota + 1
	RankSeven
	RankEight
	RankNine
	RankTen
	RankJack
	RankQueen
	RankKing
	RankAce
)

// Card — карта в колоде / на столе / в руке.
type Card struct {
	Suit Suit
	Rank Rank
}

// ID — стабильный идентификатор карты (как в Kotlin).
func (c Card) ID() string {
	return fmt.Sprintf("%d_%d", c.Suit, c.Rank)
}

func (r Rank) beats(other Rank) bool {
	return r > other
}
