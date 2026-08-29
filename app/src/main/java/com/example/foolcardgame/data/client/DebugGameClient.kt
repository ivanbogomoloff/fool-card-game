package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.domain.model.Card
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update

class DebugGameClient(
    initialScenario: DebugScenario = DebugScenario.IN_PROGRESS,
) : GameClient {

    private val state = MutableStateFlow(initialScenario.toMockState())

    fun setScenario(scenario: DebugScenario) {
        state.value = scenario.toMockState()
    }

    fun currentState(): GameStateDto = state.value

    override suspend fun getState(sessionId: GameSessionId): GameStateDto = state.value

    override fun observeState(
        sessionId: GameSessionId,
        pollIntervalMs: Long,
    ): Flow<GameStateDto> {
        val ticks = flow {
            while (true) {
                delay(pollIntervalMs)
                state.update { current ->
                    current.copy(serverTick = (current.serverTick ?: 0L) + 1)
                }
                emit(state.value)
            }
        }
        return merge(state, ticks).distinctUntilChanged()
    }

    override suspend fun playCard(
        sessionId: GameSessionId,
        card: Card,
        targetPairId: Int?,
    ): Result<Unit> = refreshAfterAction()

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> =
        refreshAfterAction()

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> = refreshAfterAction()

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> = refreshAfterAction()

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> {
        state.update { current ->
            current.copy(
                players = current.players.map { player ->
                    if (player.id == MockGameStates.LOCAL_PLAYER_ID) {
                        player.copy(isReady = true, status = com.example.foolcardgame.data.api.dto.PlayerStatusDto.PLAYING)
                    } else {
                        player
                    }
                },
                canReady = false,
            )
        }
        return refreshAfterAction()
    }

    override suspend fun leaveSession(sessionId: GameSessionId) = Unit

    private fun refreshAfterAction(): Result<Unit> {
        state.update { current ->
            current.copy(serverTick = (current.serverTick ?: 0L) + 1)
        }
        return Result.success(Unit)
    }
}
