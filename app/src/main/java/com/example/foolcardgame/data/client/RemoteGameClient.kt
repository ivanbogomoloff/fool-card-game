package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.FakeGameApi
import com.example.foolcardgame.data.api.GameApi
import com.example.foolcardgame.data.api.dto.CreateGameResult
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.JoinByCodeRequestDto
import com.example.foolcardgame.data.api.dto.KickPlayerRequestDto
import com.example.foolcardgame.data.api.dto.LoginRequestDto
import com.example.foolcardgame.data.api.dto.PlayerProfileRequestDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.local.AuthSessionStore
import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Сетевой [GameClient] на базе [GameApi] (Phase 5 — [FakeGameApi]).
 */
class RemoteGameClient(
    private val api: GameApi = FakeGameApi(),
    private val authSession: AuthSessionStore = InMemoryAuthSessionStore(),
) : GameClient {

    private val mutex = Mutex()
    private var activeSessionId: GameSessionId? = null

    override fun isAuthorized(): Boolean = authSession.isAuthorized()

    override suspend fun login(displayName: String?): Result<Unit> = runCatching {
        val response = api.login(LoginRequestDto(displayName = displayName))
        authSession.saveToken(response.token)
    }

    override suspend fun quickMatch(displayName: String, avatarId: Int): Result<GameSessionId?> =
        runCatching {
            val response = api.fastJoin(
                requireToken(),
                PlayerProfileRequestDto(displayName = displayName, avatarId = avatarId),
            )
            response?.sessionId?.also { activeSessionId = it }
        }

    override suspend fun createPrivateGame(
        displayName: String,
        avatarId: Int,
    ): Result<CreateGameResult> = runCatching {
        val response = api.createGame(
            requireToken(),
            PlayerProfileRequestDto(displayName = displayName, avatarId = avatarId),
        )
        activeSessionId = response.sessionId
        CreateGameResult(
            sessionId = response.sessionId,
            accessCode = response.accessCode,
            hostId = response.hostId,
        )
    }

    override suspend fun joinByCode(
        code: String,
        displayName: String,
        avatarId: Int,
    ): Result<Pair<GameSessionId, String>> = runCatching {
        val response = api.joinGame(
            requireToken(),
            JoinByCodeRequestDto(
                code = code.trim().uppercase(),
                displayName = displayName,
                avatarId = avatarId,
            ),
        )
        activeSessionId = response.sessionId
        response.sessionId to response.playerId
    }

    override suspend fun getRoom(sessionId: GameSessionId): Result<RoomStateDto> = runCatching {
        api.getRoom(requireToken(), sessionId)
    }

    override suspend fun kickPlayer(sessionId: GameSessionId, playerId: String): Result<Unit> =
        runCatching {
            api.kickPlayer(requireToken(), sessionId, KickPlayerRequestDto(playerId = playerId))
        }

    override suspend fun startGame(sessionId: GameSessionId): Result<Unit> = runCatching {
        api.startGame(requireToken(), sessionId)
        Unit
    }

    override suspend fun createSession(config: GameConfig): GameSessionId = mutex.withLock {
        val id = "online-${System.currentTimeMillis()}"
        activeSessionId = id
        id
    }

    override suspend fun getState(sessionId: GameSessionId): GameStateDto =
        api.getState(requireToken(), sessionId)

    override fun observeState(
        sessionId: GameSessionId,
        pollIntervalMs: Long,
    ): Flow<GameStateDto> {
        val ticks = flow {
            while (true) {
                delay(pollIntervalMs)
                emit(getState(sessionId))
            }
        }
        val immediate = flow { emit(getState(sessionId)) }
        return merge(immediate, ticks)
    }

    override suspend fun playCard(
        sessionId: GameSessionId,
        card: Card,
        targetPairId: Int?,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Онлайн-ходы — в следующих задачах Phase 5/6"))

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> =
        Result.failure(UnsupportedOperationException("Онлайн-ходы — позже"))

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> =
        Result.failure(UnsupportedOperationException("Онлайн-ходы — позже"))

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> =
        Result.failure(UnsupportedOperationException("Онлайн-ходы — позже"))

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> = Result.success(Unit)

    override suspend fun skipTurn(sessionId: GameSessionId): Result<Unit> =
        Result.failure(UnsupportedOperationException("Онлайн skip — позже"))

    override suspend fun leaveSession(sessionId: GameSessionId) {
        mutex.withLock {
            if (activeSessionId == sessionId) activeSessionId = null
        }
    }

    private suspend fun requireToken(): String =
        authSession.getToken()?.takeIf { it.isNotBlank() }
            ?: error("Не выполнен вход")
}
