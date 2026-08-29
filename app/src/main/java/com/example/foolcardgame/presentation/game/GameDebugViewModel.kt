package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.client.DebugGameClient
import com.example.foolcardgame.data.client.DebugScenario

class GameDebugViewModel(
    private val debugGameClient: DebugGameClient,
    sessionId: String,
) : GameViewModel(debugGameClient, sessionId) {

    fun setScenario(scenario: DebugScenario) {
        debugGameClient.setScenario(scenario)
        val canReady = debugGameClient.currentState().canReady
        restartReadyTimerIfNeeded(canReady = canReady)
    }

    fun clearTable() {
        debugGameClient.clearTable()
    }
}
