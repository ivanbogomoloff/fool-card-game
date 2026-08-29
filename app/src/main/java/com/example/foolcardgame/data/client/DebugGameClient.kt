package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.PlayerStatusDto
import com.example.foolcardgame.data.api.dto.TablePairDto
import com.example.foolcardgame.data.api.dto.toDto
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

    fun setTakePending() {
        state.value = MockGameStates.takePending()
    }

    fun setOpponentTurn() {
        state.value = MockGameStates.opponentTurn()
    }

    fun clearTable() {
        state.update { current ->
            current.copy(
                tablePairs = emptyList(),
                serverTick = (current.serverTick ?: 0L) + 1,
            )
        }
    }

    fun currentState(): GameStateDto = state.value

    override suspend fun createSession(config: com.example.foolcardgame.domain.model.GameConfig): GameSessionId {
        return MockGameStates.DEBUG_SESSION_ID
    }

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
    ): Result<Unit> {
        state.update { current ->
            val cardDto = card.toDto()
            val handIndex = current.localHand.indexOfFirst {
                it.suit == cardDto.suit && it.rank == cardDto.rank
            }
            if (handIndex < 0) return@update current

            val newHand = current.localHand.toMutableList().also { it.removeAt(handIndex) }
            val newPairs = current.tablePairs.toMutableList()
            if (targetPairId == null) {
                val nextId = (newPairs.maxOfOrNull { it.id } ?: 0) + 1
                newPairs.add(TablePairDto(id = nextId, attack = cardDto))
            } else {
                val pairIndex = newPairs.indexOfFirst { it.id == targetPairId }
                if (pairIndex < 0 || newPairs[pairIndex].defense != null) return@update current
                newPairs[pairIndex] = newPairs[pairIndex].copy(defense = cardDto)
            }
            current.copy(
                localHand = newHand,
                tablePairs = newPairs,
                players = current.players.map { player ->
                    if (player.id == MockGameStates.LOCAL_PLAYER_ID) {
                        player.copy(handCount = newHand.size)
                    } else {
                        player
                    }
                },
                serverTick = (current.serverTick ?: 0L) + 1,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun addCard(sessionId: GameSessionId, card: Card): Result<Unit> {
        return playCard(sessionId, card, targetPairId = null)
    }

    override suspend fun pass(sessionId: GameSessionId): Result<Unit> {
        state.update { current ->
            val takenCards = current.tablePairs.flatMap { pair ->
                listOfNotNull(pair.attack, pair.defense)
            }
            val newHand = current.localHand + takenCards
            current.copy(
                localHand = newHand,
                tablePairs = emptyList(),
                canTake = false,
                canBito = false,
                canPass = false,
                players = current.players.map { player ->
                    if (player.id == MockGameStates.LOCAL_PLAYER_ID) {
                        player.copy(handCount = newHand.size)
                    } else {
                        player
                    }
                },
                serverTick = (current.serverTick ?: 0L) + 1,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun bito(sessionId: GameSessionId): Result<Unit> {
        state.update { current ->
            current.copy(
                tablePairs = emptyList(),
                serverTick = (current.serverTick ?: 0L) + 1,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun ready(sessionId: GameSessionId): Result<Unit> {
        state.update { current ->
            current.copy(
                players = current.players.map { player ->
                    if (player.id == MockGameStates.LOCAL_PLAYER_ID) {
                        player.copy(isReady = true, status = PlayerStatusDto.PLAYING)
                    } else {
                        player
                    }
                },
                canReady = false,
            )
        }
        return refreshAfterAction()
    }

    override suspend fun skipTurn(sessionId: GameSessionId): Result<Unit> {
        state.update { current ->
            current.copy(
                currentPlayerId = current.players
                    .firstOrNull { it.id != current.currentPlayerId }
                    ?.id,
                serverTick = (current.serverTick ?: 0L) + 1,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun leaveSession(sessionId: GameSessionId) {
        state.update { current ->
            current.copy(
                canReady = false,
                canBito = false,
                canPass = false,
                canTake = false,
                serverTick = (current.serverTick ?: 0L) + 1,
            )
        }
    }

    private fun refreshAfterAction(): Result<Unit> {
        state.update { current ->
            current.copy(serverTick = (current.serverTick ?: 0L) + 1)
        }
        return Result.success(Unit)
    }
}
