package com.example.foolcardgame.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val displayName: String? = null,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val displayName: String,
    val avatarId: Int = 0,
)

@Serializable
data class PlayerProfileRequestDto(
    val displayName: String,
    val avatarId: Int,
)

@Serializable
data class FastJoinResponseDto(
    val sessionId: String,
)

@Serializable
data class CreateGameResponseDto(
    val sessionId: String,
    val accessCode: String,
    val hostId: String,
)

@Serializable
data class JoinByCodeRequestDto(
    val code: String,
    val displayName: String,
    val avatarId: Int,
)

@Serializable
data class JoinGameResponseDto(
    val sessionId: String,
    val playerId: String,
)

@Serializable
data class RoomPlayerDto(
    val id: String,
    val displayName: String,
    val avatarId: Int,
    val isHost: Boolean,
)

@Serializable
data class RoomStateDto(
    val sessionId: String,
    val accessCode: String,
    val hostId: String,
    val started: Boolean,
    val players: List<RoomPlayerDto>,
)

@Serializable
data class KickPlayerRequestDto(
    val playerId: String,
)

/** Результат создания приватной комнаты на клиенте. */
data class CreateGameResult(
    val sessionId: String,
    val accessCode: String,
    val hostId: String,
)
