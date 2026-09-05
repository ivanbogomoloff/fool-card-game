package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.GamePhaseDto
import com.example.foolcardgame.data.api.dto.GameActionEventDto
import com.example.foolcardgame.data.api.dto.GameActionKindDto
import com.example.foolcardgame.data.api.dto.RoundEventDto
import com.example.foolcardgame.data.api.dto.RoundEventKindDto
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.PlayerStateDto
import com.example.foolcardgame.data.api.dto.PlayerStatusDto
import com.example.foolcardgame.data.api.dto.TablePairDto
import com.example.foolcardgame.data.api.dto.toDto
import com.example.foolcardgame.domain.engine.GameEngine
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GameActionKind
import com.example.foolcardgame.domain.model.RoundEventKind
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.permissionsFor
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-process [GameClient] backed by [GameEngine] — local “REST API” without HTTP.
 * Stages bot connect/ready and delays bot moves for a more human feel.
 */
class LocalGameClient(
    private val engine: GameEngine = GameEngine(),
    private val defaultHumanId: String = GameConfig.DEFAULT_HUMAN_ID,
    private val botConnectDelayRange: LongRange =
        GameConfig.BOT_CONNECT_MIN_MS..GameConfig.BOT_CONNECT_MAX_MS,
    private val botReadyDelayRange: LongRange =
        GameConfig.BOT_READY_MIN_MS..GameConfig.BOT_READY_MAX_MS,
    private val random: Random = Random.Default,
    private val schedulerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GameClient {

    private val mutex = Mutex()
    private val updates = MutableSharedFlow<GameStateDto>(extraBufferCapacity = 64)
    private var activeSessionId: GameSessionId? = null
    private var humanId: String = defaultHumanId
    private var sessionScope: CoroutineScope? = null
    private var schedulerJob: Job? = null
    private var activeBotThinkDelayRange: LongRange =
        GameConfig.DEFAULT_BOT_THINK_MIN_MS..GameConfig.DEFAULT_BOT_THINK_MAX_MS

    override suspend fun createSession(config: GameConfig): GameSessionId = mutex.withLock {
        stopSchedulerLocked()
        activeBotThinkDelayRange = config.botThinkMinMs..config.botThinkMaxMs
        val id = engine.createSession(config)
        humanId = config.humanId
        activeSessionId = id
        sessionScope = CoroutineScope(SupervisorJob() + schedulerDispatcher)
        startSchedulerLocked(id)
        updates.tryEmit(engine.getState(id).toDto(humanId))
        id
    }

    override suspend fun getState(sessionId: GameSessionId): GameStateDto = mutex.withLock {
        engine.getState(sessionId).toDto(humanId)
    }

    override fun observeState(
        sessionId: GameSessionId,
        pollIntervalMs: Long,
    ): Flow<GameStateDto> {
        val ticks = flow {
            while (true) {
                delay(pollIntervalMs)
                val dto = mutex.withLock {
                    if (activeSessionId != sessionId) return@flow
                    engine.onTick(sessionId).toDto(humanId)
                }
                emit(dto)
            }
        }
        val immediate = flow {
            emit(mutex.withLock { engine.getState(sessionId).toDto(humanId) })
        }
        return merge(immediate, updates, ticks)
    }

    override suspend fun playCard(
        sessionId: GameSessionId,
        card: Card,
        targetPairId: Int?,
    ): Result<Unit> = action(sessionId) {
        engine.playCard(sessionId, humanId, card, targetPairId)
    }

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> =
        action(sessionId) {
            engine.addCard(sessionId, humanId, card)
        }

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> = action(sessionId) {
        engine.pass(sessionId, humanId)
    }

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> = action(sessionId) {
        engine.bito(sessionId, humanId)
    }

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> = action(sessionId) {
        engine.ready(sessionId, humanId)
    }

    override suspend fun skipTurn(sessionId: GameSessionId): Result<Unit> = action(sessionId) {
        engine.skipTurn(sessionId, humanId)
    }

    override suspend fun leaveSession(sessionId: GameSessionId) {
        mutex.withLock {
            engine.leave(sessionId, humanId)
            if (activeSessionId == sessionId) {
                activeSessionId = null
                stopSchedulerLocked()
            }
        }
    }

    private fun startSchedulerLocked(sessionId: GameSessionId) {
        val scope = sessionScope ?: return
        schedulerJob?.cancel()
        schedulerJob = scope.launch {
            runLobbyStaging(sessionId)
            runBotThinkLoop(sessionId)
        }
    }

    private fun stopSchedulerLocked() {
        schedulerJob?.cancel()
        schedulerJob = null
        sessionScope?.cancel()
        sessionScope = null
    }

    private suspend fun runLobbyStaging(sessionId: GameSessionId) {
        while (coroutineActive(sessionId)) {
            val state = mutex.withLock {
                if (activeSessionId != sessionId) return
                engine.getState(sessionId)
            }
            if (state.phase != GamePhase.LOBBY_WAITING) return

            val bot = state.players.firstOrNull { it.isBot && !it.isConnected }
                ?: state.players.firstOrNull { it.isBot && it.isConnected && !it.isReady }
            if (bot == null) {
                delay(200)
                continue
            }

            if (!bot.isConnected) {
                delay(randomIn(botConnectDelayRange))
                if (!coroutineActive(sessionId)) return
                mutex.withLock {
                    if (activeSessionId != sessionId) return
                    engine.setConnected(sessionId, bot.id, connected = true)
                        .onSuccess { updates.tryEmit(it.toDto(humanId)) }
                }
            } else if (!bot.isReady) {
                delay(randomIn(botReadyDelayRange))
                if (!coroutineActive(sessionId)) return
                mutex.withLock {
                    if (activeSessionId != sessionId) return
                    engine.ready(sessionId, bot.id)
                        .onSuccess { updates.tryEmit(it.toDto(humanId)) }
                }
            }
        }
    }

    private suspend fun runBotThinkLoop(sessionId: GameSessionId) {
        var lastHandledTurnKey: String? = null
        while (coroutineActive(sessionId)) {
            val state = mutex.withLock {
                if (activeSessionId != sessionId) return
                engine.getState(sessionId)
            }
            when (state.phase) {
                GamePhase.LOBBY_WAITING -> {
                    delay(200)
                    continue
                }
                GamePhase.FINISHED -> return
                GamePhase.IN_PROGRESS -> Unit
            }

            val currentId = state.currentPlayerId
            val current = currentId?.let { state.player(it) }
            val humanTurnWithParallelBots = current != null &&
                !current.isBot &&
                state.tablePairs.isNotEmpty() &&
                state.players.any { it.isBot && it.id != state.defenderId && it.hand.isNotEmpty() }
            if (current == null || (!current.isBot && !humanTurnWithParallelBots)) {
                lastHandledTurnKey = null
                delay(150)
                continue
            }

            val turnKey = if (current.isBot) {
                "${currentId}_${state.turnStartedAtMs}_${state.tick}"
            } else {
                "parallel_${state.turnStartedAtMs}_${state.tick}_${state.tablePairs.size}"
            }
            if (turnKey == lastHandledTurnKey) {
                delay(150)
                continue
            }

            // Emit current state so UI can show «Ходит» before the delay.
            updates.tryEmit(state.toDto(humanId))
            delay(randomIn(activeBotThinkDelayRange))
            if (!coroutineActive(sessionId)) return

            mutex.withLock {
                if (activeSessionId != sessionId) return
                val latest = engine.getState(sessionId)
                if (latest.phase != GamePhase.IN_PROGRESS) return@withLock
                if (current.isBot && latest.currentPlayerId != currentId) return@withLock
                val beforeTick = latest.tick
                val after = engine.advanceOneBot(sessionId)
                lastHandledTurnKey = turnKey
                if (after.tick != beforeTick) {
                    updates.tryEmit(after.toDto(humanId))
                }
            }
        }
    }

    private fun coroutineActive(sessionId: GameSessionId): Boolean =
        sessionScope?.isActive == true && activeSessionId == sessionId

    private fun randomIn(range: LongRange): Long {
        if (range.first >= range.last) return range.first
        return random.nextLong(range.first, range.last + 1)
    }

    private suspend fun action(
        sessionId: GameSessionId,
        block: () -> Result<GameState>,
    ): Result<Unit> = mutex.withLock {
        val result = block()
        result.onSuccess { state ->
            updates.tryEmit(state.toDto(humanId))
        }
        result.map { }
    }
}

internal fun GameState.toDto(localPlayerId: String): GameStateDto {
    val perms = permissionsFor(localPlayerId)
    val local = player(localPlayerId)
    return GameStateDto(
        sessionId = sessionId,
        phase = when (phase) {
            GamePhase.LOBBY_WAITING -> GamePhaseDto.LOBBY_WAITING
            GamePhase.IN_PROGRESS -> GamePhaseDto.IN_PROGRESS
            GamePhase.FINISHED -> GamePhaseDto.FINISHED
        },
        players = players.map { p ->
            PlayerStateDto(
                id = p.id,
                displayName = p.displayName,
                avatarId = p.avatarId,
                handCount = p.hand.size,
                isReady = p.isReady,
                isConnected = p.isConnected,
                status = when (p.status) {
                    com.example.foolcardgame.domain.model.PlayerStatus.WAITING -> PlayerStatusDto.WAITING
                    com.example.foolcardgame.domain.model.PlayerStatus.PLAYING -> PlayerStatusDto.PLAYING
                    com.example.foolcardgame.domain.model.PlayerStatus.DISCONNECTED -> PlayerStatusDto.DISCONNECTED
                    com.example.foolcardgame.domain.model.PlayerStatus.LEFT -> PlayerStatusDto.LEFT
                },
            )
        },
        localPlayerId = localPlayerId,
        serverTick = tick,
        deckCount = deck.size,
        trump = trumpCard?.toDto(),
        tablePairs = tablePairs.map {
            TablePairDto(
                id = it.id,
                attack = it.attack.toDto(),
                defense = it.defense?.toDto(),
            )
        },
        localHand = local?.hand?.map { it.toDto() }.orEmpty(),
        currentPlayerId = currentPlayerId,
        attackerId = attackerId,
        defenderId = defenderId,
        canBito = perms.canBito,
        canPass = perms.canPass,
        canTake = perms.canTake,
        canReady = perms.canReady,
        winnerName = winnerIds.firstOrNull()?.let { id -> players.find { it.id == id }?.displayName },
        loserName = loserId?.let { id -> players.find { it.id == id }?.displayName },
        loserId = loserId,
        revealLoserCards = if (phase == GamePhase.FINISHED) {
            loserId?.let { id -> player(id)?.hand?.map { it.toDto() } }.orEmpty()
        } else {
            emptyList()
        },
        turnDeadlineAtMs = turnDeadlineAtMs,
        roundEvent = lastRoundEvent?.let { event ->
            RoundEventDto(
                kind = when (event.kind) {
                    RoundEventKind.TOOK -> RoundEventKindDto.TOOK
                    RoundEventKind.BITO -> RoundEventKindDto.BITO
                },
                playerId = event.playerId,
                atTick = event.atTick,
            )
        },
        actionEvent = lastActionEvent?.let { event ->
            GameActionEventDto(
                kind = when (event.kind) {
                    GameActionKind.ATTACK -> GameActionKindDto.ATTACK
                    GameActionKind.DEFEND -> GameActionKindDto.DEFEND
                    GameActionKind.THROW_IN -> GameActionKindDto.THROW_IN
                    GameActionKind.PASS -> GameActionKindDto.PASS
                    GameActionKind.TOOK -> GameActionKindDto.TOOK
                    GameActionKind.BITO -> GameActionKindDto.BITO
                },
                playerId = event.playerId,
                atTick = event.atTick,
            )
        },
    )
}
