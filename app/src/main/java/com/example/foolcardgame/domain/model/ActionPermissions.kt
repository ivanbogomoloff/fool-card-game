package com.example.foolcardgame.domain.model

import com.example.foolcardgame.domain.engine.Rules

/** Permissions for the viewing/acting player — computed from [GameState]. */
data class ActionPermissions(
    val canReady: Boolean = false,
    val canBito: Boolean = false,
    val canPass: Boolean = false,
    val canTake: Boolean = false,
)

fun GameState.permissionsFor(playerId: String): ActionPermissions {
    val player = player(playerId) ?: return ActionPermissions()
    return when (phase) {
        GamePhase.LOBBY_WAITING -> ActionPermissions(canReady = !player.isReady)
        GamePhase.FINISHED -> ActionPermissions()
        GamePhase.IN_PROGRESS -> inProgressPermissions(playerId)
    }
}

private fun GameState.inProgressPermissions(playerId: String): ActionPermissions {
    val isDefender = playerId == defenderId
    val isAttacker = playerId == attackerId
    val hasUnbeaten = unbeatenPairs.isNotEmpty()

    val canTake = isDefender && hasUnbeaten
    // Attacker may declare «Бито» as soon as all cards are beaten (does not wait for helpers).
    val canBito = isAttacker && allBeaten && !attackerBitoDeclared
    // Helpers confirm «Бито» (engine: pass) after attacker declared.
    val canPass = !isDefender && !isAttacker &&
        attackerBitoDeclared &&
        allBeaten &&
        playerId in throwerIds() &&
        playerId !in passedPlayerIds

    return ActionPermissions(
        canBito = canBito,
        canPass = canPass,
        canTake = canTake,
    )
}

fun GameState.throwerIds(): Set<String> =
    players
        .filter { !it.isFinished && it.id != defenderId && it.hand.isNotEmpty() }
        .map { it.id }
        .toSet()

fun GameState.helperThrowerIds(): Set<String> =
    throwerIds() - setOfNotNull(attackerId)

fun GameState.throwingClosed(): Boolean {
    if (!allBeaten) return false
    if (!canAddMoreAttacks()) return true
    if (!attackerBitoDeclared) return false
    val helpers = helperThrowerIds()
    return helpers.isEmpty() || helpers.all { it in passedPlayerIds }
}

fun GameState.canAddMoreAttacks(): Boolean =
    Rules.canAddAttackCard(
        currentAttackCount = tablePairs.size,
        defenderHandSizeAtRoundStart = defenderHandSizeAtRoundStart,
    )

/** Helpers clockwise after attacker (for timeout auto-confirm order). */
fun GameState.throwPhaseTurnOrder(): List<String> {
    if (!allBeaten || !attackerBitoDeclared) return emptyList()
    val attacker = attackerId ?: return emptyList()
    val defender = defenderId
    val helpers = helperThrowerIds()
    if (helpers.isEmpty() || players.isEmpty()) return emptyList()

    // Seat order uses full [players] so a finished attacker still anchors clockwise order.
    val attIdx = players.indexOfFirst { it.id == attacker }
    if (attIdx < 0) {
        return players.map { it.id }.filter { it in helpers }
    }

    val ordered = mutableListOf<String>()
    for (i in 1 until players.size) {
        val player = players[(attIdx + i) % players.size]
        if (player.id == defender) continue
        if (player.id in helpers) ordered.add(player.id)
    }
    return ordered
}

fun GameState.nextThrowPhaseActor(): String? =
    throwPhaseTurnOrder().firstOrNull { it !in passedPlayerIds }
