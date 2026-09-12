package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.CreateGameResult
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RoomPlayerDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.local.AuthSessionStore
import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Общее in-memory хранилище комнат для нескольких [FakeOnlineGameClient]. */
class FakeOnlineBackend {
    val mutex = Mutex()
    val rooms = mutableMapOf<String, FakeOnlineRoom>()
    val codeToSession = mutableMapOf<String, String>()
    val roomFlows = mutableMapOf<String, MutableStateFlow<RoomStateDto>>()

    fun publishRoom(sessionId: String) {
        val dto = rooms[sessionId]?.toDto() ?: return
        roomFlows.getOrPut(sessionId) { MutableStateFlow(dto) }.value = dto
    }
}

data class FakeOnlineRoom(
    val sessionId: String,
    val accessCode: String,
    val hostId: String,
    var started: Boolean,
    val players: MutableList<RoomPlayerDto>,
) {
    fun toDto() = RoomStateDto(sessionId, accessCode, hostId, started, players.toList())
}

/**
 * In-memory online клиент для unit-тестов (имитирует stream без gRPC).
 */
class FakeOnlineGameClient(
    private val authSession: AuthSessionStore = InMemoryAuthSessionStore(),
    private val backend: FakeOnlineBackend = FakeOnlineBackend(),
    private val quickMatchSuccessAfterUpdates: Int = 2,
) : GameClient {

    private var activeSessionId: GameSessionId? = null
    private val _sessionErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Для тестов: эмитить ошибку Session (BUSY и т.п.). */
    suspend fun emitSessionError(message: String) {
        _sessionErrors.emit(message)
    }

    override fun isAuthorized(): Boolean = authSession.isAuthorized()

    override suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        require(username.isNotBlank()) { "Укажите имя пользователя" }
        authSession.saveToken("fake-${UUID.randomUUID()}")
    }

    override fun observeSessionErrors(): Flow<String> = _sessionErrors.asSharedFlow()

    override fun observeQuickMatch(displayName: String, avatarId: Int): Flow<QueueUpdate> = flow {
        requireToken()
        val playerId = "p-${UUID.randomUUID().toString().take(8)}"
        repeat(quickMatchSuccessAfterUpdates - 1) { index ->
            emit(
                QueueUpdate.Waiting(
                    playerId = playerId,
                    waitingCount = index + 1,
                    phase = "SEARCHING",
                ),
            )
            delay(GameClient.ROOM_POLL_INTERVAL_MS)
        }
        val sessionId = nextOnlineSessionId()
        backend.mutex.withLock {
            backend.rooms[sessionId] = FakeOnlineRoom(
                sessionId = sessionId,
                accessCode = randomCode(),
                hostId = playerId,
                started = true,
                players = mutableListOf(
                    RoomPlayerDto(playerId, displayName, avatarId, isHost = true),
                ),
            )
            activeSessionId = sessionId
            backend.publishRoom(sessionId)
        }
        emit(QueueUpdate.Matched(sessionId))
    }

    override suspend fun cancelQuickMatch() = Unit

    override suspend fun quickMatch(displayName: String, avatarId: Int): Result<GameSessionId?> =
        runCatching {
            var result: GameSessionId? = null
            observeQuickMatch(displayName, avatarId).collect { update ->
                if (update is QueueUpdate.Matched) {
                    result = update.sessionId
                }
            }
            result
        }

    override suspend fun createPrivateGame(
        displayName: String,
        avatarId: Int,
    ): Result<CreateGameResult> = runCatching {
        requireToken()
        backend.mutex.withLock {
            val sessionId = nextOnlineSessionId()
            val hostId = "p-${UUID.randomUUID().toString().take(8)}"
            val code = randomCode()
            backend.rooms[sessionId] = FakeOnlineRoom(
                sessionId = sessionId,
                accessCode = code,
                hostId = hostId,
                started = false,
                players = mutableListOf(
                    RoomPlayerDto(hostId, displayName, avatarId, isHost = true),
                ),
            )
            backend.codeToSession[code] = sessionId
            activeSessionId = sessionId
            backend.publishRoom(sessionId)
            CreateGameResult(sessionId, code, hostId, hostId)
        }
    }

    override suspend fun joinByCode(
        code: String,
        displayName: String,
        avatarId: Int,
    ): Result<Pair<GameSessionId, String>> = runCatching {
        requireToken()
        backend.mutex.withLock {
            val sessionId = backend.codeToSession[code.trim().uppercase()]
                ?: error("Комната не найдена")
            val room = backend.rooms[sessionId] ?: error("Комната не найдена")
            val playerId = "p-${UUID.randomUUID().toString().take(8)}"
            room.players.add(RoomPlayerDto(playerId, displayName, avatarId, isHost = false))
            activeSessionId = sessionId
            backend.publishRoom(sessionId)
            sessionId to playerId
        }
    }

    override fun observeRoom(sessionId: GameSessionId): Flow<RoomStateDto> {
        val state = backend.roomFlows.getOrPut(sessionId) {
            MutableStateFlow(
                backend.rooms[sessionId]?.toDto()
                    ?: RoomStateDto(sessionId, "", "", false, emptyList()),
            )
        }
        return state
    }

    override suspend fun getRoom(sessionId: GameSessionId): Result<RoomStateDto> = runCatching {
        backend.mutex.withLock {
            backend.rooms[sessionId]?.toDto() ?: error("Комната не найдена")
        }
    }

    override suspend fun kickPlayer(sessionId: GameSessionId, playerId: String): Result<Unit> =
        runCatching {
            backend.mutex.withLock {
                val room = backend.rooms[sessionId] ?: error("Комната не найдена")
                room.players.removeAll { it.id == playerId }
                backend.publishRoom(sessionId)
            }
        }

    override suspend fun startGame(sessionId: GameSessionId): Result<Unit> = runCatching {
        backend.mutex.withLock {
            val room = backend.rooms[sessionId] ?: error("Комната не найдена")
            room.started = true
            backend.publishRoom(sessionId)
        }
    }

    override suspend fun createSession(config: GameConfig): GameSessionId =
        error("FakeOnline: createSession не поддерживается")

    override suspend fun getState(sessionId: GameSessionId): GameStateDto =
        MockGameStates.lobbyWaiting().copy(sessionId = sessionId)

    override fun observeState(
        sessionId: GameSessionId,
        pollIntervalMs: Long,
    ): Flow<GameStateDto> = flow {
        while (true) {
            emit(getState(sessionId))
            delay(pollIntervalMs)
        }
    }

    override suspend fun playCard(
        sessionId: GameSessionId,
        card: Card,
        targetPairId: Int?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> =
        Result.success(Unit)

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> = Result.success(Unit)

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> = Result.success(Unit)

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> = Result.success(Unit)

    override suspend fun skipTurn(sessionId: GameSessionId): Result<Unit> =
        Result.failure(UnsupportedOperationException("skipTurn только offline"))

    override suspend fun leaveSession(sessionId: GameSessionId) {
        if (activeSessionId == sessionId) activeSessionId = null
    }

    private suspend fun requireToken(): String =
        authSession.getToken()?.takeIf { it.isNotBlank() } ?: error("Не выполнен вход")

    private fun nextOnlineSessionId(): String =
        OnlineSessionIds.wrap(UUID.randomUUID().toString())

    private fun randomCode(): String =
        (1..4).map { ('A'..'Z').random() }.joinToString("")
}
