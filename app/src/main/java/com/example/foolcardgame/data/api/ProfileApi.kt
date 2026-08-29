package com.example.foolcardgame.data.api

import com.example.foolcardgame.domain.model.UserProfile

interface ProfileApi {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
}
