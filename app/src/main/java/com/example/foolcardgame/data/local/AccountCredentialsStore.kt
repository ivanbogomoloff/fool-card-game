package com.example.foolcardgame.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Логин/пароль аккаунта (EncryptedSharedPreferences). */
data class AccountCredentials(
    val username: String,
    val password: String,
    val accountId: String = "",
)

interface AccountCredentialsStore {
    fun load(): AccountCredentials?
    fun save(credentials: AccountCredentials)
    fun clear()
}

class EncryptedAccountCredentialsStore(
    context: Context,
) : AccountCredentialsStore {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "account_credentials",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): AccountCredentials? {
        val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() } ?: return null
        val accountId = prefs.getString(KEY_ACCOUNT_ID, "") ?: ""
        return AccountCredentials(username = username, password = password, accountId = accountId)
    }

    override fun save(credentials: AccountCredentials) {
        prefs.edit()
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_ACCOUNT_ID, credentials.accountId)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCOUNT_ID = "account_id"
    }
}

/** In-memory для unit-тестов. */
class InMemoryAccountCredentialsStore : AccountCredentialsStore {
    private var value: AccountCredentials? = null

    override fun load(): AccountCredentials? = value

    override fun save(credentials: AccountCredentials) {
        value = credentials
    }

    override fun clear() {
        value = null
    }
}
