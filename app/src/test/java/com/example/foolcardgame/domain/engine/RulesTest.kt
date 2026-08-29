package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private val trump = Suit.HEARTS

    @Test
    fun beats_sameSuitHigher() {
        assertTrue(
            Rules.beats(
                defense = Card(Suit.SPADES, Rank.TEN),
                attack = Card(Suit.SPADES, Rank.SEVEN),
                trumpSuit = trump,
            ),
        )
        assertFalse(
            Rules.beats(
                defense = Card(Suit.SPADES, Rank.SIX),
                attack = Card(Suit.SPADES, Rank.SEVEN),
                trumpSuit = trump,
            ),
        )
    }

    @Test
    fun beats_trumpBeatsNonTrump() {
        assertTrue(
            Rules.beats(
                defense = Card(Suit.HEARTS, Rank.SIX),
                attack = Card(Suit.SPADES, Rank.ACE),
                trumpSuit = trump,
            ),
        )
    }

    @Test
    fun beats_higherTrumpBeatsLowerTrump() {
        assertTrue(
            Rules.beats(
                defense = Card(Suit.HEARTS, Rank.KING),
                attack = Card(Suit.HEARTS, Rank.NINE),
                trumpSuit = trump,
            ),
        )
        assertFalse(
            Rules.beats(
                defense = Card(Suit.HEARTS, Rank.SIX),
                attack = Card(Suit.HEARTS, Rank.ACE),
                trumpSuit = trump,
            ),
        )
    }

    @Test
    fun beats_nonTrumpDoesNotBeatOtherSuit() {
        assertFalse(
            Rules.beats(
                defense = Card(Suit.CLUBS, Rank.ACE),
                attack = Card(Suit.SPADES, Rank.SIX),
                trumpSuit = trump,
            ),
        )
    }

    @Test
    fun canThrow_onlyMatchingRanks() {
        assertTrue(Rules.canThrow(Card(Suit.CLUBS, Rank.SEVEN), setOf(Rank.SEVEN, Rank.TEN)))
        assertFalse(Rules.canThrow(Card(Suit.CLUBS, Rank.ACE), setOf(Rank.SEVEN, Rank.TEN)))
        assertFalse(Rules.canThrow(Card(Suit.CLUBS, Rank.SEVEN), emptySet()))
    }

    @Test
    fun maxAttackCards_min6AndDefenderHand() {
        assertEqualsMin(6, Rules.maxAttackCards(8))
        assertEqualsMin(4, Rules.maxAttackCards(4))
        assertEqualsMin(1, Rules.maxAttackCards(1))
    }

    private fun assertEqualsMin(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
