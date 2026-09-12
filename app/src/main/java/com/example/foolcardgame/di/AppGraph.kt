package com.example.foolcardgame.di

import android.content.Context
import com.example.foolcardgame.data.api.FakeProfileApi
import com.example.foolcardgame.data.api.ProfileApi
import com.example.foolcardgame.data.audio.AndroidGameSoundEffects
import com.example.foolcardgame.data.client.LocalGameClient
import com.example.foolcardgame.data.client.RemoteGameClient
import com.example.foolcardgame.data.local.AccountCredentialsStore
import com.example.foolcardgame.data.local.AuthSessionDataStore
import com.example.foolcardgame.data.local.AuthSessionStore
import com.example.foolcardgame.data.local.EncryptedAccountCredentialsStore
import com.example.foolcardgame.data.local.ProfileDataStore
import com.example.foolcardgame.data.local.ProfileLocalStore
import com.example.foolcardgame.data.network.GrpcChannelFactory
import com.example.foolcardgame.data.network.NetworkReconnectWatcher
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.domain.audio.GameSoundEffects
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppGraph {

    private var profileApi: ProfileApi = FakeProfileApi()
    private val sharedLocalGameClient: LocalGameClient by lazy { LocalGameClient() }
    private var sharedRemoteGameClient: RemoteGameClient? = null
    private var authSessionStore: AuthSessionStore? = null
    private var credentialsStore: AccountCredentialsStore? = null
    private var channel: ManagedChannel? = null
    private var networkWatcher: NetworkReconnectWatcher? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gameSoundEffectsInstance: AndroidGameSoundEffects? = null

    fun profileRepository(context: Context): ProfileRepository {
        return ProfileRepository(
            localStore = profileDataStore(context),
            api = profileApi,
        )
    }

    fun localGameClient(): LocalGameClient = sharedLocalGameClient

    fun remoteGameClient(context: Context): RemoteGameClient {
        sharedRemoteGameClient?.let { return it }
        val appContext = context.applicationContext
        val client = RemoteGameClient(
            channel = grpcChannel(),
            authSession = authSession(appContext),
            credentialsStore = accountCredentials(appContext),
            scope = appScope,
        )
        sharedRemoteGameClient = client
        if (networkWatcher == null) {
            networkWatcher = NetworkReconnectWatcher(appContext) {
                sharedRemoteGameClient?.forceReconnect()
            }.also { it.start() }
        }
        return client
    }

    fun authSession(context: Context): AuthSessionStore {
        return authSessionStore ?: AuthSessionDataStore(context.applicationContext)
            .also { authSessionStore = it }
    }

    fun accountCredentials(context: Context): AccountCredentialsStore {
        return credentialsStore ?: EncryptedAccountCredentialsStore(context.applicationContext)
            .also { credentialsStore = it }
    }

    fun gameSoundEffects(context: Context): GameSoundEffects {
        return gameSoundEffectsInstance ?: AndroidGameSoundEffects(
            context = context.applicationContext,
            profileRepository = profileRepository(context),
            scope = appScope,
        ).also { gameSoundEffectsInstance = it }
    }

    private fun grpcChannel(): ManagedChannel {
        return channel ?: GrpcChannelFactory.create().also { channel = it }
    }

    private fun profileDataStore(context: Context): ProfileLocalStore {
        return ProfileDataStore(context.applicationContext)
    }

    internal fun setProfileApi(api: ProfileApi) {
        profileApi = api
    }

    internal fun setAuthSessionStore(store: AuthSessionStore) {
        authSessionStore = store
        sharedRemoteGameClient = null
    }

    internal fun setCredentialsStore(store: AccountCredentialsStore) {
        credentialsStore = store
        sharedRemoteGameClient = null
    }

    internal fun setRemoteGameClientForTests(client: RemoteGameClient?) {
        sharedRemoteGameClient = client
    }
}
