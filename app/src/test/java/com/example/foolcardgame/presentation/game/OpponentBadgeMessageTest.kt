package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.GameActionKindDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpponentBadgeMessageTest {

    @Test
    fun passTookBito_returnBadgeLabels() {
        assertEquals("Пас", opponentBadgeMessage(GameActionKindDto.PASS))
        assertEquals("Беру", opponentBadgeMessage(GameActionKindDto.TOOK))
        assertEquals("Бито", opponentBadgeMessage(GameActionKindDto.BITO))
    }

    @Test
    fun otherActions_returnNull() {
        assertNull(opponentBadgeMessage(GameActionKindDto.ATTACK))
        assertNull(opponentBadgeMessage(GameActionKindDto.DEFEND))
        assertNull(opponentBadgeMessage(GameActionKindDto.THROW_IN))
    }
}
