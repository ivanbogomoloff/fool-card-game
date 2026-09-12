package com.example.foolcardgame.presentation.online

import com.example.foolcardgame.data.client.FakeOnlineGameClient
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.data.local.AccountCredentials
import com.example.foolcardgame.data.local.InMemoryAccountCredentialsStore
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
    fun displayName_fromCredentials_isReadOnlySource() = runTest {
        val creds = InMemoryAccountCredentialsStore().apply {
            save(AccountCredentials(username = "Алиса", password = "secret", accountId = "a1"))
        }
        val vm = OnlineLobbyViewModel(
            gameClient = FakeOnlineGameClient(),
            credentialsStore = creds,
        )
        assertEquals("Алиса", vm.uiState.value.displayName)
    }

    @Test
    fun quickMatch_collectsQueueUntilMatched() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = FakeOnlineGameClient(
            authSession = auth,
            quickMatchSuccessAfterUpdates = 2,
        )
        assertTrue(client.login("Игрок").isSuccess)
        val creds = InMemoryAccountCredentialsStore().apply {
            save(AccountCredentials(username = "Игрок", password = "p", accountId = "1"))
        }

        val vm = OnlineLobbyViewModel(client, creds)
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
        val client = FakeOnlineGameClient(authSession = auth)
        assertTrue(client.login("Игрок").isSuccess)
        val creds = InMemoryAccountCredentialsStore().apply {
            save(AccountCredentials(username = "Игрок", password = "p", accountId = "1"))
        }

        val vm = OnlineLobbyViewModel(client, creds)
        assertFalse(vm.uiState.value.friendsExpanded)
        vm.onFriendsExpandToggle()
        assertTrue(vm.uiState.value.friendsExpanded)
        assertEquals("Игрок", vm.uiState.value.displayName)
    }

    @Test
    fun createPrivate_navigatesToWaiting() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = FakeOnlineGameClient(authSession = auth)
        assertTrue(client.login("Хост").isSuccess)
        val creds = InMemoryAccountCredentialsStore().apply {
            save(AccountCredentials(username = "Хост", password = "p", accountId = "1"))
        }

        val vm = OnlineLobbyViewModel(client, creds)
        val nav = async { vm.navEvents.first() }
        vm.onCreatePrivateClick()
        runCurrent()

        val event = nav.await()
        assertTrue(event is OnlineLobbyNavEvent.ToWaiting)
    }

    @Test
    fun quickMatch_sessionError_clearsWaitingAndShowsMessage() = runTest {
        val auth = InMemoryAuthSessionStore()
        // Долгое ожидание матча, чтобы успеть прислать ошибку
        val client = FakeOnlineGameClient(
            authSession = auth,
            quickMatchSuccessAfterUpdates = 50,
        )
        assertTrue(client.login("Игрок").isSuccess)
        val creds = InMemoryAccountCredentialsStore().apply {
            save(AccountCredentials(username = "Игрок", password = "p", accountId = "1"))
        }

        val vm = OnlineLobbyViewModel(client, creds)
        vm.onQuickMatchClick()
        runCurrent()
        assertTrue(vm.uiState.value.isQuickMatching)

        client.emitSessionError("аккаунт уже в очереди или матче")
        runCurrent()

        assertFalse(vm.uiState.value.isQuickMatching)
        assertEquals("аккаунт уже в очереди или матче", vm.uiState.value.errorMessage)
    }
}
