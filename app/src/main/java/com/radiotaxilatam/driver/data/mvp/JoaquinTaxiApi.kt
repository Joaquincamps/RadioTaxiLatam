package com.radiotaxilatam.driver.data.mvp

import com.radiotaxilatam.driver.data.SessionStore
import com.radiotaxilatam.driver.data.model.Driver
import com.radiotaxilatam.driver.data.model.DriverStatus
import com.radiotaxilatam.driver.data.model.LocationUpdate
import com.radiotaxilatam.driver.data.model.LoginRequest
import com.radiotaxilatam.driver.data.model.LoginResponse
import com.radiotaxilatam.driver.data.model.Page
import com.radiotaxilatam.driver.data.model.RejectRequest
import com.radiotaxilatam.driver.data.model.Ride
import com.radiotaxilatam.driver.data.model.RideOffer
import com.radiotaxilatam.driver.data.model.RideStatus
import com.radiotaxilatam.driver.data.model.RideStatusRequest
import com.radiotaxilatam.driver.data.model.StatusRequest
import com.radiotaxilatam.driver.data.remote.TaxiApi
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Adapta el backend MVP de Joaquín al contrato que usa la app.
 *
 * Lo que su backend aún no tiene se simula en el móvil:
 * - Login: se busca el conductor por teléfono en /usuarios/listar (sin contraseña).
 * - Disponible / No disponible: solo local; si está "No disponible" no se muestran viajes.
 * - Ofertas: viajes en estado ASIGNADO a este conductor.
 * - Rechazar: solo oculta el viaje en este móvil (cancelar en su backend anularía el viaje del cliente).
 * - "Llegué": paso solo local entre ACEPTADO y EN_CURSO.
 * - Ubicación: no se envía.
 */
class JoaquinTaxiApi(
    private val api: JoaquinApi,
    private val session: SessionStore,
) : TaxiApi {

    private var available = false
    private val arrivedRideIds = mutableSetOf<Long>()

    private val driverId: Long
        get() = session.token.value?.removePrefix(TOKEN_PREFIX)?.toLongOrNull()
            ?: throw httpError(401, "Sesión no válida. Vuelve a iniciar sesión.")

    override suspend fun login(body: LoginRequest): LoginResponse {
        val phone = digits(body.phone)
        val conductor = api.listarUsuarios().firstOrNull {
            it.tipo == TipoUsuario.CONDUCTOR && phoneMatches(digits(it.telefono), phone)
        } ?: throw httpError(401, "No hay ningún conductor registrado con ese teléfono")
        available = false
        return LoginResponse("$TOKEN_PREFIX${conductor.id}", conductor.toDriver(DriverStatus.OFFLINE))
    }

    override suspend fun me(): Driver {
        val usuario = api.usuario(driverId)
        return usuario.toDriver(currentStatus(api.listarViajes()))
    }

    override suspend fun setStatus(body: StatusRequest): Driver {
        val viajes = api.listarViajes()
        if (body.status == DriverStatus.OFFLINE && activeViaje(viajes) != null) {
            throw httpError(409, "No puedes desconectarte con una carrera activa")
        }
        available = body.status == DriverStatus.AVAILABLE
        return api.usuario(driverId).toDriver(currentStatus(viajes))
    }

    override suspend fun sendLocation(body: LocationUpdate) = Unit

    override suspend fun pendingOffers(): List<RideOffer> {
        if (!available) return emptyList()
        val me = driverId
        return api.listarViajes()
            .filter { it.conductor?.id == me && it.estado == EstadoViaje.ASIGNADO }
            .sortedBy { it.id }
            .map { RideOffer(offerId = it.id, ride = it.toRide(), expiresInSeconds = OFFER_SECONDS) }
    }

    override suspend fun acceptOffer(offerId: Long): Ride {
        val dto = api.aceptar(offerId, driverId)
        return findViaje(dto.id)?.toRide() ?: dto.toRide()
    }

    override suspend fun rejectOffer(offerId: Long, body: RejectRequest) = Unit

    override suspend fun currentRide(): Response<Ride> {
        val viaje = activeViaje(api.listarViajes())
        return if (viaje != null) Response.success(viaje.toRide()) else Response.success<Ride>(204, null)
    }

    override suspend fun updateRideStatus(rideId: Long, body: RideStatusRequest): Ride {
        val me = driverId
        val dto = when (body.status) {
            RideStatus.ARRIVED -> {
                arrivedRideIds += rideId
                return findViaje(rideId)?.toRide() ?: throw httpError(404, "Carrera no encontrada")
            }
            RideStatus.IN_PROGRESS -> api.iniciar(rideId, me)
            RideStatus.COMPLETED -> api.finalizar(rideId, me)
            RideStatus.CANCELLED -> api.cancelar(rideId, me)
            else -> throw httpError(400, "Estado no soportado")
        }
        if (body.status != RideStatus.IN_PROGRESS) arrivedRideIds -= rideId
        return findViaje(dto.id)?.toRide() ?: dto.toRide()
    }

    override suspend fun rideHistory(page: Int, size: Int): Page<Ride> {
        val me = driverId
        val done = api.listarViajes()
            .filter { it.conductor?.id == me && (it.estado == EstadoViaje.FINALIZADO || it.estado == EstadoViaje.CANCELADO) }
            .sortedByDescending { it.id }
        val from = page * size
        return Page(done.drop(from).take(size).map { it.toRide() }, page, size, done.size.toLong(), from + size >= done.size)
    }

    private suspend fun findViaje(id: Long): Viaje? = api.listarViajes().firstOrNull { it.id == id }

    private fun activeViaje(viajes: List<Viaje>): Viaje? = viajes.firstOrNull {
        it.conductor?.id == driverId && (it.estado == EstadoViaje.ACEPTADO || it.estado == EstadoViaje.EN_CURSO)
    }

    private fun currentStatus(viajes: List<Viaje>): DriverStatus = when {
        activeViaje(viajes) != null -> DriverStatus.BUSY
        available -> DriverStatus.AVAILABLE
        else -> DriverStatus.OFFLINE
    }

    private fun Usuario.toDriver(status: DriverStatus) = Driver(
        id = id,
        name = nombre ?: "Conductor",
        phone = telefono.orEmpty(),
        status = status,
    )

    private fun Viaje.toRide() = Ride(
        id = id,
        status = rideStatus(id, estado),
        pickupAddress = origen,
        dropoffAddress = destino,
        passengerName = cliente?.nombre,
        passengerPhone = cliente?.telefono,
    )

    private fun ViajeDTO.toRide() = Ride(
        id = id,
        status = rideStatus(id, estado),
        pickupAddress = origen,
        dropoffAddress = destino,
    )

    private fun rideStatus(id: Long, estado: EstadoViaje): RideStatus = when (estado) {
        EstadoViaje.ACEPTADO -> if (id in arrivedRideIds) RideStatus.ARRIVED else RideStatus.ACCEPTED
        EstadoViaje.EN_CURSO -> RideStatus.IN_PROGRESS
        EstadoViaje.FINALIZADO -> RideStatus.COMPLETED
        EstadoViaje.CANCELADO -> RideStatus.CANCELLED
        else -> RideStatus.UNKNOWN
    }

    private companion object {
        const val TOKEN_PREFIX = "mvp:"
        const val OFFER_SECONDS = 45

        fun digits(phone: String?) = phone.orEmpty().filter(Char::isDigit)

        /** "988776655" coincide con "+51 988 776 655". */
        fun phoneMatches(a: String, b: String) =
            a.isNotEmpty() && b.isNotEmpty() && (a == b || a.endsWith(b) || b.endsWith(a)) && minOf(a.length, b.length) >= 7

        fun httpError(code: Int, message: String) =
            HttpException(Response.error<Any>(code, """{"message":"$message"}""".toResponseBody()))
    }
}
