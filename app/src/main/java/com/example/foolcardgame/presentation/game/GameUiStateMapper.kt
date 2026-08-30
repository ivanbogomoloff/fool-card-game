package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.CardDto
import com.example.foolcardgame.data.api.dto.GamePhaseDto
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.RankDto
import com.example.foolcardgame.data.api.dto.TablePairDto
import com.example.foolcardgame.data.api.dto.toDomain
import com.example.foolcardgame.domain.model.GamePhase

object GameUiStateMapper {

    fun map(dto: GameStateDto): GameUiState {
        val phase = dto.phase.toDomain()
        val hasUnbeaten = dto.tablePairs.any { it.defense == null }
        val allBeaten = dto.tablePairs.isNotEmpty() && dto.tablePairs.all { it.defense != null }

        val opponents = dto.players
            .filter { it.id != dto.localPlayerId }
            .map {
                OpponentUi(
                    id = it.id,
                    displayName = it.displayName,
                    avatarId = it.avatarId,
                    cardCount = it.handCount,
                    isConnected = it.isConnected,
                    isReady = it.isReady,
                    roleBanner = roleBannerFor(
                        playerId = it.id,
                        attackerId = dto.attackerId,
                        defenderId = dto.defenderId,
                        tableEmpty = dto.tablePairs.isEmpty(),
                        hasUnbeaten = hasUnbeaten,
                        allBeaten = allBeaten,
                    ),
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
        val localBanner = roleBannerFor(
            playerId = dto.localPlayerId,
            attackerId = dto.attackerId,
            defenderId = dto.defenderId,
            tableEmpty = dto.tablePairs.isEmpty(),
            hasUnbeaten = hasUnbeaten,
            allBeaten = allBeaten,
        )
        val revealLoserCards = dto.revealLoserCards.map { it.toUi() }
        val isLocalPlayerLoser = dto.loserId == dto.localPlayerId
        val canRevealLoserCards = dto.loserId != null &&
            !isLocalPlayerLoser &&
            revealLoserCards.isNotEmpty()
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
            actions = GameActionsUi(primary = resolvePrimaryAction(dto)),
            resultMessage = when (phase) {
                GamePhase.FINISHED -> buildResultMessage(dto)
                else -> null
            },
            localPlayerId = dto.localPlayerId,
            loserId = dto.loserId,
            loserName = dto.loserName,
            revealLoserCards = revealLoserCards,
            canRevealLoserCards = canRevealLoserCards,
            isLocalPlayerLoser = isLocalPlayerLoser,
            finishedSummary = buildFinishedSummary(dto),
            serverTick = dto.serverTick,
            hasDisconnectedOpponent = opponents.any { !it.isConnected },
            isLocalPlayerTurn = dto.currentPlayerId != null &&
                dto.currentPlayerId == dto.localPlayerId,
            isLocalDefending = localBanner == OpponentRoleBanner.DEFENDING,
            isLocalAttacking = localBanner == OpponentRoleBanner.ATTACKING,
        )
    }

    fun roleBannerFor(
        playerId: String,
        attackerId: String?,
        defenderId: String?,
        tableEmpty: Boolean,
        hasUnbeaten: Boolean,
        allBeaten: Boolean,
    ): OpponentRoleBanner = when {
        playerId == defenderId && hasUnbeaten -> OpponentRoleBanner.DEFENDING
        playerId == attackerId && (tableEmpty || allBeaten) -> OpponentRoleBanner.ATTACKING
        else -> OpponentRoleBanner.NONE
    }

    fun resolvePrimaryAction(dto: GameStateDto): HandPrimaryAction = when {
        dto.phase == GamePhaseDto.FINISHED -> HandPrimaryAction.FINISHED
        dto.canReady -> HandPrimaryAction.READY
        dto.canTake -> HandPrimaryAction.TAKE
        dto.canBito -> HandPrimaryAction.BITO
        dto.canPass -> HandPrimaryAction.PASS
        else -> HandPrimaryAction.NONE
    }

    private fun buildFinishedSummary(dto: GameStateDto): String? {
        if (dto.phase != GamePhaseDto.FINISHED) return null
        return if (dto.loserId == dto.localPlayerId) "Вы — дурак" else null
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

    fun mapTablePairs(pairs: List<TablePairDto>): List<TablePairUi> =
        pairs.map { it.toUi() }

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
