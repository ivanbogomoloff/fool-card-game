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
    fun onlineWaiting_buildsPathWithSessionId() {
        assertEquals("online/waiting/abc-123", Routes.onlineWaiting("abc-123"))
    }

    @Test
    fun game_buildsPathWithSessionId() {
        assertEquals("game/session-42", Routes.game("session-42"))
    }
}
