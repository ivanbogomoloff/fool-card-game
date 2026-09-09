package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.api.FakeGameApi
import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteGameClientTest {

    @Test
    fun login_thenQuickMatch() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = RemoteGameClient(
            api = FakeGameApi(fastJoinSuccessAfterAttempts = 2),
            authSession = auth,
        )
        assertFalse(client.isAuthorized())
        assertTrue(client.login("Иван").isSuccess)
        assertTrue(client.isAuthorized())

        assertNull(client.quickMatch("Иван", 0).getOrThrow())
        val sessionFromQuick = client.quickMatch("Иван", 0).getOrThrow()
        assertNotNull(sessionFromQuick)
        assertTrue(sessionFromQuick!!.startsWith("online-"))
    }

    @Test
    fun createJoinKickStart_onSharedApi() = runTest {
        val api = FakeGameApi()
        val hostAuth = InMemoryAuthSessionStore()
        val guestAuth = InMemoryAuthSessionStore()
        val host = RemoteGameClient(api = api, authSession = hostAuth)
        val guest = RemoteGameClient(api = api, authSession = guestAuth)

        assertTrue(host.login("Хост").isSuccess)
        assertTrue(guest.login("Гость").isSuccess)

        val created = host.createPrivateGame("Хост", 0).getOrThrow()
        val (sessionId, guestId) = guest.joinByCode(created.accessCode, "Гость", 2).getOrThrow()
        assertEquals(created.sessionId, sessionId)

        val room = host.getRoom(sessionId).getOrThrow()
        assertEquals(2, room.players.size)
        assertFalse(room.started)

        assertTrue(host.kickPlayer(sessionId, guestId).isSuccess)
        assertEquals(1, host.getRoom(sessionId).getOrThrow().players.size)

        val rejoined = guest.joinByCode(created.accessCode, "Гость", 2).getOrThrow()
        assertTrue(host.startGame(rejoined.first).isSuccess)
        assertTrue(host.getRoom(rejoined.first).getOrThrow().started)
    }

    @Test
    fun quickMatch_withoutLogin_fails() = runTest {
        val client = RemoteGameClient(api = FakeGameApi(), authSession = InMemoryAuthSessionStore())
        assertTrue(client.quickMatch("Иван", 0).isFailure)
    }
}
