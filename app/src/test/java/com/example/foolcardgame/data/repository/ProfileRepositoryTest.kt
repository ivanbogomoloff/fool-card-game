package com.example.foolcardgame.data.repository

import com.example.foolcardgame.data.api.FakeProfileApi
import com.example.foolcardgame.data.local.InMemoryProfileLocalStore
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {

    @Test
    fun saveProfile_persistsLocallyAndCallsApi() = runTest {
        val localStore = InMemoryProfileLocalStore()
        val api = FakeProfileApi()
        val repository = ProfileRepository(localStore, api)
        val profile = UserProfile(displayName = "Иван", avatarId = 2)

        val result = repository.saveProfile(profile)

        assertEquals(SaveProfileResult.Success, result)
        assertEquals(profile, localStore.observeProfile().first())
        assertEquals(profile, api.lastSavedProfile)
    }

    @Test
    fun saveProfile_returnsSavedLocallyWhenApiFails() = runTest {
        val localStore = InMemoryProfileLocalStore()
        val api = FakeProfileApi(shouldFail = true)
        val repository = ProfileRepository(localStore, api)
        val profile = UserProfile(displayName = "Anna", avatarId = 1)

        val result = repository.saveProfile(profile)

        assertTrue(result is SaveProfileResult.SavedLocallyApiFailed)
        assertEquals(profile, localStore.observeProfile().first())
    }
}
