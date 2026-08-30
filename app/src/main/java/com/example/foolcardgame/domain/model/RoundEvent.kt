package com.example.foolcardgame.domain.model

enum class RoundEventKind {
    TOOK,
    BITO,
}

/** One-shot UI hint emitted when a round ends by take or bito. */
data class RoundEvent(
    val kind: RoundEventKind,
    val playerId: String,
    val atTick: Long,
)
