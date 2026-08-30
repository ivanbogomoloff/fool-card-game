package com.example.foolcardgame.domain.audio

interface GameSoundEffects {
    fun play(kind: GameSoundKind)
}

object NoOpGameSoundEffects : GameSoundEffects {
    override fun play(kind: GameSoundKind) = Unit
}
