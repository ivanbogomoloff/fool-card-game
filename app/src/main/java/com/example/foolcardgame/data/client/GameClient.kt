package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.CardDto
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RankDto
import com.example.foolcardgame.data.api.dto.SuitDto
import com.example.foolcardgame.data.api.dto.toDto
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import kotlinx.coroutines.flow.Flow

interface GameClient {
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

    /** Pause offline bots/timers while the screen is not active. Default no-op. */
    fun setPaused(paused: Boolean) {}

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 2_000L
    }
}

internal fun card(suit: Suit, rank: Rank): CardDto = Card(suit, rank).toDto()

internal fun card(suit: SuitDto, rank: RankDto): CardDto = CardDto(suit, rank)
