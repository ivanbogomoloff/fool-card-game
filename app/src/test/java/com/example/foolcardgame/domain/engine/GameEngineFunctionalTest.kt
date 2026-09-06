package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.bot.BotAI
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Функциональный smoke: полная партия с ≥3 игроками идёт по правилам до FINISHED.
 */
class GameEngineFunctionalTest {

    @Test
    fun fullGame_threePlayers_reachesFinished_withoutIllegalStates() {
        val engine = GameEngine(botAI = BotAI(), controlAllPlayers = true)
        val sessionId = engine.createSession(GameConfig(botCount = 2, seed = 2024))

        engine.ready(sessionId, GameConfig.DEFAULT_HUMAN_ID)
        engine.getState(sessionId).players.filter { it.isBot }.forEach { bot ->
            engine.setConnected(sessionId, bot.id, connected = true)
            engine.ready(sessionId, bot.id)
        }
        assertEquals(GamePhase.IN_PROGRESS, engine.getState(sessionId).phase)

        var steps = 0
        val maxSteps = 5_000
        while (engine.getState(sessionId).phase == GamePhase.IN_PROGRESS && steps < maxSteps) {
            val before = engine.getState(sessionId)
            assertInvariants(before)
            engine.advanceUntilHumanOrFinished(sessionId, maxSteps = 1)
            val after = engine.getState(sessionId)
            if (after.tick == before.tick && after.phase == GamePhase.IN_PROGRESS) {
                val attackerId = after.attackerId
                if (attackerId != null) {
                    engine.bito(sessionId, attackerId)
                }
                val throwers = after.players.filter { it.id != after.defenderId }
                for (p in throwers) {
                    engine.pass(sessionId, p.id)
                }
            }
            steps++
        }

        val finalState = engine.getState(sessionId)
        assertEquals(
            "Game did not finish in $maxSteps steps; phase=${finalState.phase}",
            GamePhase.FINISHED,
            finalState.phase,
        )
        val loserId = finalState.loserId
        if (loserId != null) {
            assertTrue(finalState.players.count { it.id == loserId } == 1)
            val loser = finalState.player(loserId)!!
            assertTrue(loser.hand.isNotEmpty())
        } else {
            // Ничья: ни у кого не осталось карт.
            assertTrue(finalState.players.all { it.hand.isEmpty() })
            assertTrue(finalState.winnerIds.isEmpty())
        }
    }

    private fun assertInvariants(state: com.example.foolcardgame.domain.model.GameState) {
        assertTrue(state.players.size >= 3)
        val allCards = state.players.flatMap { it.hand } +
            state.tablePairs.flatMap { listOfNotNull(it.attack, it.defense) } +
            state.deck +
            state.discardPile
        assertEquals("Card conservation broken", Deck.SIZE, allCards.size)
        assertEquals(Deck.SIZE, allCards.toSet().size)
        state.tablePairs.forEach { pair ->
            pair.defense?.let { defense ->
                val trump = state.trumpSuit
                assertTrue(trump != null)
                assertTrue(Rules.beats(defense, pair.attack, trump!!))
            }
        }
        if (state.tablePairs.isNotEmpty()) {
            assertTrue(
                state.tablePairs.size <= Rules.maxAttackCards(state.defenderHandSizeAtRoundStart),
            )
        }
    }
}
