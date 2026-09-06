package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import kotlin.random.Random

object Deck {

    const val SIZE = 36

    fun create36(): List<Card> = buildList {
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                add(Card(suit, rank))
            }
        }
    }

    /** Детерминированное тасование для тестов и воспроизводимых офлайн-игр. */
    fun shuffled(seed: Long): List<Card> {
        val cards = create36().toMutableList()
        cards.shuffle(Random(seed))
        return cards
    }
}
