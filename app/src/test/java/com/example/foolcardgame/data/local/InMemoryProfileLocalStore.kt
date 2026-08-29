package com.example.foolcardgame.data.local

import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryProfileLocalStore(
    initial: UserProfile = UserProfile(),
) : ProfileLocalStore {

    private val profile = MutableStateFlow(initial)

    override fun observeProfile(): Flow<UserProfile> = profile

    override suspend fun saveProfile(profile: UserProfile) {
        this.profile.value = profile
    }
}
