package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.client.MockGameStates
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.ui.components.game.formatReadyTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStateMapperTest {

    @Test
    fun map_lobbyWaiting_showsReadyAndLobbyPhase() {
        val uiState = GameUiStateMapper.map(MockGameStates.lobbyWaiting())

        assertEquals(GamePhase.LOBBY_WAITING, uiState.phase)
        assertEquals(HandPrimaryAction.READY, uiState.actions.primary)
        assertTrue(uiState.hand.isEmpty())
        assertFalse(uiState.hasDisconnectedOpponent)
        assertTrue(uiState.opponents.all { !it.isReady })
    }

    @Test
    fun map_inProgress_showsBitoAsPrimaryAction() {
        val uiState = GameUiStateMapper.map(MockGameStates.inProgress())

        assertEquals(GamePhase.IN_PROGRESS, uiState.phase)
        assertEquals(HandPrimaryAction.BITO, uiState.actions.primary)
        assertEquals(12, uiState.hand.size)
        assertEquals(2, uiState.tablePairs.size)
        assertEquals(2, uiState.opponents.size)
        assertTrue(uiState.opponents.all { it.isReady })
        assertTrue(uiState.isLocalPlayerTurn)
        assertTrue(uiState.opponents.none { it.isCurrentTurn })
    }

    @Test
    fun map_opponentTurn_marksBotAsCurrent() {
        val uiState = GameUiStateMapper.map(MockGameStates.opponentTurn())

        assertFalse(uiState.isLocalPlayerTurn)
        assertEquals(HandPrimaryAction.NONE, uiState.actions.primary)
        val bot1 = uiState.opponents.first { it.id == "bot-1" }
        assertTrue(bot1.isCurrentTurn)
        assertTrue(uiState.opponents.filter { it.id != "bot-1" }.none { it.isCurrentTurn })
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

    @Test
    fun formatReadyTimer_formatsMinutesAndSeconds() {
        assertEquals("1:00", formatReadyTimer(60))
        assertEquals("0:45", formatReadyTimer(45))
        assertEquals("0:05", formatReadyTimer(5))
        assertEquals("0:00", formatReadyTimer(0))
    }
}
