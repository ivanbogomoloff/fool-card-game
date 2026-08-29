package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.GamePhaseDto
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.PlayerStateDto
import com.example.foolcardgame.data.api.dto.PlayerStatusDto
import com.example.foolcardgame.data.api.dto.RankDto
import com.example.foolcardgame.data.api.dto.SuitDto
import com.example.foolcardgame.data.api.dto.TablePairDto

object MockGameStates {

    const val DEBUG_SESSION_ID = "debug"
    const val LOCAL_PLAYER_ID = "local"

    fun lobbyWaiting(): GameStateDto = GameStateDto(
        sessionId = DEBUG_SESSION_ID,
        phase = GamePhaseDto.LOBBY_WAITING,
        localPlayerId = LOCAL_PLAYER_ID,
        serverTick = 0L,
        canReady = true,
        players = listOf(
            localPlayer(isReady = false),
            botPlayer(id = "bot-1", name = "Бот 1", isReady = true),
            botPlayer(id = "bot-2", name = "Бот 2", isReady = false),
        ),
    )

    fun lobbyWithDisconnected(): GameStateDto = lobbyWaiting().copy(
        players = listOf(
            localPlayer(isReady = true),
            botPlayer(id = "bot-1", name = "Бот 1", isReady = true),
            botPlayer(
                id = "bot-2",
                name = "Бот 2",
                isReady = false,
                isConnected = false,
                status = PlayerStatusDto.DISCONNECTED,
            ),
        ),
        canReady = false,
    )

    fun inProgress(): GameStateDto = GameStateDto(
        sessionId = DEBUG_SESSION_ID,
        phase = GamePhaseDto.IN_PROGRESS,
        localPlayerId = LOCAL_PLAYER_ID,
        serverTick = 0L,
        deckCount = 12,
        trump = card(SuitDto.HEARTS, RankDto.SEVEN),
        currentPlayerId = LOCAL_PLAYER_ID,
        canBito = true,
        canPass = false,
        canReady = false,
        localHand = listOf(
            card(SuitDto.HEARTS, RankDto.SIX),
            card(SuitDto.SPADES, RankDto.SEVEN),
            card(SuitDto.DIAMONDS, RankDto.KING),
            card(SuitDto.CLUBS, RankDto.ACE),
            card(SuitDto.HEARTS, RankDto.NINE),
        ),
        tablePairs = listOf(
            TablePairDto(
                id = 1,
                attack = card(SuitDto.DIAMONDS, RankDto.SEVEN),
                defense = card(SuitDto.DIAMONDS, RankDto.TEN),
            ),
            TablePairDto(
                id = 2,
                attack = card(SuitDto.CLUBS, RankDto.EIGHT),
            ),
        ),
        players = listOf(
            localPlayer(isReady = true, handCount = 5, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-1", name = "Бот 1", handCount = 3, isReady = true, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-2", name = "Бот 2", handCount = 6, isReady = true, status = PlayerStatusDto.PLAYING),
        ),
    )

    fun finished(): GameStateDto = inProgress().copy(
        phase = GamePhaseDto.FINISHED,
        canBito = false,
        canPass = false,
        canReady = false,
        winnerName = "Игрок",
        loserName = "Бот 2",
        localHand = emptyList(),
        tablePairs = emptyList(),
        deckCount = 0,
    )

    private fun localPlayer(
        isReady: Boolean,
        handCount: Int = 0,
        status: PlayerStatusDto = PlayerStatusDto.WAITING,
    ): PlayerStateDto = PlayerStateDto(
        id = LOCAL_PLAYER_ID,
        displayName = "Вы",
        avatarId = 0,
        handCount = handCount,
        isReady = isReady,
        isConnected = true,
        status = status,
    )

    private fun botPlayer(
        id: String,
        name: String,
        handCount: Int = 0,
        isReady: Boolean = false,
        isConnected: Boolean = true,
        status: PlayerStatusDto = PlayerStatusDto.WAITING,
    ): PlayerStateDto = PlayerStateDto(
        id = id,
        displayName = name,
        avatarId = 1,
        handCount = handCount,
        isReady = isReady,
        isConnected = isConnected,
        status = status,
    )
}

enum class DebugScenario {
    LOBBY_WAITING,
    LOBBY_DISCONNECTED,
    IN_PROGRESS,
    FINISHED,
}

fun DebugScenario.toMockState(): GameStateDto = when (this) {
    DebugScenario.LOBBY_WAITING -> MockGameStates.lobbyWaiting()
    DebugScenario.LOBBY_DISCONNECTED -> MockGameStates.lobbyWithDisconnected()
    DebugScenario.IN_PROGRESS -> MockGameStates.inProgress()
    DebugScenario.FINISHED -> MockGameStates.finished()
}
