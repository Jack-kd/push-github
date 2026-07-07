package com.jack.pushgithub.data

import android.content.Context
import android.content.SharedPreferences

data class GitConfig(
    val email: String = "",
    val username: String = "",
    val token: String = ""
)

class ConfigRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("github_config", Context.MODE_PRIVATE)

    fun loadConfig(): GitConfig {
        return GitConfig(
            email = prefs.getString("git_email", "") ?: "",
            username = prefs.getString("git_user", "") ?: "",
            token = prefs.getString("git_token", "") ?: ""
        )
    }

    fun saveConfig(config: GitConfig) {
        prefs.edit().apply {
            putString("git_email", config.email)
            putString("git_user", config.username)
            putString("git_token", config.token)
            apply()
        }
    }

    fun hasConfig(): Boolean {
        val config = loadConfig()
        return config.email.isNotBlank() && config.username.isNotBlank() && config.token.isNotBlank()
    }
}