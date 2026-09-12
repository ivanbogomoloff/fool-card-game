package com.example.foolcardgame.data.network

import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.client.OnlineSessionIds
import com.example.foolcardgame.data.client.QueueUpdate
import foolcard.v1.GameSessionGrpcKt
import foolcard.v1.Session
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Bidi [GameSession.Session]: исходящие ClientMessage + входящие снимки.
 * При UNAVAILABLE / смене сети — reconnect с resnapshot (Subscribe / QuickMatch).
 */
class GrpcSessionController(
    private val channel: ManagedChannel,
    private val tokenProvider: suspend () -> String,
    private val scope: CoroutineScope,
) {
    private val outgoing = MutableSharedFlow<Session.ClientMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _queueUpdates = MutableSharedFlow<QueueUpdate>(extraBufferCapacity = 32)
    private val _roomStates = MutableSharedFlow<RoomStateDto>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _gameStates = MutableSharedFlow<GameStateDto>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val mutex = Mutex()
    private var streamJob: Job? = null
    private val intentionalClose = AtomicBoolean(false)
    private var backoffMs = INITIAL_BACKOFF_MS
    private val lastReconnectAtMs = AtomicLong(0)

    @Volatile
    private var resumeAction: ResumeAction? = null

    @Volatile
    var activeGameId: String? = null
        private set

    @Volatile
    var activePlayerId: String? = null
        private set

    val queueUpdates: Flow<QueueUpdate> = _queueUpdates.asSharedFlow()
    val errors: Flow<String> = _errors.asSharedFlow()

    fun observeRoom(sessionId: String): Flow<RoomStateDto> {
        val gameId = OnlineSessionIds.unwrap(sessionId)
        return _roomStates.asSharedFlow().filter { OnlineSessionIds.unwrap(it.sessionId) == gameId }
    }

    fun observeGame(sessionId: String): Flow<GameStateDto> {
        val gameId = OnlineSessionIds.unwrap(sessionId)
        return _gameStates.asSharedFlow()
            .filter { OnlineSessionIds.unwrap(it.sessionId) == gameId }
            .onStart {
                _gameStates.replayCache.lastOrNull()
                    ?.takeIf { OnlineSessionIds.unwrap(it.sessionId) == gameId }
                    ?.let { emit(it) }
            }
    }

    suspend fun startQuickMatch(username: String, avatarId: Int) {
        resumeAction = ResumeAction.QuickMatch(username, avatarId)
        intentionalClose.set(false)
        val alreadyActive = streamJob?.isActive == true
        ensureStream()
        // На уже живом stream — шлём сразу; на новом — resume уйдёт из requestFlow при connect.
        if (alreadyActive) {
            outgoing.emit(buildQuickMatchMessage(username, avatarId))
        }
    }

    suspend fun subscribe(gameId: String, playerId: String) {
        activeGameId = gameId
        activePlayerId = playerId
        resumeAction = ResumeAction.Subscribe(gameId, playerId)
        intentionalClose.set(false)
        val alreadyActive = streamJob?.isActive == true
        ensureStream()
        if (alreadyActive) {
            outgoing.emit(buildSubscribeMessage(gameId, playerId))
        }
    }

    suspend fun send(message: Session.ClientMessage) {
        ensureStream()
        outgoing.emit(message)
    }

    suspend fun leaveAndClose() {
        intentionalClose.set(true)
        resumeAction = null
        runCatching {
            outgoing.emit(
                Session.ClientMessage.newBuilder()
                    .setLeave(Session.Leave.getDefaultInstance())
                    .build(),
            )
        }
        delay(150)
        stopStream()
        activeGameId = null
        activePlayerId = null
    }

    /** Форсированный reconnect (смена сети), с debounce. */
    fun forceReconnect() {
        if (intentionalClose.get() || resumeAction == null) return
        val now = System.currentTimeMillis()
        val prev = lastReconnectAtMs.get()
        if (now - prev < RECONNECT_DEBOUNCE_MS) return
        if (!lastReconnectAtMs.compareAndSet(prev, now)) return
        scope.launch {
            stopStream()
            delay(200)
            // Resume уйдёт первым сообщением нового requestFlow — без emit в SharedFlow до подписки.
            ensureStream()
        }
    }

    fun shutdown() {
        intentionalClose.set(true)
        resumeAction = null
        stopStream()
    }

    private suspend fun ensureStream() = mutex.withLock {
        if (streamJob?.isActive == true) return
        intentionalClose.set(false)
        streamJob = scope.launch { runStreamLoop() }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    private suspend fun runStreamLoop() {
        while (scope.isActive && !intentionalClose.get()) {
            try {
                val token = tokenProvider()
                val headers = AuthMetadata.bearerHeaders(token)
                val stub = GameSessionGrpcKt.GameSessionCoroutineStub(channel)
                // Resume в ЭТОТ stream до collect outgoing — иначе SharedFlow(replay=0) теряет emit.
                stub.session(requestFlow(), headers).collect { message ->
                    backoffMs = INITIAL_BACKOFF_MS
                    handleServerMessage(message)
                }
                // Нормальное закрытие сервером
                if (intentionalClose.get()) return
            } catch (e: Throwable) {
                if (intentionalClose.get()) return
                if (e is StatusException || e is StatusRuntimeException) {
                    val code = when (e) {
                        is StatusException -> e.status.code
                        is StatusRuntimeException -> e.status.code
                        else -> Status.Code.UNKNOWN
                    }
                    // stopStream / forceReconnect отменяют job — не считаем это ошибкой UI.
                    if (code == Status.Code.CANCELLED) return
                    _errors.emit(GrpcErrorMapper.toMessage(e))
                    if (code == Status.Code.UNAUTHENTICATED ||
                        code == Status.Code.PERMISSION_DENIED
                    ) {
                        return
                    }
                } else if (e is kotlinx.coroutines.CancellationException) {
                    return
                } else {
                    _errors.emit(GrpcErrorMapper.toMessage(e))
                }
            }
            if (intentionalClose.get() || resumeAction == null) return
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            // Следующая итерация снова откроет session(requestFlow) с emitResume в начале.
        }
    }

    /** Исходящий поток одного Session: сначала resume, затем live outgoing. */
    private fun requestFlow(): Flow<Session.ClientMessage> = flow {
        buildResumeMessage()?.let { emit(it) }
        outgoing.collect { emit(it) }
    }

    private fun buildResumeMessage(): Session.ClientMessage? =
        when (val action = resumeAction) {
            is ResumeAction.QuickMatch -> buildQuickMatchMessage(action.username, action.avatarId)
            is ResumeAction.Subscribe -> buildSubscribeMessage(action.gameId, action.playerId)
            null -> null
        }

    private fun buildQuickMatchMessage(username: String, avatarId: Int): Session.ClientMessage =
        Session.ClientMessage.newBuilder()
            .setQuickMatch(
                Session.QuickMatch.newBuilder()
                    .setUsername(username)
                    .setAvatarId(avatarId)
                    .build(),
            )
            .build()

    private fun buildSubscribeMessage(gameId: String, playerId: String): Session.ClientMessage =
        Session.ClientMessage.newBuilder()
            .setSubscribe(
                Session.Subscribe.newBuilder()
                    .setGameId(gameId)
                    .setPlayerId(playerId)
                    .build(),
            )
            .build()

    private suspend fun handleServerMessage(message: Session.ServerMessage) {
        when (message.payloadCase) {
            Session.ServerMessage.PayloadCase.QUEUE_STATE -> {
                val q = message.queueState
                activePlayerId = q.playerId
                _queueUpdates.emit(
                    QueueUpdate.Waiting(
                        playerId = q.playerId,
                        waitingCount = q.waitingCount,
                        phase = ProtoMappers.queuePhaseName(q.phase),
                    ),
                )
            }
            Session.ServerMessage.PayloadCase.MATCH_STARTED -> {
                val gameId = message.matchStarted.gameId
                activeGameId = gameId
                val playerId = activePlayerId.orEmpty()
                resumeAction = if (playerId.isNotBlank()) {
                    ResumeAction.Subscribe(gameId, playerId)
                } else {
                    resumeAction
                }
                _queueUpdates.emit(QueueUpdate.Matched(OnlineSessionIds.wrap(gameId)))
            }
            Session.ServerMessage.PayloadCase.ROOM_STATE -> {
                val room = ProtoMappers.toRoomStateDto(message.roomState)
                activeGameId = OnlineSessionIds.unwrap(room.sessionId)
                _roomStates.emit(room)
            }
            Session.ServerMessage.PayloadCase.GAME_STATE -> {
                val state = ProtoMappers.toGameStateDto(message.gameState)
                activeGameId = OnlineSessionIds.unwrap(state.sessionId)
                activePlayerId = state.localPlayerId
                resumeAction = ResumeAction.Subscribe(
                    OnlineSessionIds.unwrap(state.sessionId),
                    state.localPlayerId,
                )
                _gameStates.emit(state)
            }
            Session.ServerMessage.PayloadCase.ERROR -> {
                _errors.emit(message.error.message.ifBlank { message.error.code })
            }
            Session.ServerMessage.PayloadCase.KICKED -> {
                _errors.emit(message.kicked.reason.ifBlank { "Вас исключили из комнаты" })
                intentionalClose.set(true)
                resumeAction = null
            }
            Session.ServerMessage.PayloadCase.LEFT_ACK -> {
                intentionalClose.set(true)
                resumeAction = null
            }
            Session.ServerMessage.PayloadCase.PONG,
            Session.ServerMessage.PayloadCase.PAYLOAD_NOT_SET,
            null,
            -> Unit
        }
    }

    private sealed interface ResumeAction {
        data class QuickMatch(val username: String, val avatarId: Int) : ResumeAction
        data class Subscribe(val gameId: String, val playerId: String) : ResumeAction
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 15_000L
        private const val RECONNECT_DEBOUNCE_MS = 2_500L
    }
}
