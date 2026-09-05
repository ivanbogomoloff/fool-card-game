package com.example.foolcardgame.domain.bot

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.Player
import com.example.foolcardgame.domain.model.PlayerStatus
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import com.example.foolcardgame.domain.model.TablePair
import com.example.foolcardgame.domain.engine.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotAITest {

    private val botAI = BotAI()

    @Test
    fun chooseAction_defense_picksLegalBeatingCard() {
        val attack = Card(Suit.SPADES, Rank.SEVEN)
        val legal = Card(Suit.SPADES, Rank.TEN)
        val state = baseState(
            defenderHand = listOf(legal, Card(Suit.CLUBS, Rank.SIX)),
            tablePairs = listOf(TablePair(id = 1, attack = attack)),
            currentPlayerId = "bot-1",
        )

        val action = botAI.chooseAction(state)
        assertTrue(action is BotAI.Action.PlayCard)
        val play = action as BotAI.Action.PlayCard
        assertTrue(Rules.beats(play.card, attack, Suit.HEARTS))
        assertTrue(play.targetPairId == 1)
    }

    @Test
    fun chooseAction_attack_picksCardFromHand() {
        val card = Card(Suit.CLUBS, Rank.SIX)
        val state = baseState(
            attackerHand = listOf(card, Card(Suit.HEARTS, Rank.ACE)),
            defenderHand = listOf(Card(Suit.SPADES, Rank.NINE)),
            tablePairs = emptyList(),
            currentPlayerId = "bot-2",
            attackerId = "bot-2",
            defenderId = "bot-1",
        )

        val action = botAI.chooseAction(state)
        assertTrue(action is BotAI.Action.PlayCard)
        val play = action as BotAI.Action.PlayCard
        assertTrue(play.card in state.player("bot-2")!!.hand)
        assertTrue(play.targetPairId == null)
    }

    @Test
    fun chooseAction_takes_whenCannotBeat() {
        val state = baseState(
            defenderHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
            tablePairs = listOf(TablePair(id = 1, attack = Card(Suit.SPADES, Rank.ACE))),
            currentPlayerId = "bot-1",
        )
        val action = botAI.chooseAction(state)
        assertTrue(action is BotAI.Action.Pass)
    }

    @Test
    fun chooseAction_neverNullInSimpleDefenseSpot() {
        val state = baseState(
            defenderHand = listOf(Card(Suit.SPADES, Rank.KING)),
            tablePairs = listOf(TablePair(id = 1, attack = Card(Suit.SPADES, Rank.SEVEN))),
        )
        assertNotNull(botAI.chooseAction(state))
    }

    @Test
    fun chooseAction_helperThrows_midDefense_whenNotCurrent() {
        val throwCard = Card(Suit.CLUBS, Rank.SEVEN)
        val state = baseState(
            attackerHand = listOf(Card(Suit.DIAMONDS, Rank.SIX)),
            defenderHand = listOf(Card(Suit.SPADES, Rank.ACE)),
            helperHand = listOf(throwCard),
            tablePairs = listOf(TablePair(id = 1, attack = Card(Suit.SPADES, Rank.SEVEN))),
            currentPlayerId = "bot-1",
            defenderHandSizeAtRoundStart = 3,
        )
        val action = botAI.chooseAction(state)
        assertTrue(action is BotAI.Action.AddCard)
        val add = action as BotAI.Action.AddCard
        assertEquals("bot-2", add.playerId)
        assertEquals(throwCard, add.card)
    }

    @Test
    fun chooseAction_attackerBito_whenAllBeaten() {
        val state = baseState(
            attackerHand = listOf(Card(Suit.DIAMONDS, Rank.SIX)),
            defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
            helperHand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
            tablePairs = listOf(
                TablePair(
                    id = 1,
                    attack = Card(Suit.SPADES, Rank.SEVEN),
                    defense = Card(Suit.SPADES, Rank.TEN),
                ),
            ),
            currentPlayerId = "local",
            attackerId = "local",
            defenderId = "bot-1",
            defenderHandSizeAtRoundStart = 3,
        )
        // controlAllPlayers so local (human) can be driven
        val action = botAI.chooseAction(state, controlAllPlayers = true)
        assertTrue(action is BotAI.Action.Bito)
    }

    private fun baseState(
        attackerHand: List<Card> = listOf(Card(Suit.DIAMONDS, Rank.SIX)),
        defenderHand: List<Card>,
        tablePairs: List<TablePair>,
        currentPlayerId: String = "bot-1",
        attackerId: String = "local",
        defenderId: String = "bot-1",
        helperHand: List<Card> = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
        defenderHandSizeAtRoundStart: Int = defenderHand.size,
    ): GameState {
        fun handFor(id: String): List<Card> = when (id) {
            attackerId -> attackerHand
            defenderId -> defenderHand
            else -> helperHand
        }
        return GameState(
        sessionId = "bot-test",
        phase = GamePhase.IN_PROGRESS,
        players = listOf(
            Player(
                id = "local",
                displayName = "Вы",
                avatarId = 0,
                isBot = false,
                hand = handFor("local"),
                isReady = true,
                status = PlayerStatus.PLAYING,
            ),
            Player(
                id = "bot-1",
                displayName = "Бот 1",
                avatarId = 1,
                isBot = true,
                hand = handFor("bot-1"),
                isReady = true,
                status = PlayerStatus.PLAYING,
            ),
            Player(
                id = "bot-2",
                displayName = "Бот 2",
                avatarId = 2,
                isBot = true,
                hand = handFor("bot-2"),
                isReady = true,
                status = PlayerStatus.PLAYING,
            ),
        ),
        deck = listOf(Card(Suit.HEARTS, Rank.ACE)),
        trumpCard = Card(Suit.HEARTS, Rank.ACE),
        trumpSuit = Suit.HEARTS,
        tablePairs = tablePairs,
        attackerId = attackerId,
        defenderId = defenderId,
        currentPlayerId = currentPlayerId,
        defenderHandSizeAtRoundStart = defenderHandSizeAtRoundStart,
    )
    }
}
