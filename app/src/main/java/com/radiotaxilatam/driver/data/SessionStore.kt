package com.radiotaxilatam.driver.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")
private val TOKEN_KEY = stringPreferencesKey("token")

/** Guarda el token JWT. Se mantiene en memoria para que el interceptor HTTP lo lea sin bloquear. */
class SessionStore(private val context: Context) {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token

    suspend fun load() {
        _token.value = context.dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun save(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
        _token.value = token
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
        _token.value = null
    }
}
