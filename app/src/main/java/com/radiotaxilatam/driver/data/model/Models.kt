package com.radiotaxilatam.driver.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class DriverStatus { AVAILABLE, BUSY, OFFLINE }

@Serializable
enum class RideStatus { ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, UNKNOWN }

@Serializable
data class Driver(
    val id: Long,
    val name: String,
    val phone: String,
    val vehiclePlate: String? = null,
    val vehicleModel: String? = null,
    val status: DriverStatus = DriverStatus.OFFLINE,
)

@Serializable
data class Ride(
    val id: Long,
    val status: RideStatus = RideStatus.UNKNOWN,
    val pickupAddress: String,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val dropoffAddress: String? = null,
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val passengerName: String? = null,
    val passengerPhone: String? = null,
    val notes: String? = null,
    val estimatedFare: Double? = null,
    val finalFare: Double? = null,
    val currency: String? = null,
    val createdAt: String? = null,
    val completedAt: String? = null,
)

/** Carrera ofrecida al conductor; expira a los [expiresInSeconds] de recibirla (relativo, para no depender del reloj del móvil). */
@Serializable
data class RideOffer(
    val offerId: Long,
    val ride: Ride,
    val expiresInSeconds: Int = 20,
    val distanceToPickupMeters: Int? = null,
)

@Serializable
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val last: Boolean,
)

@Serializable
data class LoginRequest(val phone: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val driver: Driver)

@Serializable
data class StatusRequest(val status: DriverStatus)

@Serializable
data class LocationUpdate(
    val lat: Double,
    val lng: Double,
    val accuracy: Float? = null,
    val heading: Float? = null,
    val speed: Float? = null,
)

@Serializable
data class RejectRequest(val reason: String? = null)

@Serializable
data class RideStatusRequest(val status: RideStatus, val finalFare: Double? = null)

@Serializable
data class ApiError(val message: String? = null)
