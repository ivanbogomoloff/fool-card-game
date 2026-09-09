package com.example.foolcardgame.presentation.online

import com.example.foolcardgame.data.api.FakeGameApi
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.data.client.RemoteGameClient
import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class OnlineLobbyViewModelTest {

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
    fun quickMatch_pollsUntilSession() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = RemoteGameClient(
            api = FakeGameApi(fastJoinSuccessAfterAttempts = 2),
            authSession = auth,
        )
        assertTrue(client.login("Игрок").isSuccess)

        val vm = OnlineLobbyViewModel(client)
        val nav = async { vm.navEvents.first() }

        vm.onQuickMatchClick()
        runCurrent()
        assertTrue(vm.uiState.value.isQuickMatching)

        advanceTimeBy(GameClient.ROOM_POLL_INTERVAL_MS)
        runCurrent()

        val event = nav.await()
        assertTrue(event is OnlineLobbyNavEvent.ToGame)
        assertFalse(vm.uiState.value.isQuickMatching)
    }

    @Test
    fun friendsExpand_doesNotTouchApiList() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = RemoteGameClient(api = FakeGameApi(), authSession = auth)
        assertTrue(client.login("Игрок").isSuccess)

        val vm = OnlineLobbyViewModel(client)
        assertFalse(vm.uiState.value.friendsExpanded)
        vm.onFriendsExpandToggle()
        assertTrue(vm.uiState.value.friendsExpanded)
        assertEquals("Игрок", vm.uiState.value.displayName)
    }

    @Test
    fun createPrivate_navigatesToWaiting() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = RemoteGameClient(api = FakeGameApi(), authSession = auth)
        assertTrue(client.login("Хост").isSuccess)

        val vm = OnlineLobbyViewModel(client)
        val nav = async { vm.navEvents.first() }
        vm.onCreatePrivateClick()
        runCurrent()

        val event = nav.await()
        assertTrue(event is OnlineLobbyNavEvent.ToWaiting)
    }
}
