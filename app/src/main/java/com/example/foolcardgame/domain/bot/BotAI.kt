package com.example.foolcardgame.domain.bot

import com.example.foolcardgame.domain.engine.Rules
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.canAddMoreAttacks
import com.example.foolcardgame.domain.model.helperThrowerIds
import com.example.foolcardgame.domain.model.permissionsFor

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

        // Mid-defense: any non-defender may throw in parallel.
        if (state.unbeatenPairs.isNotEmpty()) {
            chooseThrowIn(state, { controllable(it) }, includeAttacker = true)?.let { return it }
        }

        val defenderId = state.defenderId
        if (defenderId != null && state.unbeatenPairs.isNotEmpty()) {
            val defender = state.player(defenderId)
            if (defender != null && controllable(defender.isBot)) {
                return chooseDefenseOrTake(state, defenderId)
            }
            if (!controlAllPlayers) {
                // Human defending — only parallel throws (already tried) remain for bots.
                return null
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

        // After attacker «Бито»: helpers throw if possible, else confirm.
        if (state.attackerBitoDeclared && state.allBeaten) {
            chooseThrowIn(state, { controllable(it) }, includeAttacker = false)?.let { return it }
            for (helperId in state.helperThrowerIds()) {
                if (helperId in state.passedPlayerIds) continue
                val helper = state.player(helperId) ?: continue
                if (!controllable(helper.isBot)) continue
                if (state.permissionsFor(helperId).canPass) {
                    return Action.Pass(helperId)
                }
            }
        }

        // All beaten, attacker not yet declared: helpers may still throw.
        if (state.allBeaten && !state.attackerBitoDeclared) {
            chooseThrowIn(state, { controllable(it) }, includeAttacker = false)?.let { return it }
        }

        if (!controlAllPlayers) {
            val actorId = state.currentPlayerId ?: return null
            val actor = state.player(actorId) ?: return null
            if (!controllable(actor.isBot)) return null
            return chooseActionForActor(state, actorId)
        }

        if (state.tablePairs.isEmpty() && attackerId != null) {
            val attacker = state.player(attackerId) ?: return null
            if (!controllable(attacker.isBot)) return null
            val card = attacker.hand.minWithOrNull(lowestFirst(state.trumpSuit)) ?: return null
            return Action.PlayCard(attackerId, card, targetPairId = null)
        }

        return null
    }

    private fun chooseDefenseOrTake(state: GameState, defenderId: String): Action {
        val trump = state.trumpSuit ?: return Action.Pass(defenderId)
        val unbeaten = state.unbeatenPairs.first()
        val beating = state.player(defenderId)?.hand
            ?.filter { Rules.beats(it, unbeaten.attack, trump) }
            ?.minWithOrNull(lowestFirst(trump))
        return if (beating != null) {
            Action.PlayCard(defenderId, beating, unbeaten.id)
        } else {
            Action.Pass(defenderId)
        }
    }

    private fun chooseThrowIn(
        state: GameState,
        controllable: (Boolean) -> Boolean,
        includeAttacker: Boolean,
    ): Action? {
        if (state.tablePairs.isEmpty() || !state.canAddMoreAttacks()) return null
        val candidates = state.players.filter { player ->
            controllable(player.isBot) &&
                player.id != state.defenderId &&
                player.id !in state.passedPlayerIds &&
                (includeAttacker || player.id != state.attackerId) &&
                !(state.attackerBitoDeclared && player.id == state.attackerId) &&
                player.hand.isNotEmpty()
        }
        for (player in candidates) {
            val throwCard = player.hand
                .filter { Rules.canThrow(it, state.tableRanks) }
                .minWithOrNull(lowestFirst(state.trumpSuit))
            if (throwCard != null) {
                return Action.AddCard(player.id, throwCard)
            }
        }
        return null
    }

    private fun chooseActionForActor(state: GameState, actorId: String): Action? {
        val perms = state.permissionsFor(actorId)
        val attackerId = state.attackerId

        if (actorId == state.defenderId && state.unbeatenPairs.isNotEmpty()) {
            return chooseDefenseOrTake(state, actorId)
        }

        if (perms.canBito) {
            return Action.Bito(actorId)
        }

        if (perms.canPass) {
            return Action.Pass(actorId)
        }

        if (state.tablePairs.isEmpty() && actorId == attackerId) {
            val card = state.player(actorId)?.hand
                ?.minWithOrNull(lowestFirst(state.trumpSuit))
                ?: return null
            return Action.PlayCard(actorId, card, targetPairId = null)
        }

        return null
    }

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
