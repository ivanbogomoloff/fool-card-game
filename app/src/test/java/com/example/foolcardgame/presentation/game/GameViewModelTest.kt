package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.GameActionEventDto
import com.example.foolcardgame.data.api.dto.GameActionKindDto
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RoundEventDto
import com.example.foolcardgame.data.api.dto.RoundEventKindDto
import com.example.foolcardgame.data.api.dto.TablePairDto
import com.example.foolcardgame.data.api.dto.PlayerStatusDto
import com.example.foolcardgame.data.client.DebugScenario
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.data.client.MockGameStates
import com.example.foolcardgame.data.client.toMockState
import com.example.foolcardgame.domain.audio.GameSoundKind
import com.example.foolcardgame.domain.audio.RecordingGameSoundEffects
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.RoundEventKind
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
import org.junit.Assert.assertNotNull
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

    @Test
    fun turnTimer_expires_callsSkipTurn() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            assertEquals(GameViewModel.TURN_TIMEOUT_SECONDS, viewModel.uiState.value.turnSecondsLeft)

            advanceTimeBy(GameViewModel.TURN_TIMEOUT_SECONDS * 1_000L)
            runCurrent()

            assertEquals(1, client.skipTurnCalls)
            assertNull(viewModel.uiState.value.turnSecondsLeft)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun opponentRoundEvent_showsBadgeAndFlyAnimation() = runTest {
        val table = MockGameStates.inProgress().tablePairs
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitRoundEvent(
                RoundEventDto(
                    kind = RoundEventKindDto.TOOK,
                    playerId = "bot-1",
                    atTick = 99L,
                ),
                clearedTable = true,
                previousTable = table,
            )
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.TOOK,
                    playerId = "bot-1",
                    atTick = 99L,
                ),
            )
            runCurrent()

            val action = viewModel.uiState.value.opponentAction
            assertNotNull(action)
            assertEquals("bot-1", action?.opponentId)
            assertEquals("Беру", action?.message)
            assertNotNull(viewModel.uiState.value.tableFlyAnimation)
            assertEquals(RoundEventKind.TOOK, viewModel.uiState.value.tableFlyAnimation?.kind)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun opponentBitoEvent_showsBadgeEvenWithoutTableSnapshot() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.clearTable()
            runCurrent()
            client.emitRoundEvent(
                RoundEventDto(
                    kind = RoundEventKindDto.BITO,
                    playerId = "bot-1",
                    atTick = 101L,
                ),
                clearedTable = true,
                previousTable = emptyList(),
            )
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.BITO,
                    playerId = "bot-1",
                    atTick = 101L,
                ),
            )
            runCurrent()

            val action = viewModel.uiState.value.opponentAction
            assertNotNull(action)
            assertEquals("Бито", action?.message)
            assertNull(viewModel.uiState.value.tableFlyAnimation)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun opponentPassActionEvent_showsBadge() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.PASS,
                    playerId = "bot-1",
                    atTick = 103L,
                ),
            )
            runCurrent()

            val action = viewModel.uiState.value.opponentAction
            assertNotNull(action)
            assertEquals("bot-1", action?.opponentId)
            assertEquals("Бито", action?.message)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun opponentThrowInActionEvent_setsPulse() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.THROW_IN,
                    playerId = "bot-1",
                    atTick = 104L,
                ),
            )
            runCurrent()

            val pulse = viewModel.uiState.value.opponentPulse
            assertNotNull(pulse)
            assertEquals("bot-1", pulse?.opponentId)
            assertEquals(104L, pulse?.atTick)
            assertNull(viewModel.uiState.value.opponentAction)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun localThrowInActionEvent_doesNotSetPulse() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.THROW_IN,
                    playerId = MockGameStates.LOCAL_PLAYER_ID,
                    atTick = 105L,
                ),
            )
            runCurrent()

            assertNull(viewModel.uiState.value.opponentPulse)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun opponentThrowInPulse_clearsAfterDelay() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.THROW_IN,
                    playerId = "bot-1",
                    atTick = 106L,
                ),
            )
            runCurrent()
            assertNotNull(viewModel.uiState.value.opponentPulse)

            advanceTimeBy(GameViewModel.OPPONENT_PULSE_MS_FOR_TEST)
            runCurrent()
            assertNull(viewModel.uiState.value.opponentPulse)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun opponentBadge_clearsAfterThreeSeconds() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.BITO,
                    playerId = "bot-1",
                    atTick = 102L,
                ),
            )
            runCurrent()
            assertNotNull(viewModel.uiState.value.opponentAction)

            advanceTimeBy(GameViewModel.OPPONENT_TOAST_MS_FOR_TEST)
            runCurrent()

            assertNull(viewModel.uiState.value.opponentAction)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun actionEvent_appendsGameHistory() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.ATTACK,
                    playerId = "bot-1",
                    atTick = 50L,
                ),
            )
            runCurrent()

            assertEquals(1, viewModel.uiState.value.gameHistory.size)
            assertTrue(viewModel.uiState.value.gameHistory.single().text.contains("Бот 1"))
            assertTrue(viewModel.uiState.value.gameHistory.single().text.contains("атакует"))
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun localRoundEvent_doesNotShowOpponentFx() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val viewModel = GameViewModel(client, MockGameStates.DEBUG_SESSION_ID)
        try {
            runCurrent()
            client.emitRoundEvent(
                RoundEventDto(
                    kind = RoundEventKindDto.BITO,
                    playerId = MockGameStates.LOCAL_PLAYER_ID,
                    atTick = 100L,
                ),
                clearedTable = true,
                previousTable = MockGameStates.inProgress().tablePairs,
            )
            runCurrent()

            assertNull(viewModel.uiState.value.opponentAction)
            assertNull(viewModel.uiState.value.tableFlyAnimation)
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun actionEvent_playsCardSoundForOpponentAndLocal() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val soundEffects = RecordingGameSoundEffects()
        val viewModel = GameViewModel(
            client,
            MockGameStates.DEBUG_SESSION_ID,
            soundEffects,
        )
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.DEFEND,
                    playerId = "bot-1",
                    atTick = 60L,
                ),
            )
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.ATTACK,
                    playerId = MockGameStates.LOCAL_PLAYER_ID,
                    atTick = 61L,
                ),
            )
            runCurrent()

            assertEquals(
                listOf(GameSoundKind.CARD_PLAY, GameSoundKind.CARD_PLAY),
                soundEffects.played,
            )
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun actionEvent_playsBitoAndTakeSounds() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val soundEffects = RecordingGameSoundEffects()
        val viewModel = GameViewModel(
            client,
            MockGameStates.DEBUG_SESSION_ID,
            soundEffects,
        )
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.BITO,
                    playerId = "bot-1",
                    atTick = 70L,
                ),
            )
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.TOOK,
                    playerId = "bot-1",
                    atTick = 71L,
                ),
            )
            runCurrent()

            assertEquals(
                listOf(GameSoundKind.BITO, GameSoundKind.TAKE),
                soundEffects.played,
            )
        } finally {
            viewModel.disposeForTest()
        }
    }

    @Test
    fun passActionEvent_doesNotPlaySound() = runTest {
        val client = LobbyTestClient(initial = MockGameStates.inProgress())
        val soundEffects = RecordingGameSoundEffects()
        val viewModel = GameViewModel(
            client,
            MockGameStates.DEBUG_SESSION_ID,
            soundEffects,
        )
        try {
            runCurrent()
            client.emitActionEvent(
                GameActionEventDto(
                    kind = GameActionKindDto.PASS,
                    playerId = "bot-1",
                    atTick = 72L,
                ),
            )
            runCurrent()

            assertEquals(emptyList<GameSoundKind>(), soundEffects.played)
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
private class LobbyTestClient(
    initial: GameStateDto = MockGameStates.lobbyWaiting(),
) : GameClient {
    private val state = MutableStateFlow(initial)
    var skipTurnCalls: Int = 0

    override suspend fun createSession(config: GameConfig): GameSessionId =
        MockGameStates.DEBUG_SESSION_ID

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

    override suspend fun skipTurn(sessionId: GameSessionId): Result<Unit> {
        skipTurnCalls++
        return Result.success(Unit)
    }

    override suspend fun leaveSession(sessionId: GameSessionId) {
        state.update { it.copy(canReady = false) }
    }

    fun clearTable() {
        state.update { it.copy(tablePairs = emptyList()) }
    }

    fun emitRoundEvent(
        event: RoundEventDto,
        clearedTable: Boolean,
        previousTable: List<TablePairDto>,
    ) {
        state.update { current ->
            current.copy(
                tablePairs = if (clearedTable) emptyList() else previousTable,
                roundEvent = event,
                serverTick = event.atTick,
            )
        }
    }

    fun emitActionEvent(event: GameActionEventDto) {
        state.update { current ->
            current.copy(
                actionEvent = event,
                serverTick = event.atTick,
            )
        }
    }
}
