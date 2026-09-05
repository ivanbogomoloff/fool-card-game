package com.example.foolcardgame.domain.model

data class Player(
    val id: String,
    val displayName: String,
    val avatarId: Int,
    val isBot: Boolean,
    val hand: List<Card> = emptyList(),
    val isReady: Boolean = false,
    val isConnected: Boolean = true,
    val status: PlayerStatus = PlayerStatus.WAITING,
    /** Out of the game (no cards left after deck emptied). */
    val isFinished: Boolean = false,
)

data class TablePair(
    val id: Int,
    val attack: Card,
    val defense: Card? = null,
) {
    val isBeaten: Boolean get() = defense != null
}

data class GameConfig(
    val botCount: Int,
    val humanId: String = DEFAULT_HUMAN_ID,
    val humanDisplayName: String = OFFLINE_HUMAN_DISPLAY_NAME,
    val humanAvatarId: Int = UserProfile.DEFAULT_AVATAR_ID,
    val seed: Long = DEFAULT_SEED,
    val botThinkMinMs: Long = DEFAULT_BOT_THINK_MIN_MS,
    val botThinkMaxMs: Long = DEFAULT_BOT_THINK_MAX_MS,
) {
    init {
        require(botCount in 1..3) { "botCount must be 1..3, was $botCount" }
        require(botThinkMinMs in DEFAULT_BOT_THINK_MIN_MS..botThinkMaxMs) {
            "botThinkMinMs must be in $DEFAULT_BOT_THINK_MIN_MS..botThinkMaxMs, was $botThinkMinMs"
        }
    }

    val playerCount: Int get() = botCount + 1

    companion object {
        const val DEFAULT_HUMAN_ID = "local"
        /** Fixed local seat label for offline/bots; account name comes with online later. */
        const val OFFLINE_HUMAN_DISPLAY_NAME = "Вы"
        const val DEFAULT_SEED = 42L
        const val TURN_TIMEOUT_MS = 60_000L
        const val THROW_TIMEOUT_MS = 30_000L
        const val DEFAULT_BOT_THINK_MIN_MS = 1_000L
        const val DEFAULT_BOT_THINK_MAX_MS = 5_000L
        const val BOT_CONNECT_MIN_MS = 1_000L
        const val BOT_CONNECT_MAX_MS = 3_000L
        const val BOT_READY_MIN_MS = 2_000L
        const val BOT_READY_MAX_MS = 5_000L
    }
}
