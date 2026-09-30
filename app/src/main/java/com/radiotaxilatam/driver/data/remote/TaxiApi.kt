package com.radiotaxilatam.driver.data.remote

import com.radiotaxilatam.driver.data.model.Driver
import com.radiotaxilatam.driver.data.model.LocationUpdate
import com.radiotaxilatam.driver.data.model.LoginRequest
import com.radiotaxilatam.driver.data.model.LoginResponse
import com.radiotaxilatam.driver.data.model.Page
import com.radiotaxilatam.driver.data.model.RejectRequest
import com.radiotaxilatam.driver.data.model.Ride
import com.radiotaxilatam.driver.data.model.RideOffer
import com.radiotaxilatam.driver.data.model.RideStatusRequest
import com.radiotaxilatam.driver.data.model.StatusRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato con el backend Spring Boot. Ver API_CONTRACT.md en la raíz del repo. */
interface TaxiApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/drivers/me")
    suspend fun me(): Driver

    @PUT("api/drivers/me/status")
    suspend fun setStatus(@Body body: StatusRequest): Driver

    @POST("api/drivers/me/location")
    suspend fun sendLocation(@Body body: LocationUpdate)

    @GET("api/drivers/me/offers")
    suspend fun pendingOffers(): List<RideOffer>

    @POST("api/offers/{offerId}/accept")
    suspend fun acceptOffer(@Path("offerId") offerId: Long): Ride

    @POST("api/offers/{offerId}/reject")
    suspend fun rejectOffer(@Path("offerId") offerId: Long, @Body body: RejectRequest)

    /** 200 con la carrera activa, o 204 si no hay ninguna. */
    @GET("api/drivers/me/current-ride")
    suspend fun currentRide(): Response<Ride>

    @POST("api/rides/{rideId}/status")
    suspend fun updateRideStatus(@Path("rideId") rideId: Long, @Body body: RideStatusRequest): Ride

    @GET("api/drivers/me/rides")
    suspend fun rideHistory(@Query("page") page: Int, @Query("size") size: Int): Page<Ride>
}
