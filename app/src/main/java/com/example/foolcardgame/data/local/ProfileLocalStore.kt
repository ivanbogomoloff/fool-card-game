package com.example.foolcardgame.data.local

import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileLocalStore {
    fun observeProfile(): Flow<UserProfile>
    suspend fun saveProfile(profile: UserProfile)
}
