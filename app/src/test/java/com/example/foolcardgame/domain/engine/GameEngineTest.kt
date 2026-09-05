package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameActionKind
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.Player
import com.example.foolcardgame.domain.model.PlayerStatus
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import com.example.foolcardgame.domain.model.TablePair
import com.example.foolcardgame.domain.model.permissionsFor
import com.example.foolcardgame.domain.model.RoundEventKind
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
        val lobby = engine.getState(sessionId)

        assertEquals(GamePhase.LOBBY_WAITING, lobby.phase)
        lobby.players.forEach { assertEquals(6, it.hand.size) }
        assertEquals(36 - 3 * 6, lobby.deck.size)
        assertNotNull(lobby.trumpCard)
        assertEquals(lobby.deck.last(), lobby.trumpCard)
        assertEquals(lobby.trumpCard?.suit, lobby.trumpSuit)
        assertNull(lobby.attackerId)

        readyAll(sessionId)
        val state = engine.getState(sessionId)
        assertEquals(GamePhase.IN_PROGRESS, state.phase)
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
        val state = engine.getState(sessionId)
        // Limit was 1 attack → throwing closed, but round waits for attacker «Бито»
        assertEquals(1, state.tablePairs.size)
        assertEquals(defense, state.tablePairs.single().defense)
        assertEquals("local", state.currentPlayerId)
        assertTrue(state.permissionsFor("local").canBito)
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
                passedPlayerIds = setOf("bot-2"),
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
                currentPlayerId = "local",
                passedPlayerIds = setOf("bot-2"),
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
                currentPlayerId = "local",
                passedPlayerIds = setOf("bot-2"),
            ),
        )

        assertTrue(engine.addCard(sessionId, "local", throwCard).isFailure)
    }

    @Test
    fun pass_afterDefense_withNoMoreThrows_endsRoundAsBito() {
        val sessionId = "s-pass-bito-2p"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "local",
                helperHand = emptyList(),
                defenderHandSizeAtRoundStart = 3,
            ),
        )

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val state = engine.getState(sessionId)
        assertTrue(state.tablePairs.isEmpty())
        // After bito, previous defender (bot-1) attacks; bot-2 empty → local defends
        assertEquals("bot-1", state.attackerId)
        assertEquals("local", state.defenderId)
    }

    @Test
    fun addCard_helperCanThrow_ontoDefender() {
        val sessionId = "s-helper-throw"
        val throwCard = Card(Suit.CLUBS, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.DIAMONDS, Rank.SIX)),
                defenderHand = listOf(
                    Card(Suit.SPADES, Rank.ACE),
                    Card(Suit.HEARTS, Rank.SIX),
                ),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "bot-2",
                helperHand = listOf(throwCard, Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 2,
            ),
        )

        assertEquals("bot-1", engine.getState(sessionId).defenderId)
        assertTrue(engine.addCard(sessionId, "bot-2", throwCard).isSuccess)
        val state = engine.getState(sessionId)
        assertEquals(2, state.tablePairs.size)
        assertEquals("bot-1", state.defenderId)
        assertEquals("bot-1", state.currentPlayerId) // defender must beat the throw
    }

    @Test
    fun pass_allThrowers_endsRoundAsBito() {
        val sessionId = "s-pass-all"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "local",
                helperHand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 3,
            ),
        )

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        var state = engine.getState(sessionId)
        assertEquals(1, state.tablePairs.size) // attacker declared; helper must confirm
        assertTrue(state.attackerBitoDeclared)
        assertTrue("bot-2" !in state.passedPlayerIds)

        assertTrue(engine.pass(sessionId, "bot-2").isSuccess)
        state = engine.getState(sessionId)
        assertTrue(state.tablePairs.isEmpty())
        assertEquals("bot-1", state.attackerId) // previous defender attacks after bito
        assertEquals("bot-2", state.defenderId)
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
        // Taker skips; next after bot-1 is bot-2
        assertEquals("bot-2", state.attackerId)
        assertEquals("local", state.defenderId)
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
        assertEquals("bot-1", state.attackerId) // previous defender attacks after bito
        assertEquals("bot-2", state.defenderId)
    }

    @Test
    fun twoPlayers_afterBito_previousDefenderAttacks() {
        val sessionId = "s-2p-bito"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "local",
                helperHand = emptyList(),
                defenderHandSizeAtRoundStart = 3,
            ),
        )

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val state = engine.getState(sessionId)
        assertEquals("bot-1", state.attackerId)
        assertEquals("local", state.defenderId)
        assertEquals("bot-1", state.currentPlayerId)
    }

    @Test
    fun twoPlayers_afterTake_takerSkips_localAttacksAgain() {
        val sessionId = "s-2p-take"
        val attack = Card(Suit.SPADES, Rank.ACE)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.CLUBS, Rank.SEVEN)),
                tablePairs = listOf(TablePair(id = 1, attack = attack)),
                currentPlayerId = "bot-1",
                helperHand = emptyList(),
            ),
        )

        assertTrue(engine.pass(sessionId, "bot-1").isSuccess)
        val state = engine.getState(sessionId)
        assertTrue(state.player("bot-1")!!.hand.contains(attack))
        // bot-1 took and skips; only local left with cards in circle → local attacks
        assertEquals("local", state.attackerId)
        assertEquals("bot-1", state.defenderId)
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
    fun shiftTurnDeadlines_extendsDeadlineWithoutSkipping() {
        var now = 1_000L
        val timedEngine = GameEngine(clock = { now })
        val sessionId = "s-shift-deadline"
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

        timedEngine.shiftTurnDeadlines(sessionId, deltaMs = 5_000L)
        now = 1_000L + GameConfig.TURN_TIMEOUT_MS + 1_000L
        timedEngine.onTick(sessionId)
        assertEquals("local", timedEngine.getState(sessionId).currentPlayerId)

        now = 1_000L + GameConfig.TURN_TIMEOUT_MS + 5_000L
        timedEngine.onTick(sessionId)
        assertEquals("bot-1", timedEngine.getState(sessionId).currentPlayerId)
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
                currentPlayerId = "bot-2",
                defenderHandSizeAtRoundStart = 3,
                attackerBitoDeclared = true,
            ),
        )
        assertTrue(engine.skipTurn(sessionId, "bot-2").isSuccess)
        // Sole helper timed out → round closes as bito.
        assertTrue(engine.getState(sessionId).tablePairs.isEmpty())
        assertFalse(engine.getState(sessionId).attackerBitoDeclared)
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
        assertTrue(ended.tablePairs.isNotEmpty())
        assertEquals(Rank.TEN, ended.tablePairs.first().defense?.rank)
    }

    @Test
    fun createSession_differentSeeds_produceDifferentHands() {
        val sessionA = engine.createSession(GameConfig(botCount = 1, seed = 1))
        val sessionB = engine.createSession(GameConfig(botCount = 1, seed = 2))
        val handA = engine.getState(sessionA).player("local")!!.hand
        val handB = engine.getState(sessionB).player("local")!!.hand
        assertFalse(handA == handB)
    }

    @Test
    fun playCard_attack_emitsAttackActionEvent() {
        val sessionId = "s-attack-event"
        val attackCard = Card(Suit.HEARTS, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(attackCard, Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.SIX)),
            ),
        )

        assertTrue(engine.playCard(sessionId, "local", attackCard, targetPairId = null).isSuccess)
        val event = engine.getState(sessionId).lastActionEvent
        assertEquals(GameActionKind.ATTACK, event?.kind)
        assertEquals("local", event?.playerId)
    }

    @Test
    fun playCard_defense_emitsDefendActionEvent() {
        val sessionId = "s-defend-event"
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

        assertTrue(engine.playCard(sessionId, "bot-1", defense, targetPairId = 1).isSuccess)
        val event = engine.getState(sessionId).lastActionEvent
        assertEquals(GameActionKind.DEFEND, event?.kind)
        assertEquals("bot-1", event?.playerId)
    }

    @Test
    fun addCard_emitsThrowInActionEvent() {
        val sessionId = "s-throw-event"
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
                passedPlayerIds = setOf("bot-2"),
            ),
        )

        assertTrue(engine.addCard(sessionId, "local", throwCard).isSuccess)
        val event = engine.getState(sessionId).lastActionEvent
        assertEquals(GameActionKind.THROW_IN, event?.kind)
        assertEquals("local", event?.playerId)
    }

    @Test
    fun pass_emitsPassActionEvent() {
        val sessionId = "s-pass-event"
        // 4 players so first helper confirm does not close the round.
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
                        hand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                        isReady = true,
                        status = PlayerStatus.PLAYING,
                    ),
                    Player(
                        id = "bot-1",
                        displayName = "Бот 1",
                        avatarId = 1,
                        isBot = true,
                        hand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
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
                    Player(
                        id = "bot-3",
                        displayName = "Бот 3",
                        avatarId = 3,
                        isBot = true,
                        hand = listOf(Card(Suit.HEARTS, Rank.NINE)),
                        isReady = true,
                        status = PlayerStatus.PLAYING,
                    ),
                ),
                deck = listOf(Card(Suit.HEARTS, Rank.ACE)),
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
                currentPlayerId = "bot-2",
                attackerBitoDeclared = true,
                defenderHandSizeAtRoundStart = 3,
            ),
        )

        assertTrue(engine.pass(sessionId, "bot-2").isSuccess)
        val event = engine.getState(sessionId).lastActionEvent
        assertEquals(GameActionKind.PASS, event?.kind)
        assertEquals("bot-2", event?.playerId)
        assertEquals(1, engine.getState(sessionId).tablePairs.size)
    }

    @Test
    fun defenderTake_emitsTookRoundEvent() {
        val sessionId = "s-take-event"
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
        val event = engine.getState(sessionId).lastRoundEvent
        assertEquals(RoundEventKind.TOOK, event?.kind)
        assertEquals("bot-1", event?.playerId)
        val action = engine.getState(sessionId).lastActionEvent
        assertEquals(GameActionKind.TOOK, action?.kind)
        assertEquals("bot-1", action?.playerId)
    }

    @Test
    fun bito_emitsBitoRoundEvent() {
        val sessionId = "s-bito-event"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                helperHand = emptyList(),
                defenderHandSizeAtRoundStart = 3,
            ),
        )
        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val event = engine.getState(sessionId).lastRoundEvent
        assertEquals(RoundEventKind.BITO, event?.kind)
        assertEquals("local", event?.playerId)
        val action = engine.getState(sessionId).lastActionEvent
        assertEquals(GameActionKind.BITO, action?.kind)
        assertEquals("local", action?.playerId)
    }

    @Test
    fun attacker_allBeaten_canBito_beforeHelpersPass() {
        val sessionId = "s-perm-attacker"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                helperHand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 3,
                currentPlayerId = "local",
            ),
        )
        val perms = engine.getState(sessionId).permissionsFor("local")
        assertTrue(perms.canBito)
        assertFalse(perms.canPass)

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val afterBito = engine.getState(sessionId)
        assertTrue(afterBito.attackerBitoDeclared)
        assertEquals(1, afterBito.tablePairs.size)
        assertFalse(afterBito.permissionsFor("local").canBito)
        assertTrue(afterBito.permissionsFor("bot-2").canPass)
    }

    @Test
    fun helper_allBeaten_canPass_onlyAfterAttackerBito() {
        val sessionId = "s-perm-helper"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "local",
                helperHand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 3,
            ),
        )
        assertFalse(engine.getState(sessionId).permissionsFor("bot-2").canPass)

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        val perms = engine.getState(sessionId).permissionsFor("bot-2")
        assertTrue(perms.canPass)
        assertFalse(perms.canBito)
    }

    @Test
    fun bito_declaresFirst_thenHelpersConfirm() {
        val sessionId = "s-bito-first"
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                helperHand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 3,
                currentPlayerId = "local",
            ),
        )

        assertTrue(engine.bito(sessionId, "local").isSuccess)
        assertTrue(engine.getState(sessionId).attackerBitoDeclared)
        assertEquals(1, engine.getState(sessionId).tablePairs.size)
        assertTrue(engine.pass(sessionId, "bot-2").isSuccess)
        assertTrue(engine.getState(sessionId).tablePairs.isEmpty())
    }

    @Test
    fun throw_afterAttackerBito_resetsDeclared() {
        val sessionId = "s-throw-reset-bito"
        val throwCard = Card(Suit.CLUBS, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.DIAMONDS, Rank.SIX)),
                defenderHand = listOf(
                    Card(Suit.SPADES, Rank.ACE),
                    Card(Suit.HEARTS, Rank.SIX),
                ),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                        defense = Card(Suit.SPADES, Rank.TEN),
                    ),
                ),
                currentPlayerId = "bot-2",
                helperHand = listOf(throwCard, Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 2,
                attackerBitoDeclared = true,
            ),
        )

        assertTrue(engine.addCard(sessionId, "bot-2", throwCard).isSuccess)
        val state = engine.getState(sessionId)
        assertFalse(state.attackerBitoDeclared)
        assertEquals(2, state.tablePairs.size)
        assertEquals("bot-1", state.currentPlayerId)
    }

    @Test
    fun addCard_helperCanThrow_midDefense_withoutTurn() {
        val sessionId = "s-helper-mid"
        val throwCard = Card(Suit.CLUBS, Rank.SEVEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerHand = listOf(Card(Suit.DIAMONDS, Rank.SIX)),
                defenderHand = listOf(
                    Card(Suit.SPADES, Rank.ACE),
                    Card(Suit.HEARTS, Rank.SIX),
                ),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                    ),
                ),
                currentPlayerId = "bot-1", // defender's turn
                helperHand = listOf(throwCard, Card(Suit.DIAMONDS, Rank.NINE)),
                defenderHandSizeAtRoundStart = 2,
            ),
        )

        assertTrue(engine.addCard(sessionId, "bot-2", throwCard).isSuccess)
        assertEquals(2, engine.getState(sessionId).tablePairs.size)
        assertEquals("bot-1", engine.getState(sessionId).currentPlayerId)
    }

    @Test
    fun defend_allBeaten_passesTurnToAttackerForBito() {
        val sessionId = "s-defend-throw-turn"
        val defense = Card(Suit.SPADES, Rank.TEN)
        engine.loadStateForTest(
            inProgressState(
                sessionId = sessionId,
                attackerId = "bot-1",
                defenderId = "bot-2",
                attackerHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
                defenderHand = listOf(defense, Card(Suit.DIAMONDS, Rank.EIGHT)),
                helperHand = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
                tablePairs = listOf(
                    TablePair(
                        id = 1,
                        attack = Card(Suit.SPADES, Rank.SEVEN),
                    ),
                ),
                currentPlayerId = "bot-2",
                defenderHandSizeAtRoundStart = 2,
            ),
        )

        assertTrue(engine.playCard(sessionId, "bot-2", defense, targetPairId = 1).isSuccess)
        val state = engine.getState(sessionId)
        assertEquals("bot-1", state.currentPlayerId)
        assertTrue(state.permissionsFor("bot-1").canBito)
        assertFalse(state.permissionsFor("local").canPass)
        assertEquals(
            GameConfig.THROW_TIMEOUT_MS,
            (state.turnDeadlineAtMs ?: 0L) - (state.turnStartedAtMs ?: 0L),
        )
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
        helperHand: List<Card> = listOf(Card(Suit.DIAMONDS, Rank.NINE)),
        attackerId: String = "local",
        defenderId: String = "bot-1",
        attackerBitoDeclared: Boolean = false,
    ): GameState {
        fun handFor(playerId: String): List<Card> = when (playerId) {
            attackerId -> attackerHand
            defenderId -> defenderHand
            else -> helperHand
        }
        return GameState(
        sessionId = sessionId,
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
        deck = deck,
        trumpCard = deck.lastOrNull() ?: Card(Suit.HEARTS, Rank.ACE),
        trumpSuit = Suit.HEARTS,
        tablePairs = tablePairs,
        attackerId = attackerId,
        defenderId = defenderId,
        currentPlayerId = currentPlayerId,
        passedPlayerIds = passedPlayerIds,
        attackerBitoDeclared = attackerBitoDeclared,
        defenderHandSizeAtRoundStart = defenderHandSizeAtRoundStart,
    )
    }
}
