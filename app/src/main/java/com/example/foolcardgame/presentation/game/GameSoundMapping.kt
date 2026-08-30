package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.GameActionKindDto
import com.example.foolcardgame.domain.audio.GameSoundKind

internal fun GameActionKindDto.toGameSoundKind(): GameSoundKind? = when (this) {
    GameActionKindDto.ATTACK,
    GameActionKindDto.DEFEND,
    GameActionKindDto.THROW_IN,
    -> GameSoundKind.CARD_PLAY
    GameActionKindDto.BITO -> GameSoundKind.BITO
    GameActionKindDto.TOOK -> GameSoundKind.TAKE
    GameActionKindDto.PASS -> null
}
