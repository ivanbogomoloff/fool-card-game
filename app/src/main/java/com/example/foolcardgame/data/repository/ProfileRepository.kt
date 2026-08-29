package com.example.foolcardgame.data.repository

import com.example.foolcardgame.data.api.ProfileApi
import com.example.foolcardgame.data.local.ProfileLocalStore
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

class ProfileRepository(
    private val localStore: ProfileLocalStore,
    private val api: ProfileApi,
) {

    fun observeProfile(): Flow<UserProfile> = localStore.observeProfile()

    suspend fun saveProfile(profile: UserProfile): SaveProfileResult {
        localStore.saveProfile(profile)
        return api.saveProfile(profile).fold(
            onSuccess = { SaveProfileResult.Success },
            onFailure = { SaveProfileResult.SavedLocallyApiFailed(it.message) },
        )
    }
}

sealed interface SaveProfileResult {
    data object Success : SaveProfileResult
    data class SavedLocallyApiFailed(val message: String?) : SaveProfileResult
}
