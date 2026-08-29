package com.example.foolcardgame.data.api

import com.example.foolcardgame.data.api.dto.toDto
import com.example.foolcardgame.domain.model.UserProfile

class FakeProfileApi : ProfileApi {

    var lastSavedProfile: UserProfile? = null
        private set
    var shouldFail: Boolean = false

    constructor()

    constructor(shouldFail: Boolean) {
        this.shouldFail = shouldFail
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        if (shouldFail) {
            return Result.failure(IllegalStateException("Fake API error"))
        }
        lastSavedProfile = profile
        println("FakeProfileApi: POST /profile ${profile.toDto()}")
        return Result.success(Unit)
    }
}
