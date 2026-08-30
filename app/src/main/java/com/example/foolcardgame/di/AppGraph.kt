package com.example.foolcardgame.di

import android.content.Context
import com.example.foolcardgame.data.api.FakeProfileApi
import com.example.foolcardgame.data.api.ProfileApi
import com.example.foolcardgame.data.client.LocalGameClient
import com.example.foolcardgame.data.audio.AndroidGameSoundEffects
import com.example.foolcardgame.data.local.ProfileDataStore
import com.example.foolcardgame.data.local.ProfileLocalStore
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.domain.audio.GameSoundEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppGraph {

    private var profileApi: ProfileApi = FakeProfileApi()
    private val sharedLocalGameClient: LocalGameClient by lazy { LocalGameClient() }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gameSoundEffectsInstance: AndroidGameSoundEffects? = null

    fun profileRepository(context: Context): ProfileRepository {
        return ProfileRepository(
            localStore = profileDataStore(context),
            api = profileApi,
        )
    }

    fun localGameClient(): LocalGameClient = sharedLocalGameClient

    fun gameSoundEffects(context: Context): GameSoundEffects {
        return gameSoundEffectsInstance ?: AndroidGameSoundEffects(
            context = context.applicationContext,
            profileRepository = profileRepository(context),
            scope = appScope,
        ).also { gameSoundEffectsInstance = it }
    }

    private fun profileDataStore(context: Context): ProfileLocalStore {
        return ProfileDataStore(context.applicationContext)
    }

    internal fun setProfileApi(api: ProfileApi) {
        profileApi = api
    }
}
