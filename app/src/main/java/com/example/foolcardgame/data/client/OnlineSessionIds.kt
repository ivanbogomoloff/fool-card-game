package com.example.foolcardgame.data.client

/**
 * Префикс online sessionId, чтобы отличить от локального UUID [GameEngine].
 * На wire уходит [unwrap] (чистый game_id сервера).
 */
object OnlineSessionIds {
    private const val PREFIX = "online:"

    fun wrap(gameId: String): String =
        if (gameId.startsWith(PREFIX)) gameId else PREFIX + gameId

    fun unwrap(sessionId: String): String =
        sessionId.removePrefix(PREFIX)

    fun isOnline(sessionId: String): Boolean =
        sessionId.startsWith(PREFIX)
}
