package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.PlayerStatusDto
import com.example.foolcardgame.data.client.DebugScenario
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.data.client.MockGameStates
import com.example.foolcardgame.data.client.toMockState
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GamePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun lobby_startsReadyTimerAt60() = runTest {
        val client = LobbyTestClient()
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()

            assertEquals(GamePhase.LOBBY_WAITING, viewModel.uiState.value.phase)
            assertEquals(HandPrimaryAction.READY, viewModel.uiState.value.actions.primary)
            assertEquals(GameViewModel.READY_TIMEOUT_SECONDS, viewModel.uiState.value.readySecondsLeft)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun onReadyClick_clearsReadySecondsLeft() = runTest {
        val client = LobbyTestClient()
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()

            viewModel.onReadyClick()
            runCurrent()

            assertNull(viewModel.uiState.value.readySecondsLeft)
            assertEquals(HandPrimaryAction.NONE, viewModel.uiState.value.actions.primary)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun readyTimer_expires_showsLobbyTimeoutDialog() = runTest {
        val client = LobbyTestClient()
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()

            advanceTimeBy(GameViewModel.READY_TIMEOUT_SECONDS * 1_000L)
            runCurrent()

            assertNull(viewModel.uiState.value.readySecondsLeft)
            assertTrue(viewModel.uiState.value.showLobbyTimeoutDialog)
        } finally {
            viewModel.disposeForTest()
        }
    }
}

class GameDebugViewModelTest {

    @Test
    fun setScenario_updatesMappedUiStatePhase() {
        val lobbyState = GameUiStateMapper.map(MockGameStates.lobbyWaiting())
        assertEquals(GamePhase.LOBBY_WAITING, lobbyState.phase)
        assertEquals(HandPrimaryAction.READY, lobbyState.actions.primary)

        val gameState = GameUiStateMapper.map(DebugScenario.IN_PROGRESS.toMockState())
        assertEquals(GamePhase.IN_PROGRESS, gameState.phase)
        assertEquals(HandPrimaryAction.BITO, gameState.actions.primary)
        assertEquals(12, gameState.hand.size)
    }

    @Test
    fun setScenario_disconnected_showsDisconnectedPlayer() {
        val uiState = GameUiStateMapper.map(DebugScenario.LOBBY_DISCONNECTED.toMockState())
        assertTrue(uiState.hasDisconnectedOpponent)
        val disconnected = uiState.opponents.first { it.displayName == "Бот 2" }
        assertFalse(disconnected.isConnected)
    }
}

/** GameClient without infinite poll ticks — safe for runTest. */
private class LobbyTestClient : GameClient {
    private val state = MutableStateFlow(MockGameStates.lobbyWaiting())

    override suspend fun getState(sessionId: GameSessionId): GameStateDto = state.value

    override fun observeState(sessionId: GameSessionId, pollIntervalMs: Long): Flow<GameStateDto> =
        state.asStateFlow()

    override suspend fun playCard(
        sessionId: GameSessionId,
        card: Card,
        targetPairId: Int?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> =
        Result.success(Unit)

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> = Result.success(Unit)

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> = Result.success(Unit)

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> {
        state.update { current ->
            current.copy(
                canReady = false,
                players = current.players.map { player ->
                    if (player.id == MockGameStates.LOCAL_PLAYER_ID) {
                        player.copy(isReady = true, status = PlayerStatusDto.PLAYING)
                    } else {
                        player
                    }
                },
            )
        }
        return Result.success(Unit)
    }

    override suspend fun leaveSession(sessionId: GameSessionId) {
        state.update { it.copy(canReady = false) }
    }
}
