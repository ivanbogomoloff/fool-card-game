package com.example.foolcardgame.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.client.GameClient

import com.example.foolcardgame.domain.audio.GameSoundEffects
import com.example.foolcardgame.domain.audio.NoOpGameSoundEffects

class GameViewModelFactory(
    private val gameClient: GameClient,
    private val sessionId: GameSessionId,
    private val soundEffects: GameSoundEffects = NoOpGameSoundEffects,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(gameClient, sessionId, soundEffects) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
