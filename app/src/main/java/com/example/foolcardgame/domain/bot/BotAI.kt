package com.example.foolcardgame.domain.bot

import com.example.foolcardgame.domain.engine.Rules
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.permissionsFor
import com.example.foolcardgame.domain.model.throwerIds
import com.example.foolcardgame.domain.model.canAddMoreAttacks

/**
 * Simple deterministic bot: lowest legal card / obvious pass-bito-take.
 */
class BotAI {

    sealed class Action {
        data class Ready(val playerId: String) : Action()
        data class PlayCard(val playerId: String, val card: Card, val targetPairId: Int?) : Action()
        data class AddCard(val playerId: String, val card: Card) : Action()
        data class Pass(val playerId: String) : Action()
        data class Bito(val playerId: String) : Action()
    }

    /**
     * @param controlAllPlayers when true (functional tests), any player can be driven by AI.
     */
    fun chooseAction(state: GameState, controlAllPlayers: Boolean = false): Action? {
        fun controllable(isBot: Boolean) = controlAllPlayers || isBot

        when (state.phase) {
            GamePhase.LOBBY_WAITING -> {
                val bot = state.players.firstOrNull {
                    controllable(it.isBot) && !it.isReady
                } ?: return null
                return Action.Ready(bot.id)
            }
            GamePhase.FINISHED -> return null
            GamePhase.IN_PROGRESS -> Unit
        }

        val defenderId = state.defenderId
        if (defenderId != null) {
            val defender = state.player(defenderId)
            if (defender != null && controllable(defender.isBot) && state.unbeatenPairs.isNotEmpty()) {
                val trump = state.trumpSuit ?: return Action.Pass(defenderId)
                val unbeaten = state.unbeatenPairs.first()
                val beating = defender.hand
                    .filter { Rules.beats(it, unbeaten.attack, trump) }
                    .minWithOrNull(lowestFirst(trump))
                return if (beating != null) {
                    Action.PlayCard(defenderId, beating, unbeaten.id)
                } else {
                    Action.Pass(defenderId)
                }
            }
        }

        val attackerId = state.attackerId
        if (attackerId != null) {
            val attacker = state.player(attackerId)
            if (attacker != null && controllable(attacker.isBot) &&
                state.permissionsFor(attackerId).canBito
            ) {
                return Action.Bito(attackerId)
            }
        }

        if (state.tablePairs.isNotEmpty() && state.allBeaten) {
            for (throwerId in state.throwerIds()) {
                val thrower = state.player(throwerId) ?: continue
                if (!controllable(thrower.isBot)) continue
                if (throwerId in state.passedPlayerIds) continue
                val perms = state.permissionsFor(throwerId)
                if (perms.canPass || state.canThrowSomething(throwerId)) {
                    val throwCard = thrower.hand
                        .filter { Rules.canThrow(it, state.tableRanks) }
                        .minWithOrNull(lowestFirst(state.trumpSuit))
                    return if (throwCard != null && state.canAddMoreSafe()) {
                        Action.AddCard(throwerId, throwCard)
                    } else if (perms.canPass) {
                        Action.Pass(throwerId)
                    } else {
                        continue
                    }
                }
            }
        }

        if (state.tablePairs.isEmpty() && attackerId != null) {
            val attacker = state.player(attackerId) ?: return null
            if (!controllable(attacker.isBot)) return null
            val card = attacker.hand.minWithOrNull(lowestFirst(state.trumpSuit)) ?: return null
            return Action.PlayCard(attackerId, card, targetPairId = null)
        }

        if (attackerId != null && state.tablePairs.isNotEmpty()) {
            val attacker = state.player(attackerId)
            if (attacker != null && controllable(attacker.isBot)) {
                val throwCard = attacker.hand
                    .filter { Rules.canThrow(it, state.tableRanks) }
                    .minWithOrNull(lowestFirst(state.trumpSuit))
                if (throwCard != null && state.canAddMoreSafe()) {
                    return Action.AddCard(attackerId, throwCard)
                }
            }
        }

        return null
    }

    private fun GameState.canThrowSomething(playerId: String): Boolean {
        val hand = player(playerId)?.hand.orEmpty()
        return hand.any { Rules.canThrow(it, tableRanks) } && canAddMoreSafe()
    }

    private fun GameState.canAddMoreSafe(): Boolean = canAddMoreAttacks()

    private fun lowestFirst(trumpSuit: com.example.foolcardgame.domain.model.Suit?) =
        Comparator<Card> { a, b ->
            val aTrump = trumpSuit != null && a.suit == trumpSuit
            val bTrump = trumpSuit != null && b.suit == trumpSuit
            when {
                aTrump != bTrump -> if (aTrump) 1 else -1
                a.rank != b.rank -> a.rank.compareTo(b.rank)
                else -> a.suit.compareTo(b.suit)
            }
        }
}
