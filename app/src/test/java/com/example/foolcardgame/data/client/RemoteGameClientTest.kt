package com.example.foolcardgame.data.client

import com.example.foolcardgame.data.local.InMemoryAuthSessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteGameClientTest {

    @Test
    fun login_thenQuickMatch_viaFakeOnline() = runTest {
        val auth = InMemoryAuthSessionStore()
        val client = FakeOnlineGameClient(
            authSession = auth,
            quickMatchSuccessAfterUpdates = 1,
        )
        assertFalse(client.isAuthorized())
        assertTrue(client.login("Иван").isSuccess)
        assertTrue(client.isAuthorized())

        val matched = client.observeQuickMatch("Иван", 0).first { it is QueueUpdate.Matched }
        assertTrue(matched is QueueUpdate.Matched)
        assertTrue(OnlineSessionIds.isOnline((matched as QueueUpdate.Matched).sessionId))
    }

    @Test
    fun createJoinKickStart_onSharedBackend() = runTest {
        val backend = FakeOnlineBackend()
        val host = FakeOnlineGameClient(authSession = InMemoryAuthSessionStore(), backend = backend)
        val guest = FakeOnlineGameClient(authSession = InMemoryAuthSessionStore(), backend = backend)

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
        val client = FakeOnlineGameClient(authSession = InMemoryAuthSessionStore())
        val result = runCatching {
            client.observeQuickMatch("Иван", 0).first()
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun onlineSessionIds_wrapUnwrap() {
        val raw = "abc-123"
        val wrapped = OnlineSessionIds.wrap(raw)
        assertTrue(OnlineSessionIds.isOnline(wrapped))
        assertEquals(raw, OnlineSessionIds.unwrap(wrapped))
        assertNotNull(OnlineSessionIds.wrap(wrapped))
        assertEquals(wrapped, OnlineSessionIds.wrap(wrapped))
    }
}
