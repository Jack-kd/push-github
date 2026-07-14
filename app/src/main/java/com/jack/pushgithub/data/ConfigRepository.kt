package com.jack.pushgithub.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GitConfig(
    val email: String = "",
    val username: String = "",
    val token: String = ""
)

class ConfigRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "github_config_encrypted",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun loadConfig(): GitConfig =
        withContext(Dispatchers.IO) {
            GitConfig(
                email = prefs.getString("git_email", "") ?: "",
                username = prefs.getString("git_user", "") ?: "",
                token = prefs.getString("git_token", "") ?: ""
            )
        }

    suspend fun saveConfig(config: GitConfig) {
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                putString("git_email", config.email)
                putString("git_user", config.username)
                putString("git_token", config.token)
                apply()
            }
        }
    }

    suspend fun hasConfig(): Boolean {
        val config = loadConfig()
        return config.email.isNotBlank() && config.username.isNotBlank() && config.token.isNotBlank()
    }
}