package com.example.foolcardgame.domain.model

data class UserProfile(
    val displayName: String = DEFAULT_DISPLAY_NAME,
    val avatarId: Int = DEFAULT_AVATAR_ID,
) {
    companion object {
        const val DEFAULT_DISPLAY_NAME = "Игрок"
        const val DEFAULT_AVATAR_ID = 0
        const val AVATAR_COUNT = 8
    }
}
