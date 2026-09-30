package com.radiotaxilatam.driver.data

import android.os.SystemClock
import com.radiotaxilatam.driver.data.model.ApiError
import com.radiotaxilatam.driver.data.model.Driver
import com.radiotaxilatam.driver.data.model.DriverStatus
import com.radiotaxilatam.driver.data.model.LocationUpdate
import com.radiotaxilatam.driver.data.model.LoginRequest
import com.radiotaxilatam.driver.data.model.Page
import com.radiotaxilatam.driver.data.model.RejectRequest
import com.radiotaxilatam.driver.data.model.Ride
import com.radiotaxilatam.driver.data.model.RideOffer
import com.radiotaxilatam.driver.data.model.RideStatus
import com.radiotaxilatam.driver.data.model.RideStatusRequest
import com.radiotaxilatam.driver.data.model.StatusRequest
import com.radiotaxilatam.driver.data.remote.AppJson
import com.radiotaxilatam.driver.data.remote.TaxiApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.io.IOException

/** Oferta recibida, con su fecha límite en reloj monotónico local. */
data class ActiveOffer(val offer: RideOffer, val deadlineElapsedMs: Long) {
    fun secondsLeft(now: Long = SystemClock.elapsedRealtime()): Int =
        ((deadlineElapsedMs - now) / 1000).toInt().coerceAtLeast(0)
}

/**
 * Fuente única de verdad del estado del conductor, compartida por la UI y el
 * servicio en segundo plano.
 */
class DriverRepository(
    private val api: TaxiApi,
    private val session: SessionStore,
) {
    private val _driver = MutableStateFlow<Driver?>(null)
    val driver: StateFlow<Driver?> = _driver.asStateFlow()

    private val _currentRide = MutableStateFlow<Ride?>(null)
    val currentRide: StateFlow<Ride?> = _currentRide.asStateFlow()

    private val _offer = MutableStateFlow<ActiveOffer?>(null)
    val offer: StateFlow<ActiveOffer?> = _offer.asStateFlow()

    /** Ofertas que el conductor ya aceptó, rechazó o dejó expirar: no volver a mostrarlas. */
    private val handledOfferIds = mutableSetOf<Long>()

    val isLoggedIn: Boolean get() = session.token.value != null

    suspend fun login(phone: String, password: String): Result<Driver> = safeCall {
        val response = api.login(LoginRequest(phone.trim(), password))
        session.save(response.token)
        _driver.value = response.driver
        response.driver
    }

    suspend fun logout() {
        if (_driver.value?.status != DriverStatus.OFFLINE) {
            safeCall { api.setStatus(StatusRequest(DriverStatus.OFFLINE)) }
        }
        clearLocalSession()
    }

    suspend fun clearLocalSession() {
        session.clear()
        _driver.value = null
        _currentRide.value = null
        _offer.value = null
        handledOfferIds.clear()
    }

    suspend fun refresh(): Result<Unit> = safeCall {
        _driver.value = api.me()
        _currentRide.value = api.currentRide().body()
    }

    suspend fun setAvailable(available: Boolean): Result<Driver> = safeCall {
        val status = if (available) DriverStatus.AVAILABLE else DriverStatus.OFFLINE
        api.setStatus(StatusRequest(status)).also {
            _driver.value = it
            if (!available) _offer.value = null
        }
    }

    /**
     * Consulta la carrera activa y las ofertas pendientes. Devuelve la oferta
     * solo si es nueva, para que el servicio lance la notificación.
     */
    suspend fun poll(): Result<RideOffer?> = safeCall {
        val ride = api.currentRide().body()
            ?.takeIf { it.status != RideStatus.CANCELLED && it.status != RideStatus.COMPLETED }
        if (ride == null && _currentRide.value != null) {
            // La carrera terminó fuera de la app (p. ej. el pasajero canceló): recargar el estado del conductor.
            _driver.value = api.me()
        }
        _currentRide.value = ride
        if (ride != null) {
            _offer.value = null
            return@safeCall null
        }
        if (_driver.value?.status != DriverStatus.AVAILABLE) return@safeCall null

        val offers = api.pendingOffers()
        val current = _offer.value
        if (current != null && offers.none { it.offerId == current.offer.offerId }) {
            _offer.value = null // expiró o se asignó a otro conductor
        }
        if (_offer.value != null) return@safeCall null
        val fresh = offers.firstOrNull { it.offerId !in handledOfferIds } ?: return@safeCall null
        _offer.value = ActiveOffer(fresh, SystemClock.elapsedRealtime() + fresh.expiresInSeconds * 1000L)
        fresh
    }

    suspend fun acceptOffer(offerId: Long): Result<Ride> {
        handledOfferIds += offerId
        _offer.value = null
        return safeCall {
            api.acceptOffer(offerId).also {
                _currentRide.value = it
                _driver.value = _driver.value?.copy(status = DriverStatus.BUSY)
            }
        }
    }

    suspend fun rejectOffer(offerId: Long): Result<Unit> {
        handledOfferIds += offerId
        _offer.value = null
        return safeCall { api.rejectOffer(offerId, RejectRequest()) }
    }

    fun expireOffer(offerId: Long) {
        handledOfferIds += offerId
        if (_offer.value?.offer?.offerId == offerId) _offer.value = null
    }

    suspend fun advanceRide(ride: Ride): Result<Ride> {
        val next = when (ride.status) {
            RideStatus.ACCEPTED -> RideStatus.ARRIVED
            RideStatus.ARRIVED -> RideStatus.IN_PROGRESS
            RideStatus.IN_PROGRESS -> RideStatus.COMPLETED
            else -> return Result.failure(AppException(null, "La carrera no se puede avanzar"))
        }
        return safeCall {
            val updated = api.updateRideStatus(ride.id, RideStatusRequest(next))
            if (next == RideStatus.COMPLETED) {
                _currentRide.value = null
                _driver.value = _driver.value?.copy(status = DriverStatus.AVAILABLE)
            } else {
                _currentRide.value = updated
            }
            updated
        }
    }

    suspend fun history(page: Int, size: Int = 20): Result<Page<Ride>> = safeCall { api.rideHistory(page, size) }

    suspend fun sendLocation(update: LocationUpdate): Result<Unit> = safeCall { api.sendLocation(update) }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(AppException(e.code(), messageFor(e)))
    } catch (e: IOException) {
        Result.failure(AppException(null, "Sin conexión con el servidor. Revisa tu internet."))
    } catch (e: Exception) {
        Result.failure(AppException(null, e.message ?: "Error inesperado"))
    }

    private fun messageFor(e: HttpException): String {
        val body = e.response()?.errorBody()?.string()
        val serverMessage = body?.let { runCatching { AppJson.decodeFromString<ApiError>(it).message }.getOrNull() }
        return serverMessage ?: when (e.code()) {
            401 -> "Sesión no válida. Vuelve a iniciar sesión."
            404 -> "No encontrado"
            409, 410 -> "La carrera ya no está disponible"
            in 500..599 -> "Error del servidor. Inténtalo de nuevo."
            else -> "Error ${e.code()}"
        }
    }
}

class AppException(val httpCode: Int?, override val message: String) : Exception(message)
