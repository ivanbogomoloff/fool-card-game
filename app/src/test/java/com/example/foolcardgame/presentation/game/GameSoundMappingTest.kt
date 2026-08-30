package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.GameActionKindDto
import com.example.foolcardgame.domain.audio.GameSoundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSoundMappingTest {

    @Test
    fun cardActions_mapToCardPlay() {
        assertEquals(GameSoundKind.CARD_PLAY, GameActionKindDto.ATTACK.toGameSoundKind())
        assertEquals(GameSoundKind.CARD_PLAY, GameActionKindDto.DEFEND.toGameSoundKind())
        assertEquals(GameSoundKind.CARD_PLAY, GameActionKindDto.THROW_IN.toGameSoundKind())
    }

    @Test
    fun bitoAndTook_mapToDedicatedSounds() {
        assertEquals(GameSoundKind.BITO, GameActionKindDto.BITO.toGameSoundKind())
        assertEquals(GameSoundKind.TAKE, GameActionKindDto.TOOK.toGameSoundKind())
    }

    @Test
    fun pass_hasNoSound() {
        assertNull(GameActionKindDto.PASS.toGameSoundKind())
    }
}
