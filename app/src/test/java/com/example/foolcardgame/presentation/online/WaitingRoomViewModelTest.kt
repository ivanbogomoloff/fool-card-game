package com.example.foolcardgame.presentation.online

import com.example.foolcardgame.data.client.FakeOnlineBackend
import com.example.foolcardgame.data.client.FakeOnlineGameClient
import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingRoomViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observeRoom_loadsPlayers_andKickStart() = runTest {
        val backend = FakeOnlineBackend()
        val hostAuth = InMemoryAuthSessionStore()
        val guestAuth = InMemoryAuthSessionStore()
        val hostClient = FakeOnlineGameClient(authSession = hostAuth, backend = backend)
        val guestClient = FakeOnlineGameClient(authSession = guestAuth, backend = backend)
        assertTrue(hostClient.login("Хост").isSuccess)
        assertTrue(guestClient.login("Гость").isSuccess)

        val created = hostClient.createPrivateGame("Хост", 0).getOrThrow()
        val (sessionId, guestId) = guestClient.joinByCode(created.accessCode, "Гость", 1).getOrThrow()

        val vm = WaitingRoomViewModel(hostClient, sessionId, created.playerId)
        runCurrent()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isHost)
        assertEquals(2, vm.uiState.value.players.size)
        assertEquals(created.accessCode, vm.uiState.value.accessCode)

        vm.onKickClick(guestId)
        runCurrent()
        assertEquals(1, vm.uiState.value.players.size)

        guestClient.joinByCode(created.accessCode, "Гость", 1).getOrThrow()
        runCurrent()
        assertEquals(2, vm.uiState.value.players.size)

        val nav = async { vm.navigateToGame.first() }
        vm.onStartClick()
        runCurrent()
        assertEquals(sessionId, nav.await())
    }
}
