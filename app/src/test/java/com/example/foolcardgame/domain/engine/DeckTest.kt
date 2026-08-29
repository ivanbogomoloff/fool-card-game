package com.example.foolcardgame.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeckTest {

    @Test
    fun create36_hasAllSuitsAndRanks() {
        val deck = Deck.create36()
        assertEquals(Deck.SIZE, deck.size)
        assertEquals(Deck.SIZE, deck.toSet().size)
    }

    @Test
    fun shuffled_isDeterministicWithSameSeed() {
        val a = Deck.shuffled(seed = 42)
        val b = Deck.shuffled(seed = 42)
        assertEquals(a, b)
    }

    @Test
    fun shuffled_differsWithDifferentSeed() {
        val a = Deck.shuffled(seed = 1)
        val b = Deck.shuffled(seed = 2)
        assertNotEquals(a, b)
    }
}
