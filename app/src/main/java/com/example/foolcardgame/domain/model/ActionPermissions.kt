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
    val hasTable = tablePairs.isNotEmpty()
    val hasUnbeaten = unbeatenPairs.isNotEmpty()

    val canTake = isDefender && hasUnbeaten
    val canBito = isAttacker && allBeaten && throwingClosed()
    val canPass = !isDefender && hasTable && allBeaten &&
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

fun GameState.throwingClosed(): Boolean {
    if (!allBeaten) return false
    if (!canAddMoreAttacks()) return true
    val throwers = throwerIds()
    return throwers.isEmpty() || throwers.all { it in passedPlayerIds }
}

fun GameState.canAddMoreAttacks(): Boolean =
    Rules.canAddAttackCard(
        currentAttackCount = tablePairs.size,
        defenderHandSizeAtRoundStart = defenderHandSizeAtRoundStart,
    )
