package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.domain.model.GamePhase

data class CardUi(
    val id: String,
    val rankLabel: String,
    val suitSymbol: String,
    val isRed: Boolean,
)

data class TablePairUi(
    val id: Int,
    val attack: CardUi,
    val defense: CardUi?,
)

data class OpponentUi(
    val id: String,
    val displayName: String,
    val avatarId: Int,
    val cardCount: Int,
    val isConnected: Boolean = true,
    val isReady: Boolean = true,
    val isCurrentTurn: Boolean = false,
)

data class WaitingPlayerUi(
    val id: String,
    val displayName: String,
    val avatarId: Int,
    val isReady: Boolean,
    val isConnected: Boolean,
)

enum class HandPrimaryAction {
    READY,
    BITO,
    PASS,
    TAKE,
    NONE,
}

data class GameActionsUi(
    val primary: HandPrimaryAction = HandPrimaryAction.NONE,
)

data class GameUiState(
    val phase: GamePhase = GamePhase.LOBBY_WAITING,
    val isLoading: Boolean = true,
    val sessionId: String = "",
    val opponents: List<OpponentUi> = emptyList(),
    val waitingPlayers: List<WaitingPlayerUi> = emptyList(),
    val deckCount: Int = 0,
    val trump: CardUi? = null,
    val tablePairs: List<TablePairUi> = emptyList(),
    val hand: List<CardUi> = emptyList(),
    val selectedCardId: String? = null,
    val showLeaveDialog: Boolean = false,
    val actions: GameActionsUi = GameActionsUi(),
    val resultMessage: String? = null,
    val serverTick: Long? = null,
    val hasDisconnectedOpponent: Boolean = false,
    val readySecondsLeft: Int? = null,
    val turnSecondsLeft: Int? = null,
    val showLobbyTimeoutDialog: Boolean = false,
    val isLocalPlayerTurn: Boolean = false,
)
