package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.CreateGameResult
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.local.AccountCredentials
import com.example.foolcardgame.data.local.AccountCredentialsStore
import com.example.foolcardgame.data.local.AuthSessionStore
import com.example.foolcardgame.data.local.InMemoryAccountCredentialsStore
import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import com.example.foolcardgame.data.network.AuthMetadata
import com.example.foolcardgame.data.network.GrpcErrorMapper
import com.example.foolcardgame.data.network.GrpcSessionController
import com.example.foolcardgame.data.network.ProtoMappers
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import foolcard.v1.AuthGrpcKt
import foolcard.v1.MatchmakingGrpcKt
import foolcard.v1.MatchmakingOuterClass
import foolcard.v1.Session
import foolcard.v1.loginRequest
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Online [GameClient] на gRPC (Auth + Matchmaking + bidi Session).
 */
class RemoteGameClient(
    private val channel: ManagedChannel,
    private val authSession: AuthSessionStore = InMemoryAuthSessionStore(),
    private val credentialsStore: AccountCredentialsStore = InMemoryAccountCredentialsStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    sessionController: GrpcSessionController? = null,
) : GameClient {

    private val authStub = AuthGrpcKt.AuthCoroutineStub(channel)
    private val matchStub = MatchmakingGrpcKt.MatchmakingCoroutineStub(channel)
    private val session = sessionController ?: GrpcSessionController(
        channel = channel,
        tokenProvider = { requireToken() },
        scope = scope,
    )

    private val mutex = Mutex()
    private var activeSessionId: GameSessionId? = null
    private var lastRoom: RoomStateDto? = null
    private var lastGameState: GameStateDto? = null

    override fun isAuthorized(): Boolean = authSession.isAuthorized()

    override suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val stored = credentialsStore.load()
        val requestUser = username.ifBlank { stored?.username.orEmpty() }
        // Пароль из Keystore только для того же username; иначе — регистрация без password.
        val requestPassword = when {
            password.isNotBlank() -> password
            stored != null && stored.username == requestUser -> stored.password
            else -> ""
        }
        if (requestUser.isBlank()) {
            error("Укажите имя пользователя")
        }
        val response = authStub.login(
            loginRequest {
                this.username = requestUser
                if (requestPassword.isNotBlank()) {
                    this.password = requestPassword
                }
            },
        )
        authSession.saveToken(response.token)
        val plainPassword = if (response.hasPassword()) {
            response.password
        } else {
            requestPassword
        }
        if (plainPassword.isNotBlank()) {
            credentialsStore.save(
                AccountCredentials(
                    username = response.username,
                    password = plainPassword,
                    accountId = response.accountId,
                ),
            )
        }
    }.mapGrpc()

    override fun observeQuickMatch(displayName: String, avatarId: Int): Flow<QueueUpdate> =
        session.queueUpdates
            .onStart {
                session.startQuickMatch(displayName, avatarId)
            }
            .transformWhile { update ->
                emit(update)
                update !is QueueUpdate.Matched
            }

    override fun observeSessionErrors(): Flow<String> = session.errors

    override suspend fun cancelQuickMatch() {
        session.leaveAndClose()
    }

    override suspend fun quickMatch(displayName: String, avatarId: Int): Result<GameSessionId?> =
        runCatching {
            val matched = withTimeoutOrNull(QUICK_MATCH_WAIT_MS) {
                observeQuickMatch(displayName, avatarId)
                    .filterIsInstance<QueueUpdate.Matched>()
                    .first()
            }
            matched?.sessionId?.also { activeSessionId = it }
        }.mapGrpc()

    override suspend fun createPrivateGame(
        displayName: String,
        avatarId: Int,
    ): Result<CreateGameResult> = runCatching {
        val headers = AuthMetadata.bearerHeaders(requireToken())
        val response = matchStub.createGame(
            MatchmakingOuterClass.PlayerProfile.newBuilder()
                .setUsername(displayName)
                .setAvatarId(avatarId)
                .build(),
            headers,
        )
        val sessionId = OnlineSessionIds.wrap(response.gameId)
        activeSessionId = sessionId
        session.subscribe(response.gameId, response.playerId)
        CreateGameResult(
            sessionId = sessionId,
            accessCode = response.accessCode,
            hostId = response.hostId,
            playerId = response.playerId,
        )
    }.mapGrpc()

    override suspend fun joinByCode(
        code: String,
        displayName: String,
        avatarId: Int,
    ): Result<Pair<GameSessionId, String>> = runCatching {
        val headers = AuthMetadata.bearerHeaders(requireToken())
        val response = matchStub.joinGame(
            MatchmakingOuterClass.JoinGameRequest.newBuilder()
                .setCode(code.trim().uppercase())
                .setUsername(displayName)
                .setAvatarId(avatarId)
                .build(),
            headers,
        )
        val sessionId = OnlineSessionIds.wrap(response.gameId)
        activeSessionId = sessionId
        session.subscribe(response.gameId, response.playerId)
        sessionId to response.playerId
    }.mapGrpc()

    override fun observeRoom(sessionId: GameSessionId): Flow<RoomStateDto> =
        session.observeRoom(sessionId).map { room ->
            lastRoom = room
            room
        }

    override suspend fun getRoom(sessionId: GameSessionId): Result<RoomStateDto> = runCatching {
        lastRoom?.takeIf { it.sessionId == sessionId }
            ?: session.observeRoom(sessionId).first()
    }.mapGrpc()

    override suspend fun kickPlayer(sessionId: GameSessionId, playerId: String): Result<Unit> =
        runCatching {
            assertActiveSession(sessionId)
            session.send(
                Session.ClientMessage.newBuilder()
                    .setKick(Session.Kick.newBuilder().setPlayerId(playerId).build())
                    .build(),
            )
        }.mapGrpc()

    override suspend fun startGame(sessionId: GameSessionId): Result<Unit> = runCatching {
        assertActiveSession(sessionId)
        session.send(
            Session.ClientMessage.newBuilder()
                .setStartGame(Session.StartGame.getDefaultInstance())
                .build(),
        )
        Unit
    }.mapGrpc()

    override suspend fun createSession(config: GameConfig): GameSessionId = mutex.withLock {
        error("Online createSession не используется — createPrivate / quickMatch")
    }

    override suspend fun getState(sessionId: GameSessionId): GameStateDto {
        lastGameState?.takeIf { it.sessionId == sessionId }?.let { return it }
        return session.observeGame(sessionId).first()
    }

    override fun observeState(
        sessionId: GameSessionId,
        pollIntervalMs: Long,
    ): Flow<GameStateDto> =
        session.observeGame(sessionId).map { state ->
            lastGameState = state
            activeSessionId = state.sessionId
            state
        }

    override suspend fun playCard(
        sessionId: GameSessionId,
        card: Card,
        targetPairId: Int?,
    ): Result<Unit> = runCatching {
        assertActiveSession(sessionId)
        val play = Session.PlayCard.newBuilder().setCard(ProtoMappers.toProtoCard(card))
        if (targetPairId != null) {
            play.setTargetPairId(targetPairId)
        }
        session.send(
            Session.ClientMessage.newBuilder().setPlayCard(play.build()).build(),
        )
    }.mapGrpc()

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> =
        runCatching {
            assertActiveSession(sessionId)
            session.send(
                Session.ClientMessage.newBuilder()
                    .setAddCard(
                        Session.AddCard.newBuilder()
                            .setCard(ProtoMappers.toProtoCard(card))
                            .build(),
                    )
                    .build(),
            )
        }.mapGrpc()

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> = runCatching {
        assertActiveSession(sessionId)
        session.send(
            Session.ClientMessage.newBuilder()
                .setPass(Session.Pass.getDefaultInstance())
                .build(),
        )
    }.mapGrpc()

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> = runCatching {
        assertActiveSession(sessionId)
        session.send(
            Session.ClientMessage.newBuilder()
                .setBito(Session.Bito.getDefaultInstance())
                .build(),
        )
    }.mapGrpc()

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> = runCatching {
        assertActiveSession(sessionId)
        session.send(
            Session.ClientMessage.newBuilder()
                .setReady(Session.Ready.getDefaultInstance())
                .build(),
        )
    }.mapGrpc()

    override suspend fun skipTurn(sessionId: GameSessionId): Result<Unit> =
        Result.failure(UnsupportedOperationException("skipTurn только offline"))

    override suspend fun leaveSession(sessionId: GameSessionId) {
        mutex.withLock {
            if (activeSessionId == sessionId ||
                OnlineSessionIds.unwrap(activeSessionId.orEmpty()) ==
                OnlineSessionIds.unwrap(sessionId)
            ) {
                activeSessionId = null
            }
        }
        session.leaveAndClose()
        lastRoom = null
        lastGameState = null
    }

    fun forceReconnect() {
        session.forceReconnect()
    }

    fun shutdown() {
        session.shutdown()
    }

    private fun assertActiveSession(sessionId: GameSessionId) {
        val current = activeSessionId
        if (current != null &&
            OnlineSessionIds.unwrap(current) != OnlineSessionIds.unwrap(sessionId)
        ) {
            error("Другая активная сессия")
        }
        activeSessionId = sessionId
    }

    private suspend fun requireToken(): String =
        authSession.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Не выполнен вход")

    private fun <T> Result<T>.mapGrpc(): Result<T> =
        fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(GrpcErrorMapper.toMessage(it), it)) },
        )

    companion object {
        private const val QUICK_MATCH_WAIT_MS = 120_000L
    }
}
