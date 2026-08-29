package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.dto.GamePhaseDto
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
    }
}
