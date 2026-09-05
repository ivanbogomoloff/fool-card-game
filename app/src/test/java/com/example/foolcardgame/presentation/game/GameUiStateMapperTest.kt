package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.client.MockGameStates
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.ui.components.game.formatReadyTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiStateMapperTest {

    @Test
    fun map_lobbyWaiting_showsReadyAndLobbyPhase() {
        val uiState = GameUiStateMapper.map(MockGameStates.lobbyWaiting())

        assertEquals(GamePhase.LOBBY_WAITING, uiState.phase)
        assertEquals(HandPrimaryAction.READY, uiState.actions.primary)
        assertEquals(6, uiState.hand.size)
        assertEquals("♥", uiState.trump?.suitSymbol)
        assertEquals(18, uiState.deckCount)
        assertFalse(uiState.hasDisconnectedOpponent)
        assertTrue(uiState.opponents.all { !it.isReady })
        assertTrue(uiState.opponents.all { it.roleBanner == OpponentRoleBanner.NONE })
    }

    @Test
    fun map_inProgress_withUnbeaten_marksDefenderNotAttacker() {
        val uiState = GameUiStateMapper.map(MockGameStates.inProgress())

        assertEquals(GamePhase.IN_PROGRESS, uiState.phase)
        assertEquals(HandPrimaryAction.BITO, uiState.actions.primary)
        assertEquals(12, uiState.hand.size)
        assertEquals(2, uiState.tablePairs.size)
        assertEquals(2, uiState.opponents.size)
        assertTrue(uiState.opponents.all { it.isReady })
        assertTrue(uiState.isLocalPlayerTurn)
        assertFalse(uiState.isLocalAttacking)
        assertFalse(uiState.isLocalDefending)

        val bot1 = uiState.opponents.first { it.id == "bot-1" }
        assertEquals(OpponentRoleBanner.DEFENDING, bot1.roleBanner)
        assertTrue(uiState.opponents.filter { it.id != "bot-1" }.all {
            it.roleBanner == OpponentRoleBanner.NONE
        })
    }

    @Test
    fun map_opponentTurn_marksBotAsAttacking() {
        val uiState = GameUiStateMapper.map(MockGameStates.opponentTurn())

        assertFalse(uiState.isLocalPlayerTurn)
        assertEquals(HandPrimaryAction.NONE, uiState.actions.primary)
        assertTrue(uiState.isLocalDefending.not())
        val bot1 = uiState.opponents.first { it.id == "bot-1" }
        assertEquals(OpponentRoleBanner.ATTACKING, bot1.roleBanner)
        assertTrue(uiState.opponents.filter { it.id != "bot-1" }.all {
            it.roleBanner == OpponentRoleBanner.NONE
        })
    }

    @Test
    fun map_emptyTable_localAttacker_isLocalAttacking() {
        val dto = MockGameStates.opponentTurn().copy(
            currentPlayerId = MockGameStates.LOCAL_PLAYER_ID,
            attackerId = MockGameStates.LOCAL_PLAYER_ID,
            defenderId = "bot-1",
        )
        val uiState = GameUiStateMapper.map(dto)

        assertTrue(uiState.isLocalAttacking)
        assertFalse(uiState.isLocalDefending)
        assertTrue(uiState.opponents.all { it.roleBanner == OpponentRoleBanner.NONE })
    }

    @Test
    fun map_takePending_localIsDefending() {
        val uiState = GameUiStateMapper.map(MockGameStates.takePending())

        assertTrue(uiState.isLocalDefending)
        assertFalse(uiState.isLocalAttacking)
        assertEquals(HandPrimaryAction.TAKE, uiState.actions.primary)
        // While defending, attacker has no «Ходит» banner
        assertTrue(uiState.opponents.all { it.roleBanner == OpponentRoleBanner.NONE })
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
        assertEquals(HandPrimaryAction.FINISHED, uiState.actions.primary)
        assertTrue(uiState.resultMessage?.contains("Победитель") == true)
        assertNull(uiState.finishedSummary)
        assertTrue(uiState.canRevealLoserCards)
        assertTrue(uiState.tablePairs.isNotEmpty())
        assertEquals("bot-2", uiState.loserId)
        assertEquals(3, uiState.revealLoserCards.size)
    }

    @Test
    fun map_finished_localLoser_showsFinishedSummary() {
        val uiState = GameUiStateMapper.map(
            MockGameStates.finished().copy(
                loserId = MockGameStates.LOCAL_PLAYER_ID,
                loserName = "Вы",
                revealLoserCards = emptyList(),
            ),
        )

        assertEquals("Вы — дурак", uiState.finishedSummary)
        assertFalse(uiState.canRevealLoserCards)
        assertTrue(uiState.isLocalPlayerLoser)
    }

    @Test
    fun resolvePrimaryAction_bitoBeforePass() {
        val dto = MockGameStates.inProgress().copy(
            canReady = false,
            canTake = false,
            canBito = true,
            canPass = true,
        )
        assertEquals(HandPrimaryAction.BITO, GameUiStateMapper.resolvePrimaryAction(dto))
    }

    @Test
    fun resolvePrimaryAction_helperPassWhenCannotBito() {
        val dto = MockGameStates.inProgress().copy(
            canReady = false,
            canTake = false,
            canBito = false,
            canPass = true,
        )
        assertEquals(HandPrimaryAction.PASS, GameUiStateMapper.resolvePrimaryAction(dto))
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
    fun resolvePrimaryAction_lobbyAfterReady_showsWaitingReady() {
        val dto = MockGameStates.lobbyWaiting().copy(canReady = false)
        assertEquals(HandPrimaryAction.WAITING_READY, GameUiStateMapper.resolvePrimaryAction(dto))
    }

    @Test
    fun formatReadyTimer_formatsMinutesAndSeconds() {
        assertEquals("1:00", formatReadyTimer(60))
        assertEquals("0:45", formatReadyTimer(45))
        assertEquals("0:05", formatReadyTimer(5))
        assertEquals("0:00", formatReadyTimer(0))
    }
}
