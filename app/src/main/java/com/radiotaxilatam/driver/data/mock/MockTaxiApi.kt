package com.radiotaxilatam.driver.data.mock

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Backend simulado en memoria para probar la app sin el servidor de Spring Boot.
 * Login: cualquier telÃ©fono con contraseÃ±a "1234". Mientras el conductor estÃ¡
 * disponible, llega una carrera nueva cada ~15 s.
 */
class MockTaxiApi : TaxiApi {
    private val mutex = Mutex()
    private var driver = Driver(1, "Carlos Quispe", "+51 987 654 321", "ABC-123", "Toyota Yaris", DriverStatus.OFFLINE)
    private var pendingOffer: Pair<RideOffer, Instant>? = null
    private var nextOfferAt: Instant = Instant.now()
    private var currentRide: Ride? = null
    private var nextId = 1000L
    private val history = mutableListOf<Ride>()

    init {
        repeat(8) { i ->
            val daysAgo = (i + 1).toLong()
            history += randomRide(nextId++).copy(
                status = if (i == 3) RideStatus.CANCELLED else RideStatus.COMPLETED,
                createdAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS).toString(),
                completedAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS).plus(25, ChronoUnit.MINUTES).toString(),
            )
        }
    }

    private suspend fun <T> call(block: () -> T): T {
        delay(Random.nextLong(150, 500))
        return mutex.withLock(action = block)
    }

    override suspend fun login(body: LoginRequest): LoginResponse = call {
        if (body.password != "1234") throw httpError(401, "TelÃ©fono o contraseÃ±a incorrectos")
        LoginResponse("mock-token", driver.copy(phone = body.phone))
    }

    override suspend fun me(): Driver = call { driver }

    override suspend fun setStatus(body: StatusRequest): Driver = call {
        if (currentRide != null && body.status == DriverStatus.OFFLINE) {
            throw httpError(409, "No puedes desconectarte con una carrera activa")
        }
        driver = driver.copy(status = body.status)
        nextOfferAt = Instant.now().plusSeconds(5)
        if (body.status == DriverStatus.OFFLINE) pendingOffer = null
        driver
    }

    override suspend fun sendLocation(body: LocationUpdate) = call { }

    override suspend fun pendingOffers(): List<RideOffer> = call {
        val now = Instant.now()
        pendingOffer?.let { (offer, expires) -> if (now.isAfter(expires)) pendingOffer = null }
        if (pendingOffer == null && driver.status == DriverStatus.AVAILABLE && now.isAfter(nextOfferAt)) {
            val offer = RideOffer(nextId++, randomRide(nextId++), 20, Random.nextInt(300, 2500))
            pendingOffer = offer to now.plusSeconds(offer.expiresInSeconds.toLong())
            nextOfferAt = now.plusSeconds(15)
        }
        listOfNotNull(pendingOffer?.first)
    }

    override suspend fun acceptOffer(offerId: Long): Ride = call {
        val offer = pendingOffer?.first?.takeIf { it.offerId == offerId }
            ?: throw httpError(410, "La carrera ya no estÃ¡ disponible")
        pendingOffer = null
        val ride = offer.ride.copy(status = RideStatus.ACCEPTED)
        currentRide = ride
        driver = driver.copy(status = DriverStatus.BUSY)
        ride
    }

    override suspend fun rejectOffer(offerId: Long, body: RejectRequest) = call {
        if (pendingOffer?.first?.offerId == offerId) pendingOffer = null
        nextOfferAt = Instant.now().plusSeconds(15)
    }

    override suspend fun currentRide(): Response<Ride> = call {
        currentRide?.let { Response.success(it) } ?: Response.success<Ride>(204, null)
    }

    override suspend fun updateRideStatus(rideId: Long, body: RideStatusRequest): Ride = call {
        val ride = currentRide?.takeIf { it.id == rideId } ?: throw httpError(404, "Carrera no encontrada")
        val updated = ride.copy(
            status = body.status,
            finalFare = body.finalFare ?: ride.finalFare,
            completedAt = if (body.status == RideStatus.COMPLETED) Instant.now().toString() else null,
        )
        if (body.status == RideStatus.COMPLETED || body.status == RideStatus.CANCELLED) {
            currentRide = null
            history.add(0, updated.copy(finalFare = updated.finalFare ?: updated.estimatedFare))
            driver = driver.copy(status = DriverStatus.AVAILABLE)
            nextOfferAt = Instant.now().plusSeconds(10)
        } else {
            currentRide = updated
        }
        updated
    }

    override suspend fun rideHistory(page: Int, size: Int): Page<Ride> = call {
        val from = page * size
        val content = history.drop(from).take(size)
        Page(content, page, size, history.size.toLong(), last = from + size >= history.size)
    }

    private fun randomRide(id: Long): Ride {
        val (pickup, pLat, pLng) = PLACES.random()
        val (dropoff, dLat, dLng) = PLACES.filter { it.first != pickup }.random()
        return Ride(
            id = id,
            status = RideStatus.ACCEPTED,
            pickupAddress = pickup,
            pickupLat = pLat,
            pickupLng = pLng,
            dropoffAddress = dropoff,
            dropoffLat = dLat,
            dropoffLng = dLng,
            passengerName = NAMES.random(),
            passengerPhone = "+51 9${Random.nextInt(10_000_000, 99_999_999)}",
            notes = NOTES.random(),
            estimatedFare = Random.nextInt(8, 35).toDouble(),
            currency = "PEN",
            createdAt = Instant.now().toString(),
        )
    }

    private fun httpError(code: Int, message: String) = HttpException(
        Response.error<Any>(code, """{"message":"$message"}""".toResponseBody())
    )

    private companion object {
        val PLACES = listOf(
            Triple("Av. Larco 345, Miraflores", -12.1219, -77.0297),
            Triple("Plaza de Armas, Cercado de Lima", -12.0464, -77.0300),
            Triple("Aeropuerto Jorge ChÃ¡vez, Callao", -12.0219, -77.1143),
            Triple("Jockey Plaza, Santiago de Surco", -12.0850, -76.9772),
            Triple("Parque Kennedy, Miraflores", -12.1211, -77.0300),
            Triple("Av. Javier Prado Este 2465, San Borja", -12.0892, -77.0010),
            Triple("Real Plaza Salaverry, JesÃºs MarÃ­a", -12.0893, -77.0521),
            Triple("Barranco, Puente de los Suspiros", -12.1494, -77.0222),
        )
        val NAMES = listOf("MarÃ­a LÃ³pez", "JosÃ© RamÃ­rez", "Ana Torres", "Luis Mendoza", "Rosa HuamÃ¡n", "Pedro Castillo")
        val NOTES = listOf(null, "Lleva maletas", "Esperar en la puerta principal", "Paga en efectivo", null)
    }
}
