package com.example.foolcardgame.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {

    @Test
    fun routeConstants_areNotBlank() {
        assertTrue(Routes.LOGIN.isNotBlank())
        assertTrue(Routes.MAIN.isNotBlank())
        assertTrue(Routes.OFFLINE_SETUP.isNotBlank())
        assertTrue(Routes.ONLINE_LOBBY.isNotBlank())
        assertTrue(Routes.PROFILE.isNotBlank())
    }

    @Test
    fun onlineWaiting_buildsPathWithSessionAndPlayer() {
        assertEquals(
            "online/waiting/abc-123/p1",
            Routes.onlineWaiting("abc-123", "p1"),
        )
    }

    @Test
    fun onlineJoin_buildsPath() {
        assertEquals("online/join/%D0%98%D0%B2%D0%B0%D0%BD/2", Routes.onlineJoin("Иван", 2))
    }

    @Test
    fun game_buildsPathWithSessionId() {
        assertEquals("game/session-42", Routes.game("session-42"))
        assertEquals(
            "game/online%3Aabc",
            Routes.game("online:abc"),
        )
    }

    @Test
    fun isOnlineSession_detectsPrefix() {
        assertTrue(Routes.isOnlineSession("online:xyz"))
        assertTrue(!Routes.isOnlineSession("uuid-local"))
    }
}
