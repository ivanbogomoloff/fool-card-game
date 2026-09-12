package engine

import (
	"math/rand"
)

const deckSize = 36

// Create36 — полная колода 36 карт.
func Create36() []Card {
	out := make([]Card, 0, deckSize)
	for s := SuitSpades; s <= SuitClubs; s++ {
		for r := RankSix; r <= RankAce; r++ {
			out = append(out, Card{Suit: s, Rank: r})
		}
	}
	return out
}

// Shuffled — детерминированное тасование (seed для тестов и воспроизводимых партий).
func Shuffled(seed int64) []Card {
	cards := Create36()
	r := rand.New(rand.NewSource(seed))
	r.Shuffle(len(cards), func(i, j int) {
		cards[i], cards[j] = cards[j], cards[i]
	})
	return cards
}
