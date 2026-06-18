package com.abscafe.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "abs_cafe_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[USER_ROLE_KEY] }

    suspend fun saveLogin(token: String, name: String, email: String, role: String, id: Int) {
        context.dataStore.edit {
            it[TOKEN_KEY] = token
            it[USER_NAME_KEY] = name
            it[USER_EMAIL_KEY] = email
            it[USER_ROLE_KEY] = role
            it[USER_ID_KEY] = id.toString()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
