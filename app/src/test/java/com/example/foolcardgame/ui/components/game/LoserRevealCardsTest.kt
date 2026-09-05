package com.example.foolcardgame.ui.components.game

import androidx.compose.ui.geometry.Rect
import com.example.foolcardgame.presentation.game.CardUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoserRevealCardsTest {

    @Test
    fun revealLoserCardsToTablePairs_mapsAttackOnly() {
        val cards = listOf(
            CardUi(id = "h6", rankLabel = "6", suitSymbol = "♥", isRed = true),
            CardUi(id = "s7", rankLabel = "7", suitSymbol = "♠", isRed = false),
        )
        val pairs = revealLoserCardsToTablePairs(cards)
        assertEquals(2, pairs.size)
        assertEquals("h6", pairs[0].attack.id)
        assertNull(pairs[0].defense)
        assertEquals("s7", pairs[1].attack.id)
        assertTrue(pairs[0].id < 0)
        assertTrue(pairs[1].id < pairs[0].id)
    }

    @Test
    fun loserRevealTableSlotTops_threeColumnGrid() {
        val bounds = Rect(0f, 0f, 300f, 200f)
        val slots = loserRevealTableSlotTops(
            tableBounds = bounds,
            count = 4,
            cardWidthPx = 60f,
            cardHeightPx = 90f,
        )
        assertEquals(4, slots.size)
        // First two on row 0; index 3 on row 1
        assertEquals(slots[0].y, slots[1].y, 0.1f)
        assertTrue(slots[3].y > slots[0].y)
        assertTrue(slots[1].x > slots[0].x)
    }
}
