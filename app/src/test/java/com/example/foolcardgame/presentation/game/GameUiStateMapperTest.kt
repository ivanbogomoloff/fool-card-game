package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.client.MockGameStates
import com.example.foolcardgame.domain.model.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStateMapperTest {

    @Test
    fun map_lobbyWaiting_showsReadyActionAndWaitingPlayers() {
        val uiState = GameUiStateMapper.map(MockGameStates.lobbyWaiting())

        assertEquals(GamePhase.LOBBY_WAITING, uiState.phase)
        assertTrue(uiState.actions.readyVisible)
        assertTrue(uiState.actions.readyEnabled)
        assertFalse(uiState.actions.bitoEnabled)
        assertEquals(3, uiState.waitingPlayers.size)
        assertTrue(uiState.waitingPlayers.any { !it.isReady })
    }

    @Test
    fun map_inProgress_enablesBitoAndMapsHand() {
        val uiState = GameUiStateMapper.map(MockGameStates.inProgress())

        assertEquals(GamePhase.IN_PROGRESS, uiState.phase)
        assertFalse(uiState.actions.readyVisible)
        assertTrue(uiState.actions.bitoEnabled)
        assertEquals(5, uiState.hand.size)
        assertEquals(2, uiState.tablePairs.size)
        assertEquals(2, uiState.opponents.size)
    }

    @Test
    fun map_disconnectedPlayer_showsNotConnected() {
        val uiState = GameUiStateMapper.map(MockGameStates.lobbyWithDisconnected())

        val disconnected = uiState.waitingPlayers.first { it.displayName == "Бот 2" }
        assertFalse(disconnected.isConnected)
        assertFalse(disconnected.isReady)
    }

    @Test
    fun map_finished_showsResultMessage() {
        val uiState = GameUiStateMapper.map(MockGameStates.finished())

        assertEquals(GamePhase.FINISHED, uiState.phase)
        assertTrue(uiState.resultMessage?.contains("Победитель") == true)
    }
}
