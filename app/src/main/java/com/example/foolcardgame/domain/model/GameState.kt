package com.example.foolcardgame.domain.model

/**
 * Immutable snapshot of a game session — domain analogue of a REST state payload.
 * No Android / HTTP dependencies.
 */
data class GameState(
    val sessionId: String,
    val phase: GamePhase,
    val players: List<Player>,
    /** Remaining draw pile; index 0 is next to draw; last card is the face-up trump. */
    val deck: List<Card>,
    val trumpCard: Card?,
    val trumpSuit: Suit?,
    val tablePairs: List<TablePair>,
    val attackerId: String?,
    val defenderId: String?,
    /** Whose primary action is expected (attack / defend / throw). */
    val currentPlayerId: String?,
    val passedPlayerIds: Set<String> = emptySet(),
    /** Max attack cards this round = min(6, this value). */
    val defenderHandSizeAtRoundStart: Int = 0,
    val tick: Long = 0,
    val winnerIds: List<String> = emptyList(),
    val loserId: String? = null,
    /** Cards removed after successful «бито». */
    val discardPile: List<Card> = emptyList(),
    val turnStartedAtMs: Long? = null,
    /** When set, [currentPlayerId] must act before this epoch ms or the turn is skipped. */
    val turnDeadlineAtMs: Long? = null,
    /** When set, UI may show take/bito feedback; cleared at the start of the next mutate. */
    val lastRoundEvent: RoundEvent? = null,
    /** When set, UI may append an action to game history; cleared at the start of the next mutate. */
    val lastActionEvent: GameActionEvent? = null,
) {
    fun player(id: String): Player? = players.find { it.id == id }

    fun playersInGame(): List<Player> = players.filter { !it.isFinished }

    fun playersWithCards(): List<Player> =
        players.filter { !it.isFinished && it.hand.isNotEmpty() }

    val tableRanks: Set<Rank>
        get() = tablePairs.flatMap { pair ->
            listOfNotNull(pair.attack.rank, pair.defense?.rank)
        }.toSet()

    val unbeatenPairs: List<TablePair>
        get() = tablePairs.filter { !it.isBeaten }

    val allBeaten: Boolean
        get() = tablePairs.isNotEmpty() && tablePairs.all { it.isBeaten }
}
