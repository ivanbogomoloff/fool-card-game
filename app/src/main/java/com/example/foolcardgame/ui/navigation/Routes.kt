package com.example.foolcardgame.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val OFFLINE_SETUP = "offline/setup"
    const val ONLINE_LOBBY = "online/lobby"
    const val ONLINE_JOIN = "online/join/{displayName}/{avatarId}"
    const val ONLINE_WAITING = "online/waiting/{sessionId}/{playerId}"
    const val PROFILE = "profile"
    const val GAME = "game/{sessionId}"
    const val GAME_DEBUG = "game/debug"

    fun onlineJoin(displayName: String, avatarId: Int): String =
        "online/join/${encode(displayName)}/$avatarId"

    fun onlineWaiting(sessionId: String, playerId: String): String =
        "online/waiting/${encode(sessionId)}/${encode(playerId)}"

    fun game(sessionId: String) = "game/$sessionId"

    fun isOnlineSession(sessionId: String): Boolean = sessionId.startsWith("online-")

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
