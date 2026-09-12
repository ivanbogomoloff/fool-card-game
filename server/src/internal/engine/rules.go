package engine

// Rules — проверки отбивки и подкида (порт Kotlin Rules).
type Rules struct{}

// Beats — отбивает ли defense карту attack при козыре trumpSuit.
func (Rules) Beats(defense, attack Card, trumpSuit Suit) bool {
	switch {
	case defense.Suit == attack.Suit:
		return defense.Rank.beats(attack.Rank)
	case defense.Suit == trumpSuit && attack.Suit != trumpSuit:
		return true
	case defense.Suit == trumpSuit && attack.Suit == trumpSuit:
		return defense.Rank.beats(attack.Rank)
	default:
		return false
	}
}

// CanThrow — можно ли подкинуть карту по рангу на столе.
func (Rules) CanThrow(card Card, ranksOnTable map[Rank]struct{}) bool {
	if len(ranksOnTable) == 0 {
		return false
	}
	_, ok := ranksOnTable[card.Rank]
	return ok
}

// MaxAttackCards — лимит атакующих карт в раунде.
func (Rules) MaxAttackCards(defenderHandSizeAtRoundStart int) int {
	if defenderHandSizeAtRoundStart < 6 {
		return defenderHandSizeAtRoundStart
	}
	return 6
}

// CanAddAttackCard — есть ли место для ещё одной атаки.
func (Rules) CanAddAttackCard(currentAttackCount, defenderHandSizeAtRoundStart int) bool {
	return currentAttackCount < (Rules{}).MaxAttackCards(defenderHandSizeAtRoundStart)
}
