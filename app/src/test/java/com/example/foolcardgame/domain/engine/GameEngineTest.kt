package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.Player
import com.example.foolcardgame.domain.model.PlayerStatus
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import com.example.foolcardgame.domain.model.TablePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private val engine = GameEngine()

    @Test
    fun deal_gives6CardsEach_andTrumpFromDeckBottom() {
        val sessionId = engine.createSession(GameConfig(botCount = 2, seed = 42))
        readyAll(sessionId)
        val state = engine.getState(sessionId)

        assertEquals(GamePhase.IN_PROGRESS, state.phase)
        state.players.forEach { assertEquals(6, it.hand.size) }
        assertEquals(36 - 3 * 6, state.deck.size)
        assertNotNull(state.trumpCard)
        assertEquals(state.deck.last(), state.trumpCard)
        assertEquals(state.trumpCard?.suit, state.trumpSuit)
        assertNotNull(state.attackerId)
        assertNotNull(state.defenderId)
    }

    @Test
    fun playCard_attack_placesCardOnTable() {
        val sessionId = "s-attack"
        val attackCard = Card(Suit.SPADES, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(attackCard, Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.SIX)),
            ),
        )

        val result = engine.playCard(sessionId, "local", attackCard, targetPairId = null)
        assertTrue(result.isSuccess)
        val state = engine.getState(sessionId)
        assertEquals(1, state.tablePairs.size)
        assertEquals(attackCard, state.tablePairs.single().attack)
        assertNull(state.tablePairs.single().defense)
        assertEquals("bot-1", state.currentPlayerId)
        assertFalse(state.player("local")!!.hand.contains(attackCard))
    }

    @Test
    fun playCard_defense_beatsWithSameSuitHigher() {
        val sessionId = "s-def-suit"
        val attack = Card(Suit.SPADES, Rank.SEVEN)
        val defense = Card(Suit.SPADES, Rank.TEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(defense, Card(Suit.HEARTS, Rank.SIX)),
                tablePairs = listOf(TablePair(id = 1, attack = attack)),
                currentPlayerId = "bot-1",
            ),
        )

        val result = engine.playCard(sessionId, "bot-1", defense, targetPairId = 1)
        assertTrue(result.isSuccess)
        val pair = engine.getState(sessionId).tablePairs.single()
        assertEquals(defense, pair.defense)
        assertTrue(pair.isBeaten)
    }

    @Test
    fun playCard_defense_beatsWithTrump() {
        val sessionId = "s-def-trump"
        val attack = Card(Suit.SPADES, Rank.ACE)
        val defense = Card(Suit.HEARTS, Rank.SIX)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(defense),
                tablePairs = listOf(TablePair(id = 1, attack = attack)),
                currentPlayerId = "bot-1",
            ),
        )

        assertTrue(engine.playCard(sessionId, "bot-1", defense, targetPairId = 1).isSuccess)
        assertEquals(defense, engine.getState(sessionId).tablePairs.single().defense)
    }

    @Test
    fun addCard_throwsSameRank_whenAllowed() {
        val sessionId = "s-add-ok"
        val onTable = Card(Suit.SPADES, Rank.SEVEN)
        val throwCard = Card(Suit.CLUBS, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(throwCard, Card(Suit.DIAMONDS, Rank.SIX)),
                defenderHand = listOf(
                    Card(Suit.SPADES, Rank.ACE),
                    Card(Suit.CLUBS, Rank.ACE),
                    Card(Suit.DIAMONDS, Rank.ACE),
                ),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = onTable,
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "local",
            ),
        )

        assertTrue(engine.addCard(sessionId, "local", throwCard).isSuccess)
        assertEquals(2, engine.getState(sessionId).tablePairs.size)
    }

    @Test
    fun addCard_rejects_whenRankNotOnTable() {
        val sessionId = "s-add-bad"
        val throwCard = Card(Suit.CLUBS, Rank.ACE)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(throwCard),
                defenderHand = listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.SIX)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
            ),
        )

        assertTrue(engine.addCard(sessionId, "local", throwCard).isFailure)
    }

    @Test
    fun addCard_respectsLimit_min6_andDefenderHandCount() {
        val sessionId = "s-add-limit"
        // Defender started with 1 card → max 1 attack on table
        val throwCard = Card(Suit.CLUBS, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(throwCard),
                defenderHand = listOf(Card(Suit.SPADES, Rank.ACE)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                defenderHandSizeAtRoundStart = 1,
            ),
        )

        assertTrue(engine.addCard(sessionId, "local", throwCard).isFailure)
    }

    @Test
    fun pass_defenderTakesAll_whenCannotBeat() {
        val sessionId = "s-take"
        val attack = Card(Suit.SPADES, Rank.ACE)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.CLUBS, Rank.SEVEN)),
                tablePairs = listOf(TablePair(id = 1, attack = attack)),
                currentPlayerId = "bot-1",
            ),
        )

        assertTrue(engine.pass(sessionId, "bot-1").isSuccess)
        val state = engine.getState(sessionId)
        assertTrue(state.tablePairs.isEmpty())
        assertTrue(state.player("bot-1")!!.hand.contains(attack))
        assertEquals("bot-1", state.attackerId) // took → attacks again
    }

    @Test
    fun bito_clearsTable_andDrawsCards() {
        val sessionId = "s-bito"
        val deck = listOf(
            Card(Suit.CLUBS, Rank.SIX),
            Card(Suit.CLUBS, Rank.SEVEN),
            Card(Suit.CLUBS, Rank.EIGHT),
            Card(Suit.HEARTS, Rank.NINE), // trump at bottom
        )
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.DIAMONDS, Rank.SIX)), // 1 card → will draw
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                deck = deck,
                passedPlayerIds = setOf("local", "bot-2"),
            ),
        )

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val state = engine.getState(sessionId)
        assertTrue(state.tablePairs.isEmpty())
        assertTrue(state.player("local")!!.hand.size > 1)
        assertEquals("bot-2", state.attackerId) // слева от защитника bot-1
    }

    @Test
    fun onTick_skipsTurn_whenDeadlinePassed() {
        var now = 1_000L
        val timedEngine = GameEngine(clock = { now })
        val sessionId = "s-deadline"
        timedEngine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.SPADES, Rank.ACE)),
                tablePairs = emptyList(),
                currentPlayerId = "local",
            ).copy(
                turnStartedAtMs = 1_000L,
                turnDeadlineAtMs = 1_000L + GameConfig.TURN_TIMEOUT_MS,
            ),
        )

        now = 1_000L + GameConfig.TURN_TIMEOUT_MS
        timedEngine.onTick(sessionId)
        val state = timedEngine.getState(sessionId)
        assertEquals("bot-1", state.attackerId)
        assertEquals("bot-1", state.currentPlayerId)
    }

    @Test
    fun skipTurn_emptyTable_rotatesAttacker() {
        val sessionId = "s-skip-empty"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.SPADES, Rank.ACE)),
                tablePairs = emptyList(),
                currentPlayerId = "local",
            ),
        )
        assertTrue(engine.skipTurn(sessionId, "local").isSuccess)
        assertEquals("bot-1", engine.getState(sessionId).attackerId)
    }

    @Test
    fun skipTurn_defender_discardsTableWithoutTake() {
        val sessionId = "s-skip-def"
        val attack = Card(Suit.SPADES, Rank.ACE)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.CLUBS, Rank.SEVEN)),
                tablePairs = listOf(TablePair(id = 1, attack = attack)),
                currentPlayerId = "bot-1",
            ),
        )
        assertTrue(engine.skipTurn(sessionId, "bot-1").isSuccess)
        val state = engine.getState(sessionId)
        assertTrue(state.tablePairs.isEmpty())
        assertFalse(state.player("bot-1")!!.hand.contains(attack))
        assertTrue(state.discardPile.contains(attack))
    }

    @Test
    fun skipTurn_throwPhase_marksPassed() {
        val sessionId = "s-skip-throw"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(
                    Card(Suit.DIAMONDS, Rank.SEVEN),
                    Card(Suit.DIAMONDS, Rank.EIGHT),
                    Card(Suit.DIAMONDS, Rank.NINE),
                ),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "local",
                defenderHandSizeAtRoundStart = 3,
            ),
        )
        assertTrue(engine.skipTurn(sessionId, "local").isSuccess)
        assertTrue("local" in engine.getState(sessionId).passedPlayerIds)
    }

    @Test
    fun ready_marksPlayerReady() {
        val sessionId = engine.createSession(GameConfig(botCount = 1, seed = 3))
        assertTrue(engine.ready(sessionId, "local").isSuccess)
        assertTrue(engine.getState(sessionId).player("local")!!.isReady)
    }

    @Test
    fun gameEnds_whenDeckEmpty_andOnePlayerHasCards() {
        val sessionId = "s-end"
        engine.loadStateForTest(
            GameState(
                sessionId = sessionId,
                phase = GamePhase.IN_PROGRESS,
                players = listOf(
                    Player(
                        id = "local",
                        displayName = "Вы",
                        avatarId = 0,
                        isBot = false,
                        hand = emptyList(),
                        isReady = true,
                        status = PlayerStatus.PLAYING,
                        isFinished = true,
                    ),
                    Player(
                        id = "bot-1",
                        displayName = "Бот 1",
                        avatarId = 1,
                        isBot = true,
                        hand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                        isReady = true,
                        status = PlayerStatus.PLAYING,
                    ),
                ),
                deck = emptyList(),
                trumpCard = Card(Suit.HEARTS, Rank.ACE),
                trumpSuit = Suit.HEARTS,
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                attackerId = "local",
                defenderId = "bot-1",
                currentPlayerId = "local",
                passedPlayerIds = setOf("local"),
                defenderHandSizeAtRoundStart = 1,
            ),
        )

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val ended = engine.getState(sessionId)
        assertEquals(GamePhase.FINISHED, ended.phase)
        assertEquals("bot-1", ended.loserId)
    }

    private fun readyAll(sessionId: String) {
        engine.ready(sessionId, "local")
        engine.getState(sessionId).players.filter { it.isBot }.forEach { bot ->
            engine.setConnected(sessionId, bot.id, connected = true)
            engine.ready(sessionId, bot.id)
        }
    }

    private fun inProgressState(
        sessionId: String,
        attackerHand: List<Card>,
        defenderHand: List<Card>,
        tablePairs: List<TablePair> = emptyList(),
        currentPlayerId: String = "local",
        deck: List<Card> = listOf(Card(Suit.HEARTS, Rank.ACE)),
        passedPlayerIds: Set<String> = emptySet(),
        defenderHandSizeAtRoundStart: Int = defenderHand.size,
    ): GameState = GameState(
        sessionId = sessionId,
        phase = GamePhase.IN_PROGRESS,
        players = listOf(
            Player(
                id = "local",
                displayName = "Вы",
                avatarId = 0,
                isBot = false,
                hand = attackerHand,
                isReady = true,
                status = PlayerStatus.PLAYING,
            ),
            Player(
                id = "bot-1",
                displayName = "Бот 1",
                avatarId = 1,
                isBot = true,
                hand = defenderHand,
                isReady = true,
                status = PlayerStatus.PLAYING,
            ),
            Player(
                id = "bot-2",
                displayName = "Бот 2",
                avatarId = 2,
                isBot = true,
                hand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
                isReady = true,
                status = PlayerStatus.PLAYING,
            ),
        ),
        deck = deck,
        trumpCard = deck.lastOrNull() ?: Card(Suit.HEARTS, Rank.ACE),
        trumpSuit = Suit.HEARTS,
        tablePairs = tablePairs,
        attackerId = "local",
        defenderId = "bot-1",
        currentPlayerId = currentPlayerId,
        passedPlayerIds = passedPlayerIds,
        defenderHandSizeAtRoundStart = defenderHandSizeAtRoundStart,
    )
}
