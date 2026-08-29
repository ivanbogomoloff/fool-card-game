package com.example.foolcardgame.data.api.dto

import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val displayName: String,
    val avatarId: Int,
)

fun UserProfile.toDto(): ProfileDto = ProfileDto(
    displayName = displayName,
    avatarId = avatarId,
)

fun ProfileDto.toDomain(): UserProfile = UserProfile(
    displayName = displayName,
    avatarId = avatarId,
)
