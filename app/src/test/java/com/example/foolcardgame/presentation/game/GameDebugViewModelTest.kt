package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.client.DebugGameClient
import com.example.foolcardgame.data.client.DebugScenario
import com.example.foolcardgame.domain.model.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GameDebugViewModelTest {

    @Test
    fun setScenario_updatesMappedUiStatePhase() {
        val client = DebugGameClient(initialScenario = DebugScenario.LOBBY_WAITING)

        val lobbyState = GameUiStateMapper.map(client.currentState())
        assertEquals(GamePhase.LOBBY_WAITING, lobbyState.phase)

        client.setScenario(DebugScenario.IN_PROGRESS)
        val gameState = GameUiStateMapper.map(client.currentState())
        assertEquals(GamePhase.IN_PROGRESS, gameState.phase)
        assertEquals(5, gameState.hand.size)
    }

    @Test
    fun setScenario_disconnected_showsDisconnectedPlayer() {
        val client = DebugGameClient(initialScenario = DebugScenario.IN_PROGRESS)
        client.setScenario(DebugScenario.LOBBY_DISCONNECTED)

        val uiState = GameUiStateMapper.map(client.currentState())
        val disconnected = uiState.waitingPlayers.first { it.displayName == "Бот 2" }
        assertFalse(disconnected.isConnected)
    }
}
