package com.example.foolcardgame.di

import android.content.Context
import com.example.foolcardgame.data.api.FakeProfileApi
import com.example.foolcardgame.data.api.ProfileApi
import com.example.foolcardgame.data.client.LocalGameClient
import com.example.foolcardgame.data.local.ProfileDataStore
import com.example.foolcardgame.data.local.ProfileLocalStore
import com.example.foolcardgame.data.repository.ProfileRepository

object AppGraph {

    private var profileApi: ProfileApi = FakeProfileApi()
    private val sharedLocalGameClient: LocalGameClient by lazy { LocalGameClient() }

    fun profileRepository(context: Context): ProfileRepository {
        return ProfileRepository(
            localStore = profileDataStore(context),
            api = profileApi,
        )
    }

    fun localGameClient(): LocalGameClient = sharedLocalGameClient

    private fun profileDataStore(context: Context): ProfileLocalStore {
        return ProfileDataStore(context.applicationContext)
    }

    internal fun setProfileApi(api: ProfileApi) {
        profileApi = api
    }
}
