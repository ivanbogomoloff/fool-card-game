package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.CardDto
import com.example.foolcardgame.data.api.dto.CreateGameResult
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RankDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.api.dto.SuitDto
import com.example.foolcardgame.data.api.dto.toDto
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** События очереди быстрой игры (gRPC QueueState / MatchStarted). */
sealed interface QueueUpdate {
    data class Waiting(
        val playerId: String,
        val waitingCount: Int,
        val phase: String,
    ) : QueueUpdate

    data class Matched(val sessionId: GameSessionId) : QueueUpdate
}

interface GameClient {
    /**
     * Вход: Auth.Login.
     * UI передаёт только [username]; [password] обычно пустой —
     * [RemoteGameClient] подставляет из Keystore, если имя совпадает с сохранённым.
     * Без пароля на сервере — регистрация нового имени (или ошибка «имя занято»).
     */
    suspend fun login(username: String = "", password: String = ""): Result<Unit> =
        Result.success(Unit)

    /** Есть ли сохранённая online-сессия. */
    fun isAuthorized(): Boolean = false

    /**
     * Быстрая игра через Session stream.
     * Отмена сбора Flow / [cancelQuickMatch] → Leave из очереди.
     */
    fun observeQuickMatch(displayName: String, avatarId: Int): Flow<QueueUpdate> = emptyFlow()

    /** Ошибки Session stream (BUSY, timeout, сеть) для UI. */
    fun observeSessionErrors(): Flow<String> = emptyFlow()

    suspend fun cancelQuickMatch(): Unit = Unit

    /** @deprecated Phase 5 poll; online использует [observeQuickMatch]. */
    suspend fun quickMatch(displayName: String, avatarId: Int): Result<GameSessionId?> =
        Result.failure(UnsupportedOperationException("Только online"))

    suspend fun createPrivateGame(displayName: String, avatarId: Int): Result<CreateGameResult> =
        Result.failure(UnsupportedOperationException("Только online"))

    suspend fun joinByCode(
        code: String,
        displayName: String,
        avatarId: Int,
    ): Result<Pair<GameSessionId, String>> =
        Result.failure(UnsupportedOperationException("Только online"))

    /** Снимок комнаты со stream (replay). */
    fun observeRoom(sessionId: GameSessionId): Flow<RoomStateDto> = emptyFlow()

    suspend fun getRoom(sessionId: GameSessionId): Result<RoomStateDto> =
        Result.failure(UnsupportedOperationException("Только online"))

    suspend fun kickPlayer(sessionId: GameSessionId, playerId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Только online"))

    suspend fun startGame(sessionId: GameSessionId): Result<Unit> =
        Result.failure(UnsupportedOperationException("Только online"))

    suspend fun createSession(config: GameConfig): GameSessionId
    suspend fun getState(sessionId: GameSessionId): GameStateDto
    fun observeState(
        sessionId: GameSessionId,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    ): Flow<GameStateDto>

    suspend fun playCard(sessionId: GameSessionId, card: Card, targetPairId: Int?): Result<Unit>
    suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit>
    suspend fun pass(sessionId: GameSessionId): Result<Unit>
    suspend fun bito(sessionId: GameSessionId): Result<Unit>
    suspend fun ready(sessionId: GameSessionId): Result<Unit>
    suspend fun skipTurn(sessionId: GameSessionId): Result<Unit>
    suspend fun leaveSession(sessionId: GameSessionId)

    /** Пауза офлайн-ботов/таймеров, пока экран неактивен. По умолчанию no-op. */
    fun setPaused(paused: Boolean) {}

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 2_000L
        const val ROOM_POLL_INTERVAL_MS = 5_000L
    }
}

internal fun card(suit: Suit, rank: Rank): CardDto = Card(suit, rank).toDto()

internal fun card(suit: SuitDto, rank: RankDto): CardDto = CardDto(suit, rank)
