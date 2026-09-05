package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.RoundEventKind

data class GameHistoryEntryUi(
    val id: String,
    val text: String,
    val timestampMs: Long,
)

data class OpponentActionUi(
    val opponentId: String,
    val message: String,
    val atTick: Long,
)

/** One-shot scale pulse on opponent name plate (e.g. after THROW_IN). */
data class OpponentPulseUi(
    val opponentId: String,
    val atTick: Long,
)

data class TableFlyAnimationUi(
    val pairs: List<TablePairUi>,
    val targetOpponentId: String,
    val kind: RoundEventKind,
    val atTick: Long,
)

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
    val roleBanner: OpponentRoleBanner = OpponentRoleBanner.NONE,
)

enum class OpponentRoleBanner {
    NONE,
    ATTACKING,
    DEFENDING,
}

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
    FINISHED,
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
    val localPlayerId: String = "",
    val loserId: String? = null,
    val loserName: String? = null,
    val revealLoserCards: List<CardUi> = emptyList(),
    val showLoserCards: Boolean = false,
    val canRevealLoserCards: Boolean = false,
    val isLocalPlayerLoser: Boolean = false,
    val finishedSummary: String? = null,
    val serverTick: Long? = null,
    val hasDisconnectedOpponent: Boolean = false,
    val readySecondsLeft: Int? = null,
    val turnSecondsLeft: Int? = null,
    val showLobbyTimeoutDialog: Boolean = false,
    val isLocalPlayerTurn: Boolean = false,
    val isLocalDefending: Boolean = false,
    /** True while local player is the round defender (even if all cards are beaten). */
    val isLocalDefender: Boolean = false,
    val isLocalAttacking: Boolean = false,
    val opponentAction: OpponentActionUi? = null,
    val opponentPulse: OpponentPulseUi? = null,
    val tableFlyAnimation: TableFlyAnimationUi? = null,
    val gameHistory: List<GameHistoryEntryUi> = emptyList(),
)
