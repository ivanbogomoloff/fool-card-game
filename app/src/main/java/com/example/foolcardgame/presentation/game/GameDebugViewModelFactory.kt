package com.example.foolcardgame.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foolcardgame.data.client.DebugGameClient
import com.example.foolcardgame.data.client.MockGameStates

class GameDebugViewModelFactory(
    private val debugGameClient: DebugGameClient = DebugGameClient(),
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameDebugViewModel::class.java)) {
            return GameDebugViewModel(
                debugGameClient = debugGameClient,
                sessionId = MockGameStates.DEBUG_SESSION_ID,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
