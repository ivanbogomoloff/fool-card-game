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
        deckCount = 18,
        trump = card(SuitDto.HEARTS, RankDto.SEVEN),
        currentPlayerId = null,
        canBito = false,
        canPass = false,
        canTake = false,
        canReady = true,
        localHand = listOf(
            card(SuitDto.HEARTS, RankDto.SIX),
            card(SuitDto.SPADES, RankDto.SEVEN),
            card(SuitDto.DIAMONDS, RankDto.KING),
            card(SuitDto.CLUBS, RankDto.ACE),
            card(SuitDto.HEARTS, RankDto.NINE),
            card(SuitDto.SPADES, RankDto.TEN),
        ),
        tablePairs = emptyList(),
        players = listOf(
            localPlayer(isReady = false, handCount = 6, status = PlayerStatusDto.WAITING),
            botPlayer(id = "bot-1", name = "Бот 1", handCount = 6, isReady = false),
            botPlayer(id = "bot-2", name = "Бот 2", handCount = 6, isReady = false),
        ),
    )

    fun lobbyWithDisconnected(): GameStateDto = inProgress().copy(
        canBito = true,
        canPass = false,
        canTake = false,
        canReady = false,
        players = listOf(
            localPlayer(isReady = true, handCount = 12, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-1", name = "Бот 1", handCount = 3, isReady = true, status = PlayerStatusDto.PLAYING),
            botPlayer(
                id = "bot-2",
                name = "Бот 2",
                handCount = 6,
                isReady = true,
                isConnected = false,
                status = PlayerStatusDto.DISCONNECTED,
            ),
        ),
    )

    /** Стол с картами и кнопкой «Беру» — для проверки анимации улёта вниз. */
    fun takePending(): GameStateDto = inProgress().copy(
        canBito = false,
        canPass = false,
        canTake = true,
        canReady = false,
        currentPlayerId = LOCAL_PLAYER_ID,
        attackerId = "bot-1",
        defenderId = LOCAL_PLAYER_ID,
    )

    /** Ход оппонента-атакующего — пустой стол, индикатор «Ходит». */
    fun opponentTurn(): GameStateDto = inProgress().copy(
        currentPlayerId = "bot-1",
        attackerId = "bot-1",
        defenderId = LOCAL_PLAYER_ID,
        canBito = false,
        canPass = false,
        canTake = false,
        canReady = false,
        tablePairs = emptyList(),
    )

    fun inProgress(): GameStateDto = GameStateDto(
        sessionId = DEBUG_SESSION_ID,
        phase = GamePhaseDto.IN_PROGRESS,
        localPlayerId = LOCAL_PLAYER_ID,
        serverTick = 0L,
        deckCount = 12,
        trump = card(SuitDto.HEARTS, RankDto.SEVEN),
        currentPlayerId = LOCAL_PLAYER_ID,
        attackerId = LOCAL_PLAYER_ID,
        defenderId = "bot-1",
        canBito = true,
        canPass = false,
        canTake = false,
        canReady = false,
        localHand = listOf(
            card(SuitDto.HEARTS, RankDto.SIX),
            card(SuitDto.SPADES, RankDto.SEVEN),
            card(SuitDto.DIAMONDS, RankDto.KING),
            card(SuitDto.CLUBS, RankDto.ACE),
            card(SuitDto.HEARTS, RankDto.NINE),
            card(SuitDto.SPADES, RankDto.TEN),
            card(SuitDto.CLUBS, RankDto.JACK),
            card(SuitDto.DIAMONDS, RankDto.QUEEN),
            card(SuitDto.HEARTS, RankDto.KING),
            card(SuitDto.SPADES, RankDto.ACE),
            card(SuitDto.CLUBS, RankDto.SIX),
            card(SuitDto.DIAMONDS, RankDto.EIGHT),
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
            localPlayer(isReady = true, handCount = 12, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-1", name = "Бот 1", handCount = 3, isReady = true, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-2", name = "Бот 2", handCount = 6, isReady = true, status = PlayerStatusDto.PLAYING),
        ),
    )

    fun finished(): GameStateDto = inProgress().copy(
        phase = GamePhaseDto.FINISHED,
        canBito = false,
        canPass = false,
        canTake = false,
        canReady = false,
        currentPlayerId = null,
        winnerName = "Вы",
        loserName = "Бот 2",
        loserId = "bot-2",
        localHand = listOf(
            card(SuitDto.HEARTS, RankDto.SIX),
            card(SuitDto.SPADES, RankDto.SEVEN),
        ),
        tablePairs = listOf(
            TablePairDto(
                id = 1,
                attack = card(SuitDto.DIAMONDS, RankDto.SEVEN),
                defense = card(SuitDto.DIAMONDS, RankDto.TEN),
            ),
        ),
        deckCount = 0,
        players = listOf(
            localPlayer(isReady = true, handCount = 2, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-1", name = "Бот 1", handCount = 0, isReady = true, status = PlayerStatusDto.PLAYING),
            botPlayer(id = "bot-2", name = "Бот 2", handCount = 3, isReady = true, status = PlayerStatusDto.PLAYING),
        ),
        revealLoserCards = listOf(
            card(SuitDto.CLUBS, RankDto.SIX),
            card(SuitDto.HEARTS, RankDto.EIGHT),
            card(SuitDto.SPADES, RankDto.NINE),
        ),
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
