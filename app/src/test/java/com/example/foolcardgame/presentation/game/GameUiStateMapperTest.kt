package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.client.MockGameStates
import com.example.foolcardgame.domain.model.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStateMapperTest {

    @Test
    fun map_lobbyWaiting_showsReadyInGameMode() {
        val uiState = GameUiStateMapper.map(MockGameStates.lobbyWaiting())

        assertEquals(GamePhase.IN_PROGRESS, uiState.phase)
        assertEquals(HandPrimaryAction.READY, uiState.actions.primary)
        assertEquals(12, uiState.hand.size)
        assertFalse(uiState.hasDisconnectedOpponent)
    }

    @Test
    fun map_inProgress_showsBitoAsPrimaryAction() {
        val uiState = GameUiStateMapper.map(MockGameStates.inProgress())

        assertEquals(GamePhase.IN_PROGRESS, uiState.phase)
        assertEquals(HandPrimaryAction.BITO, uiState.actions.primary)
        assertEquals(12, uiState.hand.size)
        assertEquals(2, uiState.tablePairs.size)
        assertEquals(2, uiState.opponents.size)
    }

    @Test
    fun map_takeOnly_showsTakeAsPrimaryAction() {
        val dto = MockGameStates.inProgress().copy(
            canBito = false,
            canPass = false,
            canTake = true,
            canReady = false,
        )

        val uiState = GameUiStateMapper.map(dto)

        assertEquals(HandPrimaryAction.TAKE, uiState.actions.primary)
    }

    @Test
    fun map_disconnectedPlayer_showsInGameWithBanner() {
        val uiState = GameUiStateMapper.map(MockGameStates.lobbyWithDisconnected())

        assertEquals(GamePhase.IN_PROGRESS, uiState.phase)
        assertTrue(uiState.hasDisconnectedOpponent)
        val disconnected = uiState.opponents.first { it.displayName == "Бот 2" }
        assertFalse(disconnected.isConnected)
    }

    @Test
    fun map_finished_showsResultMessage() {
        val uiState = GameUiStateMapper.map(MockGameStates.finished())

        assertEquals(GamePhase.FINISHED, uiState.phase)
        assertEquals(HandPrimaryAction.NONE, uiState.actions.primary)
        assertTrue(uiState.resultMessage?.contains("Победитель") == true)
    }

    @Test
    fun resolvePrimaryAction_priorityReadyOverOthers() {
        val dto = MockGameStates.inProgress().copy(
            canReady = true,
            canTake = true,
            canBito = true,
            canPass = true,
        )
        assertEquals(HandPrimaryAction.READY, GameUiStateMapper.resolvePrimaryAction(dto))
    }
}
