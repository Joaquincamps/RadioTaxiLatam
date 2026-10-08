package com.radiotaxilatam.driver.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")
private val CONDUCTOR_ID_KEY = longPreferencesKey("conductorId")

/**
 * Guarda el id del conductor que inició sesión. El backend de Joaquín no tiene token:
 * cada ruta lleva el id del conductor en la URL (o en ?conductorId=).
 */
class SessionStore(private val context: Context) {
    private val _conductorId = MutableStateFlow<Long?>(null)
    val conductorId: StateFlow<Long?> = _conductorId

    suspend fun load() {
        _conductorId.value = context.dataStore.data.map { it[CONDUCTOR_ID_KEY] }.first()
    }

    suspend fun save(conductorId: Long) {
        context.dataStore.edit { it[CONDUCTOR_ID_KEY] = conductorId }
        _conductorId.value = conductorId
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(CONDUCTOR_ID_KEY) }
        _conductorId.value = null
    }
}
