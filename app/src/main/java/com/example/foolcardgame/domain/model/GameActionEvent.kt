package com.example.foolcardgame.domain.model

enum class GameActionKind {
    ATTACK,
    DEFEND,
    THROW_IN,
    PASS,
    TOOK,
    BITO,
}

/** Одноразовый UI-сигнал при действии игрока. */
data class GameActionEvent(
    val kind: GameActionKind,
    val playerId: String,
    val atTick: Long,
)
