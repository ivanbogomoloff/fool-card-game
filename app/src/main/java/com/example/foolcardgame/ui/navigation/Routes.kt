package com.example.foolcardgame.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val OFFLINE_SETUP = "offline/setup"
    const val ONLINE_LOBBY = "online/lobby"
    const val ONLINE_CREATE = "online/create"
    const val ONLINE_JOIN = "online/join"
    const val ONLINE_WAITING = "online/waiting/{sessionId}"
    const val PROFILE = "profile"
    const val GAME = "game/{sessionId}"
    const val GAME_DEBUG = "game/debug"

    fun onlineWaiting(sessionId: String) = "online/waiting/$sessionId"
    fun game(sessionId: String) = "game/$sessionId"
}
