package com.example.foolcardgame.domain.model

/**
 * Неизменяемый снимок игровой сессии — доменный аналог REST-состояния.
 * Без зависимостей от Android / HTTP.
 */
data class GameState(
    val sessionId: String,
    val phase: GamePhase,
    val players: List<Player>,
    /** Оставшаяся колода; индекс 0 — следующая к добору; последняя карта — открытый козырь. */
    val deck: List<Card>,
    val trumpCard: Card?,
    val trumpSuit: Suit?,
    val tablePairs: List<TablePair>,
    val attackerId: String?,
    val defenderId: String?,
    /** От кого ожидается основное действие (атака / отбивка / подкид). */
    val currentPlayerId: String?,
    val passedPlayerIds: Set<String> = emptySet(),
    /**
     * Атакующий нажал «Бито», пока все карты на столе отбиты.
     * Помощники подтверждают через pass (UI «Бито»); сбрасывается при новом подкиде / конце раунда.
     */
    val attackerBitoDeclared: Boolean = false,
    /** Максимум атакующих карт в раунде = min(6, это значение). */
    val defenderHandSizeAtRoundStart: Int = 0,
    val tick: Long = 0,
    val winnerIds: List<String> = emptyList(),
    val loserId: String? = null,
    /** Карты, ушедшие в отбой после успешного «бито». */
    val discardPile: List<Card> = emptyList(),
    val turnStartedAtMs: Long? = null,
    /** Если задано, [currentPlayerId] должен сходить до этого epoch ms, иначе ход пропускается. */
    val turnDeadlineAtMs: Long? = null,
    /** Если задано, UI может показать отклик «беру»/«бито»; очищается в начале следующего mutate. */
    val lastRoundEvent: RoundEvent? = null,
    /** Если задано, UI может добавить действие в историю; очищается в начале следующего mutate. */
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
