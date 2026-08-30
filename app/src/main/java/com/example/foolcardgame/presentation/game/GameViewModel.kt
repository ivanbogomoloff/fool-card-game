package com.example.foolcardgame.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.api.dto.GamePhaseDto
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RoundEventKindDto
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.domain.audio.GameSoundEffects
import com.example.foolcardgame.domain.audio.NoOpGameSoundEffects
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.RoundEventKind
import com.example.foolcardgame.domain.model.Suit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

open class GameViewModel(
    protected val gameClient: GameClient,
    protected val sessionId: GameSessionId,
    private val soundEffects: GameSoundEffects = NoOpGameSoundEffects,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var readyTimerJob: Job? = null
    private var turnTimerJob: Job? = null
    private var observeJob: Job? = null
    private var lastTurnPlayerId: String? = null
    private var previousDto: GameStateDto? = null
    private var lastHandledEventTick: Long = -1L
    private var lastHandledActionKey: String? = null
    private var opponentToastJob: Job? = null

    init {
        observeJob = viewModelScope.launch {
            gameClient.observeState(sessionId).collect { dto ->
                val mapped = GameUiStateMapper.map(dto)
                var newOpponentAction: OpponentActionUi? = null
                var newFlyAnimation: TableFlyAnimationUi? = null
                var newHistoryEntry: GameHistoryEntryUi? = null

                dto.roundEvent?.let { event ->
                    if (event.playerId != dto.localPlayerId &&
                        event.atTick > lastHandledEventTick
                    ) {
                        lastHandledEventTick = event.atTick
                        val kind = when (event.kind) {
                            RoundEventKindDto.TOOK -> RoundEventKind.TOOK
                            RoundEventKindDto.BITO -> RoundEventKind.BITO
                        }
                        val snapshotPairs = previousDto?.tablePairs
                            ?.let { GameUiStateMapper.mapTablePairs(it) }
                            .orEmpty()
                        if (snapshotPairs.isNotEmpty() && dto.phase != GamePhaseDto.FINISHED) {
                            newFlyAnimation = TableFlyAnimationUi(
                                pairs = snapshotPairs,
                                targetOpponentId = event.playerId,
                                kind = kind,
                                atTick = event.atTick,
                            )
                        }
                    }
                }

                dto.actionEvent?.let { event ->
                    val key = event.historyKey()
                    if (key != lastHandledActionKey) {
                        lastHandledActionKey = key
                        newHistoryEntry = event.toHistoryEntry(
                            players = dto.players,
                            timestampMs = System.currentTimeMillis(),
                        )
                        event.kind.toGameSoundKind()?.let { soundEffects.play(it) }
                        if (event.playerId != dto.localPlayerId) {
                            opponentBadgeMessage(event.kind)?.let { message ->
                                newOpponentAction = OpponentActionUi(
                                    opponentId = event.playerId,
                                    message = message,
                                    atTick = event.atTick,
                                )
                                scheduleOpponentToastClear()
                            }
                        }
                    }
                }
                previousDto = dto

                _uiState.update { current ->
                    mapped.copy(
                        selectedCardId = current.selectedCardId,
                        showLeaveDialog = current.showLeaveDialog,
                        readySecondsLeft = current.readySecondsLeft,
                        turnSecondsLeft = current.turnSecondsLeft,
                        showLobbyTimeoutDialog = current.showLobbyTimeoutDialog,
                        showLoserCards = current.showLoserCards,
                        opponentAction = newOpponentAction ?: current.opponentAction,
                        tableFlyAnimation = newFlyAnimation ?: current.tableFlyAnimation,
                        gameHistory = if (newHistoryEntry != null) {
                            current.gameHistory + newHistoryEntry
                        } else {
                            current.gameHistory
                        },
                    )
                }
                syncReadyTimer(canReady = dto.canReady)
                syncTurnTimer(
                    isLocalTurn = mapped.isLocalPlayerTurn,
                    currentPlayerId = dto.currentPlayerId,
                    turnDeadlineAtMs = dto.turnDeadlineAtMs,
                )
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        cancelReadyTimer(clearSeconds = true)
        cancelTurnTimer(clearSeconds = true)
        kotlinx.coroutines.runBlocking {
            runCatching { gameClient.leaveSession(sessionId) }
        }
        super.onCleared()
    }

    /** Cancels observation/timer so coroutine tests can finish. */
    internal fun disposeForTest() {
        observeJob?.cancel()
        opponentToastJob?.cancel()
        cancelReadyTimer(clearSeconds = true)
        cancelTurnTimer(clearSeconds = true)
    }

    fun onCardSelected(cardId: String) {
        _uiState.update {
            it.copy(selectedCardId = if (it.selectedCardId == cardId) null else cardId)
        }
    }

    fun onAttackDrop(cardId: String) {
        playCardById(cardId, targetPairId = null)
    }

    fun onDefendDrop(cardId: String, pairId: Int) {
        playCardById(cardId, targetPairId = pairId)
    }

    fun onBitoClick() {
        viewModelScope.launch { gameClient.bito(sessionId) }
    }

    fun onPassClick() {
        viewModelScope.launch { gameClient.pass(sessionId) }
    }

    fun onTakeClick() {
        viewModelScope.launch { gameClient.pass(sessionId) }
    }

    fun onReadyClick() {
        cancelReadyTimer(clearSeconds = true)
        viewModelScope.launch { gameClient.ready(sessionId) }
    }

    fun onToggleLoserCardsClick() {
        _uiState.update { it.copy(showLoserCards = !it.showLoserCards) }
    }

    fun onExitClick() {
        onLeaveConfirm()
    }

    fun onBackClick() {
        _uiState.update { it.copy(showLeaveDialog = true) }
    }

    fun onLeaveConfirm() {
        cancelReadyTimer(clearSeconds = true)
        cancelTurnTimer(clearSeconds = true)
        viewModelScope.launch {
            gameClient.leaveSession(sessionId)
            _uiState.update { it.copy(showLeaveDialog = false) }
        }
    }

    fun onLeaveDismiss() {
        _uiState.update { it.copy(showLeaveDialog = false) }
    }

    fun onLobbyTimeoutDismiss() {
        _uiState.update { it.copy(showLobbyTimeoutDialog = false) }
    }

    fun onFlyAnimationFinished() {
        _uiState.update { it.copy(tableFlyAnimation = null) }
    }

    private fun scheduleOpponentToastClear() {
        opponentToastJob?.cancel()
        opponentToastJob = viewModelScope.launch {
            delay(OPPONENT_TOAST_MS)
            _uiState.update { it.copy(opponentAction = null) }
        }
    }

    /** Restarts lobby ready countdown (e.g. when debug scenario switches to lobby). */
    protected fun restartReadyTimerIfNeeded(canReady: Boolean) {
        cancelReadyTimer(clearSeconds = true)
        syncReadyTimer(canReady = canReady)
    }

    private fun syncReadyTimer(canReady: Boolean) {
        if (canReady && !_uiState.value.showLobbyTimeoutDialog) {
            if (readyTimerJob?.isActive != true) {
                startReadyTimer()
            }
        } else if (!canReady) {
            cancelReadyTimer(clearSeconds = true)
        }
    }

    private fun syncTurnTimer(
        isLocalTurn: Boolean,
        currentPlayerId: String?,
        turnDeadlineAtMs: Long?,
    ) {
        if (!isLocalTurn) {
            lastTurnPlayerId = currentPlayerId
            cancelTurnTimer(clearSeconds = true)
            return
        }
        val turnChanged = currentPlayerId != lastTurnPlayerId
        lastTurnPlayerId = currentPlayerId
        if (turnChanged || turnTimerJob?.isActive != true) {
            startTurnTimer(turnDeadlineAtMs)
        }
    }

    private fun startReadyTimer() {
        readyTimerJob?.cancel()
        readyTimerJob = viewModelScope.launch {
            for (secondsLeft in READY_TIMEOUT_SECONDS downTo 1) {
                _uiState.update { it.copy(readySecondsLeft = secondsLeft) }
                delay(1_000)
            }
            gameClient.leaveSession(sessionId)
            _uiState.update {
                it.copy(
                    readySecondsLeft = null,
                    showLobbyTimeoutDialog = true,
                )
            }
        }
    }

    private fun startTurnTimer(turnDeadlineAtMs: Long?) {
        turnTimerJob?.cancel()
        turnTimerJob = viewModelScope.launch {
            val maxTimeoutSeconds = maxOf(TURN_TIMEOUT_SECONDS, THROW_TIMEOUT_SECONDS)
            val totalSeconds = if (turnDeadlineAtMs != null) {
                ((turnDeadlineAtMs - System.currentTimeMillis()) / 1_000L)
                    .toInt()
                    .coerceIn(1, maxTimeoutSeconds)
            } else {
                TURN_TIMEOUT_SECONDS
            }
            for (secondsLeft in totalSeconds downTo 1) {
                _uiState.update { it.copy(turnSecondsLeft = secondsLeft) }
                delay(1_000)
            }
            gameClient.skipTurn(sessionId)
            _uiState.update { it.copy(turnSecondsLeft = null) }
        }
    }

    private fun cancelReadyTimer(clearSeconds: Boolean) {
        readyTimerJob?.cancel()
        readyTimerJob = null
        if (clearSeconds) {
            _uiState.update { it.copy(readySecondsLeft = null) }
        }
    }

    private fun cancelTurnTimer(clearSeconds: Boolean) {
        turnTimerJob?.cancel()
        turnTimerJob = null
        if (clearSeconds) {
            _uiState.update { it.copy(turnSecondsLeft = null) }
        }
    }

    private fun playCardById(cardId: String, targetPairId: Int?) {
        val card = cardId.toCardOrNull() ?: return
        viewModelScope.launch {
            gameClient.playCard(sessionId, card, targetPairId)
            _uiState.update { it.copy(selectedCardId = null) }
        }
    }

    companion object {
        const val READY_TIMEOUT_SECONDS = 60
        const val TURN_TIMEOUT_SECONDS = (GameConfig.TURN_TIMEOUT_MS / 1_000L).toInt()
        const val THROW_TIMEOUT_SECONDS = (GameConfig.THROW_TIMEOUT_MS / 1_000L).toInt()
        private const val OPPONENT_TOAST_MS = 3_000L
        internal const val OPPONENT_TOAST_MS_FOR_TEST = OPPONENT_TOAST_MS
    }
}

internal fun String.toCardOrNull(): Card? {
    val parts = split('_')
    if (parts.size != 2) return null
    val suit = runCatching { Suit.valueOf(parts[0]) }.getOrNull() ?: return null
    val rank = runCatching { Rank.valueOf(parts[1]) }.getOrNull() ?: return null
    return Card(suit = suit, rank = rank)
}
