package com.radiotaxilatam.driver.data.mvp

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/*
 * Backend MVP de Joaquín tal como está hoy (repo Joaquincamps/RadioTaxiLatam, rama desarrollo).
 * Los nombres de campos y rutas son los suyos; JoaquinTaxiApi los traduce a los modelos de la app.
 */

@Serializable
enum class TipoUsuario { CLIENTE, CONDUCTOR, DESCONOCIDO }

@Serializable
enum class EstadoViaje { PENDIENTE, BUSCANDO_CONDUCTOR, ASIGNADO, ACEPTADO, EN_CURSO, FINALIZADO, CANCELADO, DESCONOCIDO }

@Serializable
data class Usuario(
    val id: Long,
    val nombre: String? = null,
    val telefono: String? = null,
    val tipo: TipoUsuario = TipoUsuario.DESCONOCIDO,
)

/** Viaje tal como lo devuelve /viaje/listar/viajes (con cliente y conductor anidados). */
@Serializable
data class Viaje(
    val id: Long,
    val cliente: Usuario? = null,
    val conductor: Usuario? = null,
    val origen: String = "",
    val destino: String? = null,
    val estado: EstadoViaje = EstadoViaje.DESCONOCIDO,
)

/** Viaje tal como lo devuelven las acciones (aceptar, iniciar...): solo ids. */
@Serializable
data class ViajeDTO(
    val id: Long,
    val clienteId: Long? = null,
    val conductorId: Long? = null,
    val origen: String = "",
    val destino: String? = null,
    val estado: EstadoViaje = EstadoViaje.DESCONOCIDO,
)

interface JoaquinApi {
    @GET("usuarios/listar")
    suspend fun listarUsuarios(): List<Usuario>

    @GET("usuarios/usuario/{id}")
    suspend fun usuario(@Path("id") id: Long): Usuario

    @GET("viaje/listar/viajes")
    suspend fun listarViajes(): List<Viaje>

    @POST("viaje/aceptar/viaje/{viajeId}/conductor/asignado/{conductorId}")
    suspend fun aceptar(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO

    @POST("viaje/iniciar/viaje/{viajeId}/conductor/iniciaViaje/{conductorId}")
    suspend fun iniciar(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO

    @POST("viaje/finalizar/viaje/{viajeId}/conductor/finalizaViaje/{conductorId}")
    suspend fun finalizar(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO

    @POST("viaje/cancelar/viaje/{viajeId}/conductor/cancelaViaje/{conductorId}")
    suspend fun cancelar(@Path("viajeId") viajeId: Long, @Path("conductorId") conductorId: Long): ViajeDTO
}
