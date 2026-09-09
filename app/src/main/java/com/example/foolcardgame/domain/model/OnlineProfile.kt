package com.example.foolcardgame.domain.model

/** Online-аккаунт (имя + аватар), без локальных настроек темы/звуков. */
data class OnlineProfile(
    val displayName: String,
    val avatarId: Int,
)
