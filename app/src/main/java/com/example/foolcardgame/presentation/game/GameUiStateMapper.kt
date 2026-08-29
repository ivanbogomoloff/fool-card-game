package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.CardDto
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RankDto
import com.example.foolcardgame.data.api.dto.TablePairDto
import com.example.foolcardgame.data.api.dto.toDomain
import com.example.foolcardgame.domain.model.GamePhase

object GameUiStateMapper {

    fun map(dto: GameStateDto): GameUiState {
        val phase = dto.phase.toDomain()
        val opponents = dto.players
            .filter { it.id != dto.localPlayerId }
            .map {
                OpponentUi(
                    id = it.id,
                    displayName = it.displayName,
                    avatarId = it.avatarId,
                    cardCount = it.handCount,
                )
            }
        val waitingPlayers = dto.players.map {
            WaitingPlayerUi(
                id = it.id,
                displayName = it.displayName,
                avatarId = it.avatarId,
                isReady = it.isReady,
                isConnected = it.isConnected,
            )
        }
        return GameUiState(
            phase = phase,
            isLoading = false,
            sessionId = dto.sessionId,
            opponents = opponents,
            waitingPlayers = waitingPlayers,
            deckCount = dto.deckCount,
            trump = dto.trump?.toUi(),
            tablePairs = dto.tablePairs.map { it.toUi() },
            hand = dto.localHand.map { it.toUi() },
            actions = GameActionsUi(
                bitoEnabled = dto.canBito,
                passEnabled = dto.canPass,
                readyEnabled = dto.canReady,
                readyVisible = phase == GamePhase.LOBBY_WAITING,
            ),
            resultMessage = when (phase) {
                GamePhase.FINISHED -> buildResultMessage(dto)
                else -> null
            },
            serverTick = dto.serverTick,
        )
    }

    private fun buildResultMessage(dto: GameStateDto): String {
        return when {
            dto.winnerName != null && dto.loserName != null ->
                "Победитель: ${dto.winnerName}. Дурак: ${dto.loserName}."
            dto.winnerName != null -> "Победитель: ${dto.winnerName}."
            else -> "Партия завершена."
        }
    }

    private fun CardDto.toUi(): CardUi = CardUi(
        id = toDomain().id,
        rankLabel = rank.toLabel(),
        suitSymbol = suit.toSymbol(),
        isRed = suit == com.example.foolcardgame.data.api.dto.SuitDto.HEARTS ||
            suit == com.example.foolcardgame.data.api.dto.SuitDto.DIAMONDS,
    )

    private fun TablePairDto.toUi(): TablePairUi = TablePairUi(
        id = id,
        attack = attack.toUi(),
        defense = defense?.toUi(),
    )

    private fun RankDto.toLabel(): String = when (this) {
        RankDto.SIX -> "6"
        RankDto.SEVEN -> "7"
        RankDto.EIGHT -> "8"
        RankDto.NINE -> "9"
        RankDto.TEN -> "10"
        RankDto.JACK -> "В"
        RankDto.QUEEN -> "Д"
        RankDto.KING -> "К"
        RankDto.ACE -> "Т"
    }

    private fun com.example.foolcardgame.data.api.dto.SuitDto.toSymbol(): String = when (this) {
        com.example.foolcardgame.data.api.dto.SuitDto.SPADES -> "♠"
        com.example.foolcardgame.data.api.dto.SuitDto.HEARTS -> "♥"
        com.example.foolcardgame.data.api.dto.SuitDto.DIAMONDS -> "♦"
        com.example.foolcardgame.data.api.dto.SuitDto.CLUBS -> "♣"
    }
}
