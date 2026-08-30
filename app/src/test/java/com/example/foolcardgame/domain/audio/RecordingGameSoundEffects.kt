package com.example.foolcardgame.domain.audio

class RecordingGameSoundEffects : GameSoundEffects {

    val played = mutableListOf<GameSoundKind>()

    override fun play(kind: GameSoundKind) {
        played.add(kind)
    }
}
