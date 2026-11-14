package com.radiotaxilatam.driver.data

import android.os.SystemClock
import com.radiotaxilatam.driver.data.geo.Punto
import com.radiotaxilatam.driver.data.model.ConductorDTO
import com.radiotaxilatam.driver.data.model.ErrorDTO
import com.radiotaxilatam.driver.data.model.EstadoConductor
import com.radiotaxilatam.driver.data.model.EstadoConductorDTO
import com.radiotaxilatam.driver.data.model.EstadoViaje
import com.radiotaxilatam.driver.data.model.LoginDTO
import com.radiotaxilatam.driver.data.model.OfertaViajeDto
import com.radiotaxilatam.driver.data.model.TipoUsuario
import com.radiotaxilatam.driver.data.model.UbicacionDTO
import com.radiotaxilatam.driver.data.model.Viaje
import com.radiotaxilatam.driver.data.model.ViajeDTO
import com.radiotaxilatam.driver.data.model.aConductorDTO
import com.radiotaxilatam.driver.data.remote.AppJson
import com.radiotaxilatam.driver.data.remote.TaxiApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

/** Oferta recibida, con su fecha límite en reloj monotónico local. */
data class OfertaActiva(val oferta: OfertaViajeDto, val limiteElapsedMs: Long, val segundosTotales: Int) {
    fun segundosRestantes(ahora: Long = SystemClock.elapsedRealtime()): Int =
        ((limiteElapsedMs - ahora) / 1000).toInt().coerceAtLeast(0)
}

/**
 * Fuente única de verdad del estado del conductor, compartida por la UI y el servicio en segundo plano.
 *
 * Lo que el backend de Joaquín aún no tiene se resuelve aquí:
 * - Sesión: no hay token; se guarda el id del conductor.
 * - Mis datos: se busca el conductor en GET /usuarios/listar.
 * - Viaje actual e historial: se filtran los viajes de este conductor en GET /viaje/listar/viajes.
 * - Si el conductor cancela, su backend lo deja en BUSY: la app lo vuelve a poner AVAILABLE
 *   (también tras finalizar, aunque ahí su backend ya lo hace; no hace daño). Al devolver no hace falta.
 * - Oferta que caduca sin respuesta: su backend ya la pasa al siguiente conductor, pero la app la sigue
 *   rechazando justo antes de que caduque (MARGEN_RECHAZO_S) para no esperar a su tarea programada.
 */
class ConductorRepositorio(
    private val api: TaxiApi,
    private val session: SessionStore,
    private val registro: RegistroViajes,
) {
    private val _conductor = MutableStateFlow<ConductorDTO?>(null)
    val conductor: StateFlow<ConductorDTO?> = _conductor.asStateFlow()

    private val _viajeActual = MutableStateFlow<Viaje?>(null)
    val viajeActual: StateFlow<Viaje?> = _viajeActual.asStateFlow()

    private val _oferta = MutableStateFlow<OfertaActiva?>(null)
    val oferta: StateFlow<OfertaActiva?> = _oferta.asStateFlow()

    /** Última ubicación del taxi (la que se envía al backend), para dibujarlo en el mapa. */
    private val _ubicacion = MutableStateFlow<Punto?>(null)
    val ubicacion: StateFlow<Punto?> = _ubicacion.asStateFlow()

    /** Desde cuándo (epoch ms) el conductor está en descanso en este móvil; null si está trabajando. */
    private val _descansoDesde = MutableStateFlow<Long?>(null)
    val descansoDesde: StateFlow<Long?> = _descansoDesde.asStateFlow()

    val registros: StateFlow<Map<Long, RegistroViaje>> = registro.registros

    private val _sesionCaducada = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sesionCaducada: SharedFlow<Unit> = _sesionCaducada

    /** Evita que la consulta periódica pise el resultado de aceptar, rechazar o avanzar el viaje. */
    private val mutex = Mutex()

    /** Ofertas que el conductor ya aceptó, rechazó o dejó caducar: no volver a mostrarlas. */
    private val ofertasAtendidas = mutableSetOf<Long>()

    val haIniciadoSesion: Boolean get() = session.conductorId.value != null

    private val conductorId: Long
        get() = session.conductorId.value ?: throw AppException(null, "Sesión no válida. Vuelve a iniciar sesión.")

    suspend fun login(movil: String, password: String): Result<ConductorDTO> = safeCall(
        mensajes = mapOf(500 to "Teléfono o contraseña incorrectos, o el usuario no es conductor"),
    ) {
        val conductor = api.login(LoginDTO(movil.trim(), password))
        session.save(conductor.id)
        _conductor.value = conductor
        conductor
    }

    suspend fun logout() {
        if (_conductor.value?.estado == EstadoConductor.AVAILABLE) {
            safeCall { api.cambiarDisponibilidad(conductorId, EstadoConductorDTO(EstadoConductor.OFFLINE)) }
        }
        borrarSesionLocal()
    }

    suspend fun borrarSesionLocal() {
        session.clear()
        _conductor.value = null
        _viajeActual.value = null
        _oferta.value = null
        _ubicacion.value = null
        _descansoDesde.value = null
        ofertasAtendidas.clear()
    }

    suspend fun refrescar(): Result<Unit> = safeCall {
        _conductor.value = obtenerConductor()
        _viajeActual.value = buscarViajeActivo(api.obtenerTodosLosViajes())
    }

    suspend fun cambiarDisponibilidad(disponible: Boolean): Result<ConductorDTO> = safeCall(
        mensajes = mapOf(409 to "No puedes desconectarte con una carrera activa"),
    ) {
        val estado = if (disponible) EstadoConductor.AVAILABLE else EstadoConductor.OFFLINE
        api.cambiarDisponibilidad(conductorId, EstadoConductorDTO(estado)).also {
            _conductor.value = it
            _descansoDesde.value = if (disponible) null else System.currentTimeMillis()
            if (!disponible) _oferta.value = null
        }
    }

    /**
     * Consulta el viaje actual y las ofertas pendientes. Devuelve la oferta
     * solo si es nueva, para que el servicio lance la notificación.
     */
    suspend fun consultar(): Result<OfertaViajeDto?> = safeCall(bloquear = true) {
        val viaje = buscarViajeActivo(api.obtenerTodosLosViajes())
        if (viaje == null && _conductor.value?.estado == EstadoConductor.BUSY) {
            // El viaje terminó o lo cancelaron, pero su backend deja al conductor en BUSY.
            liberarConductor()
        }
        _viajeActual.value = viaje
        if (viaje != null) {
            _oferta.value = null
            return@safeCall null
        }
        if (_conductor.value?.estado != EstadoConductor.AVAILABLE) return@safeCall null

        val ofertas = api.obtenerOfertas(conductorId)
        val actual = _oferta.value
        if (actual != null && ofertas.none { it.id == actual.oferta.id }) {
            _oferta.value = null // caducó o el viaje se lo llevó otro conductor
        }
        if (_oferta.value != null) return@safeCall null
        val nueva = ofertas.firstOrNull { it.id !in ofertasAtendidas } ?: return@safeCall null
        val segundos = (nueva.segundosRestantes - MARGEN_RECHAZO_S).coerceAtLeast(1)
        _oferta.value = OfertaActiva(nueva, SystemClock.elapsedRealtime() + segundos * 1000L, segundos)
        nueva
    }

    suspend fun aceptarOferta(ofertaId: Long): Result<Viaje> {
        ofertasAtendidas += ofertaId
        _oferta.value = null
        return safeCall(bloquear = true) {
            val dto = api.aceptarOferta(ofertaId, conductorId)
            val viaje = api.obtenerTodosLosViajes().firstOrNull { it.id == dto.id } ?: dto.aViaje()
            _viajeActual.value = viaje
            _conductor.value = _conductor.value?.copy(estado = EstadoConductor.BUSY)
            registro.actualizar(viaje.id) { it.copy(aceptadoEn = System.currentTimeMillis()) }
            viaje
        }
    }

    suspend fun rechazarOferta(ofertaId: Long): Result<Unit> {
        ofertasAtendidas += ofertaId
        _oferta.value = null
        return safeCall(bloquear = true) {
            val response = api.rechazarOferta(ofertaId, conductorId)
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    /** Se agotó el tiempo: se rechaza para que el backend ofrezca el viaje al siguiente conductor. */
    suspend fun ofertaCaducada(ofertaId: Long) {
        if (ofertaId in ofertasAtendidas) return
        rechazarOferta(ofertaId)
    }

    suspend fun avanzarViaje(viaje: Viaje): Result<Unit> = safeCall(
        bloquear = true,
        mensajes = mapOf(500 to "No se pudo actualizar la carrera. Puede que la hayan cancelado."),
    ) {
        when (viaje.estado) {
            EstadoViaje.ACEPTADO -> {
                val dto = api.registrarLlegada(viaje.id, conductorId)
                _viajeActual.value = viaje.copy(estado = dto.estado)
            }
            EstadoViaje.LLEGADO -> {
                val dto = api.iniciarViaje(viaje.id, conductorId)
                _viajeActual.value = viaje.copy(estado = dto.estado)
                registro.actualizar(viaje.id) { it.copy(iniciadoEn = System.currentTimeMillis()) }
            }
            EstadoViaje.EN_CURSO -> {
                api.finalizarViaje(viaje.id, conductorId)
                registro.actualizar(viaje.id) { it.copy(terminadoEn = System.currentTimeMillis()) }
                _viajeActual.value = null
                liberarConductor()
            }
            else -> throw AppException(null, "La carrera no se puede avanzar")
        }
    }

    /**
     * Cancela el viaje antes de iniciarlo (p. ej. el cliente no acepta el precio: no hay taxímetro).
     * Su backend deja al conductor en BUSY, así que la app lo vuelve a poner AVAILABLE.
     */
    suspend fun cancelarViaje(viaje: Viaje): Result<Unit> = safeCall(
        bloquear = true,
        mensajes = mapOf(
            403 to "Esta carrera ya no está asignada a ti",
            404 to "La carrera ya no existe",
            500 to "No se pudo cancelar la carrera. Puede que ya esté cancelada.",
        ),
    ) {
        if (viaje.estado != EstadoViaje.ACEPTADO && viaje.estado != EstadoViaje.LLEGADO) {
            throw AppException(null, "Solo se puede cancelar antes de iniciar el viaje")
        }
        api.cancelarViaje(viaje.id, conductorId)
        registro.actualizar(viaje.id) { it.copy(terminadoEn = System.currentTimeMillis()) }
        _viajeActual.value = null
        liberarConductor()
    }

    /**
     * Devuelve la carrera antes de iniciarla (p. ej. una avería o no llega a tiempo): a diferencia de
     * cancelar, el pasajero no se queda sin taxi, porque su backend se la ofrece al siguiente conductor.
     * Su backend ya deja al conductor AVAILABLE. No sale en el historial: el viaje deja de ser suyo.
     */
    suspend fun devolverViaje(viaje: Viaje): Result<Unit> = safeCall(
        bloquear = true,
        mensajes = mapOf(
            403 to "Esta carrera ya no está asignada a ti",
            404 to "La carrera ya no existe",
            409 to "Solo se puede devolver una carrera antes de iniciarla",
        ),
    ) {
        if (viaje.estado != EstadoViaje.ACEPTADO && viaje.estado != EstadoViaje.LLEGADO) {
            throw AppException(null, "Solo se puede devolver una carrera antes de iniciarla")
        }
        val response = api.devolverViaje(viaje.id, conductorId)
        if (!response.isSuccessful) throw HttpException(response)
        registro.borrar(viaje.id)
        _viajeActual.value = null
        _conductor.value = _conductor.value?.copy(estado = EstadoConductor.AVAILABLE)
    }

    /** Guarda la distancia de la ruta calculada para mostrarla en el historial. */
    suspend fun guardarDistancia(viajeId: Long, metros: Int) {
        registro.actualizar(viajeId) { it.copy(distanciaMetros = metros) }
    }

    /** Viajes FINALIZADO y CANCELADO de este conductor, los más recientes primero. */
    suspend fun historial(): Result<List<Viaje>> = safeCall {
        val id = conductorId
        api.obtenerTodosLosViajes()
            .filter { it.conductor?.id == id && (it.estado == EstadoViaje.FINALIZADO || it.estado == EstadoViaje.CANCELADO) }
            .sortedByDescending { it.id }
    }

    suspend fun enviarUbicacion(latitud: Double, longitud: Double): Result<Unit> {
        _ubicacion.value = Punto(latitud, longitud)
        return safeCall { api.enviarUbicacion(conductorId, UbicacionDTO(latitud, longitud)) }
    }

    private suspend fun obtenerConductor(): ConductorDTO {
        val id = conductorId
        val usuario = api.obtenerTodosLosUsuarios().firstOrNull { it.id == id && it.tipo == TipoUsuario.CONDUCTOR }
        if (usuario == null) {
            borrarSesionLocal()
            _sesionCaducada.tryEmit(Unit)
            throw AppException(null, "Tu usuario ya no existe. Vuelve a iniciar sesión.")
        }
        return usuario.aConductorDTO()
    }

    private suspend fun liberarConductor() {
        _conductor.value = try {
            api.cambiarDisponibilidad(conductorId, EstadoConductorDTO(EstadoConductor.AVAILABLE))
        } catch (e: HttpException) {
            obtenerConductor()
        }
    }

    private fun buscarViajeActivo(viajes: List<Viaje>): Viaje? {
        val id = conductorId
        return viajes.firstOrNull {
            it.conductor?.id == id && it.estado in ESTADOS_ACTIVOS
        }
    }

    private fun ViajeDTO.aViaje() = Viaje(
        id = id,
        origen = origen,
        destino = destino,
        latitudOrigen = latitudOrigen,
        longitudOrigen = longitudOrigen,
        latitudDestino = latitudDestino,
        longitudDestino = longitudDestino,
        estado = estado,
    )

    private suspend fun <T> safeCall(
        bloquear: Boolean = false,
        mensajes: Map<Int, String> = emptyMap(),
        block: suspend () -> T,
    ): Result<T> = try {
        Result.success(if (bloquear) mutex.withLock { block() } else block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        Result.failure(e)
    } catch (e: HttpException) {
        Result.failure(AppException(e.code(), mensajeDe(e, mensajes)))
    } catch (e: IOException) {
        Result.failure(AppException(null, "Sin conexión con el servidor. Revisa tu internet."))
    } catch (e: Exception) {
        Result.failure(AppException(null, e.message ?: "Error inesperado"))
    }

    private fun mensajeDe(e: HttpException, mensajes: Map<Int, String>): String {
        val body = e.response()?.errorBody()?.string()
        val mensajeServidor = body?.let { runCatching { AppJson.decodeFromString<ErrorDTO>(it).message }.getOrNull() }
        // Sus RuntimeException llegan como 500 y su texto no sirve al conductor: ahí manda el mensaje de la app.
        if (e.code() < 500 && !mensajeServidor.isNullOrBlank()) return mensajeServidor
        return mensajes[e.code()] ?: when (e.code()) {
            404 -> "No encontrado"
            409, 410 -> "La carrera ya no está disponible"
            in 500..599 -> "Error del servidor. Inténtalo de nuevo."
            else -> "Error ${e.code()}"
        }
    }

    private companion object {
        /**
         * Segundos antes de que caduque la oferta en los que la app la rechaza automáticamente.
         * El tiempo total lo fija el backend (hoy 20 s en OfertaViajeServicio).
         */
        const val MARGEN_RECHAZO_S = 1

        /** Estados en los que el viaje es la carrera actual del conductor. */
        val ESTADOS_ACTIVOS = setOf(EstadoViaje.ACEPTADO, EstadoViaje.LLEGADO, EstadoViaje.EN_CURSO)
    }
}

class AppException(val httpCode: Int?, override val message: String) : Exception(message)
