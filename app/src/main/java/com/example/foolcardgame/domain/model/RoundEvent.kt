package com.example.foolcardgame.domain.model

enum class RoundEventKind {
    TOOK,
    BITO,
}

/** Одноразовый UI-сигнал при завершении раунда «беру» или «бито». */
data class RoundEvent(
    val kind: RoundEventKind,
    val playerId: String,
    val atTick: Long,
)
