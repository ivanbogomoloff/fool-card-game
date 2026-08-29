package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.GamePhaseDto
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugGameClientTest {

    @Test
    fun observeState_firstEmissionMatchesCurrentState() = runTest {
        val client = DebugGameClient(initialScenario = DebugScenario.LOBBY_WAITING)

        val emitted = client.observeState(
            sessionId = MockGameStates.DEBUG_SESSION_ID,
            pollIntervalMs = 100,
        ).first()

        assertEquals(client.currentState(), emitted)
        assertEquals(GamePhaseDto.LOBBY_WAITING, emitted.phase)
        assertTrue(emitted.canReady)
    }

    @Test
    fun ready_marksLocalPlayerReady() = runTest {
        val client = DebugGameClient(initialScenario = DebugScenario.LOBBY_WAITING)

        client.ready(MockGameStates.DEBUG_SESSION_ID)

        val state = client.getState(MockGameStates.DEBUG_SESSION_ID)
        val localPlayer = state.players.first { it.id == MockGameStates.LOCAL_PLAYER_ID }
        assertTrue(localPlayer.isReady)
    }

    @Test
    fun setScenario_updatesState() {
        val client = DebugGameClient(initialScenario = DebugScenario.LOBBY_WAITING)
        client.setScenario(DebugScenario.IN_PROGRESS)

        val state = client.currentState()
        assertEquals(GamePhaseDto.IN_PROGRESS, state.phase)
        assertEquals(12, state.localHand.size)
    }

    @Test
    fun clearTable_removesPairs_keepsDeckAndHand() {
        val client = DebugGameClient(initialScenario = DebugScenario.IN_PROGRESS)
        val before = client.currentState()

        client.clearTable()

        val after = client.currentState()
        assertTrue(after.tablePairs.isEmpty())
        assertEquals(before.deckCount, after.deckCount)
        assertEquals(before.trump, after.trump)
        assertEquals(before.localHand, after.localHand)
    }

    @Test
    fun bito_clearsTablePairs() = runTest {
        val client = DebugGameClient(initialScenario = DebugScenario.IN_PROGRESS)
        assertTrue(client.currentState().tablePairs.isNotEmpty())

        client.bito(MockGameStates.DEBUG_SESSION_ID)

        assertTrue(client.currentState().tablePairs.isEmpty())
    }

    @Test
    fun playCard_attack_movesCardFromHandToTable() = runTest {
        val client = DebugGameClient(initialScenario = DebugScenario.IN_PROGRESS)
        client.clearTable()
        val card = Card(Suit.HEARTS, Rank.SIX)

        client.playCard(MockGameStates.DEBUG_SESSION_ID, card, targetPairId = null)

        val state = client.currentState()
        assertTrue(state.localHand.none { it.suit.name == "HEARTS" && it.rank.name == "SIX" })
        assertEquals(1, state.tablePairs.size)
        assertEquals("HEARTS", state.tablePairs.first().attack.suit.name)
        assertEquals(11, state.localHand.size)
    }

    @Test
    fun playCard_defend_fillsDefenseSlot() = runTest {
        val client = DebugGameClient(initialScenario = DebugScenario.IN_PROGRESS)
        val undefended = client.currentState().tablePairs.first { it.defense == null }
        val card = Card(Suit.CLUBS, Rank.ACE)

        client.playCard(MockGameStates.DEBUG_SESSION_ID, card, targetPairId = undefended.id)

        val pair = client.currentState().tablePairs.first { it.id == undefended.id }
        assertEquals("CLUBS", pair.defense?.suit?.name)
        assertEquals("ACE", pair.defense?.rank?.name)
    }
}
