package com.radiotaxilatam.driver.data.remote

import com.radiotaxilatam.driver.data.model.ConductorDTO
import com.radiotaxilatam.driver.data.model.EstadoConductorDTO
import com.radiotaxilatam.driver.data.model.LoginDTO
import com.radiotaxilatam.driver.data.model.OfertaViajeDto
import com.radiotaxilatam.driver.data.model.UbicacionDTO
import com.radiotaxilatam.driver.data.model.Usuario
import com.radiotaxilatam.driver.data.model.Viaje
import com.radiotaxilatam.driver.data.model.ViajeDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rutas del backend de Joaquín tal cual, con el nombre de su método en el controlador.
 * UsuarioController lleva @RequestMapping("/usuarios") y ViajeController ("/viaje"),
 * por eso login, estado y ubicación van detrás de /usuarios. OfertaViajeControlador no tiene prefijo.
 * Implementaciones: Retrofit (ApiFactory) y MockTaxiApi (modo demo).
 */
interface TaxiApi {
    // --- UsuarioController ---

    @POST("usuarios/api/auth/login")
    suspend fun login(@Body loginDTO: LoginDTO): ConductorDTO

    @PUT("usuarios/api/drivers/{id}/status")
    suspend fun cambiarDisponibilidad(@Path("id") id: Long, @Body estadoConductorDTO: EstadoConductorDTO): ConductorDTO

    @POST("usuarios/api/drivers/{id}/location")
    suspend fun enviarUbicacion(@Path("id") id: Long, @Body ubicacionDTO: UbicacionDTO): UbicacionDTO

    @GET("usuarios/listar")
    suspend fun obtenerTodosLosUsuarios(): List<Usuario>

    // --- OfertaViajeControlador ---

    @GET("api/drivers/{id}/offers")
    suspend fun obtenerOfertas(@Path("id") id: Long): List<OfertaViajeDto>

    @POST("api/offers/{ofertaId}/accept")
    suspend fun aceptarOferta(@Path("ofertaId") ofertaId: Long, @Query("conductorId") conductorId: Long): ViajeDTO

    @POST("api/offers/{ofertaId}/reject")
    suspend fun rechazarOferta(@Path("ofertaId") ofertaId: Long, @Query("conductorId") conductorId: Long): Response<Unit>

    // --- ViajeController ---

    @GET("viaje/listar/viajes")
    suspend fun obtenerTodosLosViajes(): List<Viaje>

    @POST("viaje/iniciar/viaje/{viajeId}/conductor/iniciaViaje/{conductorId}")
    suspend fun iniciarViaje(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO

    @POST("viaje/finalizar/viaje/{viajeId}/conductor/finalizaViaje/{conductorId}")
    suspend fun finalizarViaje(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO

    @POST("viaje/cancelar/viaje/{viajeId}/conductor/cancelaViaje/{conductorId}")
    suspend fun cancelarViaje(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO
}
