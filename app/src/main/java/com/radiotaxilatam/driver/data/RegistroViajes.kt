package com.radiotaxilatam.driver.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radiotaxilatam.driver.data.remote.AppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Horas (epoch ms) y distancia de un viaje, tal como las vio este móvil. */
@Serializable
data class RegistroViaje(
    val aceptadoEn: Long? = null,
    val iniciadoEn: Long? = null,
    val terminadoEn: Long? = null,
    val distanciaMetros: Int? = null,
)

private val Context.registroStore by preferencesDataStore(name = "registro_viajes")
private val REGISTROS_KEY = stringPreferencesKey("registros")

/**
 * El backend de Joaquín solo guarda cuándo se creó y se finalizó cada viaje (creadoEn, finalizadoEn),
 * así que el móvil anota cuándo el conductor aceptó, inició y terminó (o canceló) cada uno.
 * Solo cubre los viajes hechos desde este móvil.
 */
class RegistroViajes(private val context: Context) {
    private val _registros = MutableStateFlow<Map<Long, RegistroViaje>>(emptyMap())
    val registros: StateFlow<Map<Long, RegistroViaje>> = _registros
    private val mutex = Mutex()

    suspend fun cargar() {
        val json = context.registroStore.data.map { it[REGISTROS_KEY] }.first() ?: return
        _registros.value = runCatching { AppJson.decodeFromString<Map<Long, RegistroViaje>>(json) }.getOrDefault(emptyMap())
    }

    suspend fun actualizar(viajeId: Long, cambio: (RegistroViaje) -> RegistroViaje) = mutex.withLock {
        val nuevos = _registros.value + (viajeId to cambio(_registros.value[viajeId] ?: RegistroViaje()))
        _registros.value = nuevos
        context.registroStore.edit { it[REGISTROS_KEY] = AppJson.encodeToString(nuevos) }
    }

    /** Olvida un viaje que dejó de ser de este conductor (lo devolvió). */
    suspend fun borrar(viajeId: Long) = mutex.withLock {
        if (viajeId !in _registros.value) return@withLock
        val nuevos = _registros.value - viajeId
        _registros.value = nuevos
        context.registroStore.edit { it[REGISTROS_KEY] = AppJson.encodeToString(nuevos) }
    }
}
