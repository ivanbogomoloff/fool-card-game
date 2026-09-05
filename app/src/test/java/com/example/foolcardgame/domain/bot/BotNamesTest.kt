package com.example.foolcardgame.domain.bot

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotNamesTest {

    @Test
    fun pick_returnsDistinctNames() {
        val names = BotNames.pick(3, Random(42))
        assertEquals(3, names.size)
        assertEquals(3, names.toSet().size)
        assertTrue(names.all { it in BotNames.all })
    }

    @Test
    fun pool_containsHollywoodAndRussianClassics() {
        assertEquals(30, BotNames.hollywoodStars.size)
        assertEquals(30, BotNames.russianClassics.size)
        assertEquals(60, BotNames.all.size)
        assertEquals(60, BotNames.all.toSet().size)
    }

    @Test
    fun pick_sameSeed_isStable() {
        assertEquals(
            BotNames.pick(2, Random(7)),
            BotNames.pick(2, Random(7)),
        )
    }
}
