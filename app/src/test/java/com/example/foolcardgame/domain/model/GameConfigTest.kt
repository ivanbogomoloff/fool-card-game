package com.example.foolcardgame.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GameConfigTest {

    @Test
    fun defaults_botReactionRangeIsOneToFiveSeconds() {
        val config = GameConfig(botCount = 1)

        assertEquals(GameConfig.DEFAULT_BOT_THINK_MIN_MS, config.botThinkMinMs)
        assertEquals(GameConfig.DEFAULT_BOT_THINK_MAX_MS, config.botThinkMaxMs)
        assertEquals(1_000L, config.botThinkMinMs)
        assertEquals(5_000L, config.botThinkMaxMs)
    }
}
