package com.example.foolcardgame.presentation.online

import com.example.foolcardgame.data.api.FakeGameApi
import com.example.foolcardgame.data.client.RemoteGameClient
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
    fun poll_loadsPlayers_andKickStart() = runTest {
        val api = FakeGameApi()
        val hostAuth = InMemoryAuthSessionStore()
        val guestAuth = InMemoryAuthSessionStore()
        val hostClient = RemoteGameClient(api = api, authSession = hostAuth)
        val guestClient = RemoteGameClient(api = api, authSession = guestAuth)
        assertTrue(hostClient.login("Хост").isSuccess)
        assertTrue(guestClient.login("Гость").isSuccess)

        val created = hostClient.createPrivateGame("Хост", 0).getOrThrow()
        val (sessionId, guestId) = guestClient.joinByCode(created.accessCode, "Гость", 1).getOrThrow()

        val vm = WaitingRoomViewModel(hostClient, sessionId, created.hostId)
        runCurrent()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isHost)
        assertEquals(2, vm.uiState.value.players.size)
        assertEquals(created.accessCode, vm.uiState.value.accessCode)

        vm.onKickClick(guestId)
        runCurrent()
        assertEquals(1, vm.uiState.value.players.size)

        guestClient.joinByCode(created.accessCode, "Гость", 1).getOrThrow()
        val vm2 = WaitingRoomViewModel(hostClient, sessionId, created.hostId)
        runCurrent()
        assertEquals(2, vm2.uiState.value.players.size)

        val nav = async { vm2.navigateToGame.first() }
        vm2.onStartClick()
        runCurrent()
        assertEquals(sessionId, nav.await())
    }
}
