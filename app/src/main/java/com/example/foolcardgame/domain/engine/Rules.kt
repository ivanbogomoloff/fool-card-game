package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import kotlin.math.min

object Rules {

    fun beats(defense: Card, attack: Card, trumpSuit: Suit): Boolean {
        return when {
            defense.suit == attack.suit -> defense.rank > attack.rank
            defense.suit == trumpSuit && attack.suit != trumpSuit -> true
            defense.suit == trumpSuit && attack.suit == trumpSuit ->
                defense.rank > attack.rank
            else -> false
        }
    }

    fun canThrow(card: Card, ranksOnTable: Set<Rank>): Boolean =
        ranksOnTable.isNotEmpty() && card.rank in ranksOnTable

    fun maxAttackCards(defenderHandSizeAtRoundStart: Int): Int =
        min(6, defenderHandSizeAtRoundStart)

    fun canAddAttackCard(
        currentAttackCount: Int,
        defenderHandSizeAtRoundStart: Int,
    ): Boolean = currentAttackCount < maxAttackCards(defenderHandSizeAtRoundStart)
}
