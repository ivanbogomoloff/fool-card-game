package com.example.foolcardgame.ui.components.game

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckDisplayModeTest {

    @Test
    fun deckCountZero_isHidden() {
        assertEquals(DeckDisplayMode.Hidden, deckDisplayMode(0))
    }

    @Test
    fun deckCountNegative_isHidden() {
        assertEquals(DeckDisplayMode.Hidden, deckDisplayMode(-1))
    }

    @Test
    fun deckCountOne_isTrumpOnly() {
        assertEquals(DeckDisplayMode.TrumpOnly, deckDisplayMode(1))
    }

    @Test
    fun deckCountTwoOrMore_isStackWithTrump() {
        assertEquals(DeckDisplayMode.StackWithTrump, deckDisplayMode(2))
        assertEquals(DeckDisplayMode.StackWithTrump, deckDisplayMode(18))
    }
}
