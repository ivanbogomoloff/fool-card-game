package com.example.foolcardgame.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

/** Локальное хранение online-токена (Phase 5 fake / Phase 6 JWT). */
interface AuthSessionStore {
    fun isAuthorized(): Boolean
    suspend fun getToken(): String?
    fun observeToken(): Flow<String?>
    suspend fun saveToken(token: String)
    suspend fun clear()
}

class AuthSessionDataStore(
    context: Context,
) : AuthSessionStore {

    private val dataStore = context.applicationContext.authDataStore
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var cacheReady: Boolean = false

    init {
        // Прогрев кэша для синхронного isAuthorized() на UI-потоке.
        runBlocking {
            cachedToken = dataStore.data.map { it[KEY_TOKEN] }.first()
            cacheReady = true
        }
    }

    override fun isAuthorized(): Boolean {
        if (!cacheReady) {
            cachedToken = runBlocking { dataStore.data.map { it[KEY_TOKEN] }.first() }
            cacheReady = true
        }
        return !cachedToken.isNullOrBlank()
    }

    override suspend fun getToken(): String? {
        val token = dataStore.data.map { it[KEY_TOKEN] }.first()
        cachedToken = token
        cacheReady = true
        return token
    }

    override fun observeToken(): Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY_TOKEN] }

    override suspend fun saveToken(token: String) {
        dataStore.edit { it[KEY_TOKEN] = token }
        cachedToken = token
        cacheReady = true
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY_TOKEN) }
        cachedToken = null
        cacheReady = true
    }

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
    }
}

/** In-memory для тестов и unit-тестов RemoteGameClient. */
class InMemoryAuthSessionStore : AuthSessionStore {
    private val tokenFlow = MutableStateFlow<String?>(null)

    override fun isAuthorized(): Boolean = !tokenFlow.value.isNullOrBlank()

    override suspend fun getToken(): String? = tokenFlow.value

    override fun observeToken(): Flow<String?> = tokenFlow.asStateFlow()

    override suspend fun saveToken(token: String) {
        tokenFlow.value = token
    }

    override suspend fun clear() {
        tokenFlow.value = null
    }
}
