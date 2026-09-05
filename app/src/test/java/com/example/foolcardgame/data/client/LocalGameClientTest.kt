package com.example.foolcardgame.data.client

import app.cash.turbine.test
import com.example.foolcardgame.data.api.dto.toDomain
import com.example.foolcardgame.domain.engine.GameEngine
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.Player
import com.example.foolcardgame.domain.model.PlayerStatus
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import com.example.foolcardgame.domain.model.TablePair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalGameClientTest {

    private fun client(
        dispatcher: CoroutineDispatcher,
        connectDelay: LongRange = 0L..0L,
        readyDelay: LongRange = 0L..0L,
        engine: GameEngine = GameEngine(),
    ) = LocalGameClient(
        engine = engine,
        botConnectDelayRange = connectDelay,
        botReadyDelayRange = readyDelay,
        schedulerDispatcher = dispatcher,
    )

    @Test
    fun createSession_botsStartDisconnected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(dispatcher)
        val sessionId = client.createSession(GameConfig(botCount = 2, seed = 42))
        val state = client.getState(sessionId)
        assertTrue(state.players.filter { it.id != state.localPlayerId }.all { !it.isConnected })
        assertEquals(GamePhase.LOBBY_WAITING.name, state.phase.name)
        assertEquals(6, state.localHand.size)
        assertTrue(state.deckCount > 0)
        assertTrue(state.trump != null)
        client.leaveSession(sessionId)
    }

    @Test
    fun lobby_connectThenReady_afterDelays() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(
            dispatcher = dispatcher,
            connectDelay = 100L..100L,
            readyDelay = 100L..100L,
        )
        val sessionId = client.createSession(GameConfig(botCount = 1, seed = 42))
        assertFalse(client.getState(sessionId).players.first { it.id == "bot-1" }.isConnected)

        advanceTimeBy(100)
        runCurrent()
        assertTrue(client.getState(sessionId).players.first { it.id == "bot-1" }.isConnected)
        assertFalse(client.getState(sessionId).players.first { it.id == "bot-1" }.isReady)

        client.ready(sessionId)
        advanceTimeBy(100)
        runCurrent()
        val state = client.getState(sessionId)
        assertEquals(GamePhase.IN_PROGRESS.name, state.phase.name)
        client.leaveSession(sessionId)
    }

    @Test
    fun playCard_doesNotAdvanceBotsSynchronously() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(dispatcher)
        val sessionId = client.createSession(
            GameConfig(
                botCount = 1,
                seed = 42,
                botThinkMinMs = 5_000,
                botThinkMaxMs = 5_000,
            ),
        )
        advanceTimeBy(1)
        runCurrent()
        client.ready(sessionId)
        advanceTimeBy(1)
        runCurrent()

        var state = client.getState(sessionId)
        assertEquals(GamePhase.IN_PROGRESS.name, state.phase.name)

        if (state.currentPlayerId == state.localPlayerId && state.localHand.isNotEmpty()) {
            val card = state.localHand.first().toDomain()
            val tickBefore = state.serverTick
            client.playCard(sessionId, card, targetPairId = null)
            state = client.getState(sessionId)
            assertTrue(state.serverTick != null)
            assertTrue((state.serverTick ?: 0) >= (tickBefore ?: 0))
            if (state.currentPlayerId == "bot-1") {
                assertTrue(state.tablePairs.all { it.defense == null })
            }
        }
        client.leaveSession(sessionId)
    }

    @Test
    fun playCard_botThinkDelayRespectsGameConfigMax() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(dispatcher)
        val sessionId = client.createSession(
            GameConfig(
                botCount = 1,
                seed = 42,
                botThinkMinMs = 3_000,
                botThinkMaxMs = 3_000,
            ),
        )
        advanceTimeBy(1)
        runCurrent()
        client.ready(sessionId)
        advanceTimeBy(1)
        runCurrent()

        var state = client.getState(sessionId)
        if (state.currentPlayerId != state.localPlayerId || state.localHand.isEmpty()) {
            client.leaveSession(sessionId)
            return@runTest
        }

        val card = state.localHand.first().toDomain()
        client.playCard(sessionId, card, targetPairId = null)
        state = client.getState(sessionId)
        if (state.currentPlayerId != "bot-1") {
            client.leaveSession(sessionId)
            return@runTest
        }

        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(client.getState(sessionId).tablePairs.all { it.defense == null })

        advanceTimeBy(1)
        runCurrent()
        client.leaveSession(sessionId)
    }

    @Test
    fun observeState_emitsAfterAction() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(dispatcher)
        val sessionId = client.createSession(
            GameConfig(
                botCount = 1,
                seed = 11,
                botThinkMinMs = 1_000,
                botThinkMaxMs = 1_000,
            ),
        )
        advanceTimeBy(1)
        runCurrent()

        client.observeState(sessionId, pollIntervalMs = 5_000).test {
            awaitItem()
            client.ready(sessionId)
            val afterReady = awaitItem()
            assertTrue(afterReady.players.any { it.isReady })
            cancelAndIgnoreRemainingEvents()
        }
        client.leaveSession(sessionId)
    }

    @Test
    fun observeState_ticksCallOnTick() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(dispatcher)
        val sessionId = client.createSession(
            GameConfig(
                botCount = 1,
                seed = 5,
                botThinkMinMs = 1_000,
                botThinkMaxMs = 1_000,
            ),
        )
        advanceTimeBy(1)
        runCurrent()
        client.ready(sessionId)
        advanceTimeBy(1)
        runCurrent()

        client.observeState(sessionId, pollIntervalMs = 100).test {
            awaitItem()
            advanceTimeBy(100)
            runCurrent()
            val tick = awaitItem()
            assertTrue(tick.serverTick != null && tick.serverTick!! > 0)
            cancelAndIgnoreRemainingEvents()
        }
        client.leaveSession(sessionId)
    }

    @Test
    fun setPaused_doesNotHangAndAllowsResume() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = client(
            dispatcher = dispatcher,
            connectDelay = 50L..50L,
            readyDelay = 50L..50L,
        )
        val sessionId = client.createSession(GameConfig(botCount = 1, seed = 7))
        client.setPaused(true)
        advanceTimeBy(300)
        runCurrent()
        assertFalse(client.getState(sessionId).players.first { it.id == "bot-1" }.isConnected)

        client.setPaused(false)
        advanceTimeBy(300)
        runCurrent()
        assertTrue(client.getState(sessionId).players.first { it.id == "bot-1" }.isConnected)
        client.leaveSession(sessionId)
    }

    @Test
    fun humanFullDefend_holdsBeforeBotThinkThenBito() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = GameEngine()
        val client = client(dispatcher = dispatcher, engine = engine)
        val sessionId = client.createSession(
            GameConfig(
                botCount = 1,
                seed = 42,
                botThinkMinMs = 1_000,
                botThinkMaxMs = 1_000,
            ),
        )
        advanceTimeBy(1)
        runCurrent()

        val defense = Card(Suit.SPADES, Rank.TEN)
        val attack = Card(Suit.SPADES, Rank.SEVEN)
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
                        hand = listOf(defense, Card(Suit.CLUBS, Rank.NINE)),
                        isReady = true,
                        status = PlayerStatus.PLAYING,
                    ),
                    Player(
                        id = "bot-1",
                        displayName = "Бот 1",
                        avatarId = 1,
                        isBot = true,
                        // Empty hand → no parallel throw-in while human still defends
                        hand = emptyList(),
                        isReady = true,
                        isConnected = true,
                        status = PlayerStatus.PLAYING,
                    ),
                ),
                deck = listOf(
                    Card(Suit.HEARTS, Rank.ACE),
                    Card(Suit.HEARTS, Rank.KING),
                    Card(Suit.DIAMONDS, Rank.QUEEN),
                    Card(Suit.CLUBS, Rank.JACK),
                    Card(Suit.SPADES, Rank.EIGHT),
                    Card(Suit.DIAMONDS, Rank.SEVEN),
                    Card(Suit.CLUBS, Rank.SIX),
                ),
                trumpCard = Card(Suit.CLUBS, Rank.SIX),
                trumpSuit = Suit.CLUBS,
                tablePairs = listOf(TablePair(id = 1, attack = attack)),
                attackerId = "bot-1",
                defenderId = "local",
                currentPlayerId = "local",
                defenderHandSizeAtRoundStart = 1,
            ),
        )

        assertTrue(client.playCard(sessionId, defense, targetPairId = 1).isSuccess)
        assertEquals(1, client.getState(sessionId).tablePairs.size)
        assertEquals("bot-1", client.getState(sessionId).currentPlayerId)

        // Poll (~150) + hold (1000) + think (1000). Without hold, bito would land ~1150ms.
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, client.getState(sessionId).tablePairs.size)

        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(client.getState(sessionId).tablePairs.isEmpty())
        client.leaveSession(sessionId)
    }
}
