package com.example.foolcardgame.domain.model

enum class GameActionKind {
    ATTACK,
    DEFEND,
    THROW_IN,
    PASS,
    TOOK,
    BITO,
}

/** One-shot UI hint emitted when a player performs an action. */
data class GameActionEvent(
    val kind: GameActionKind,
    val playerId: String,
    val atTick: Long,
)
