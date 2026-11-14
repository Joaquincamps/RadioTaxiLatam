package com.radiotaxilatam.driver.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

/*
 * Objetos JSON del backend de Joaquín (repo Joaquincamps/RadioTaxiLatam, rama desarrollo),
 * con sus mismos nombres de clase, campos y enums. Si él cambia un DTO, se cambia aquí igual.
 */

@Serializable
enum class TipoUsuario { CLIENTE, CONDUCTOR, DESCONOCIDO }

@Serializable
enum class EstadoConductor { AVAILABLE, BUSY, OFFLINE }

@Serializable
enum class EstadoViaje { PENDIENTE, BUSCANDO_CONDUCTOR, ASIGNADO, ACEPTADO, LLEGADO, EN_CURSO, FINALIZADO, CANCELADO, DESCONOCIDO }

@Serializable
data class LoginDTO(val movil: String, val password: String)

@Serializable
data class ConductorDTO(
    val id: Long,
    val nombre: String? = null,
    val telefono: String? = null,
    val matricula: String? = null,
    val modeloVehiculo: String? = null,
    val estado: EstadoConductor = EstadoConductor.OFFLINE,
)

@Serializable
data class EstadoConductorDTO(val estado: EstadoConductor)

@Serializable
data class UbicacionDTO(val latitud: Double, val longitud: Double)

/** Viaje tal como lo devuelven las acciones (crear, aceptar, iniciar...): solo ids, sin datos del cliente. */
@Serializable
data class ViajeDTO(
    val id: Long,
    val clienteId: Long? = null,
    val conductorId: Long? = null,
    val origen: String = "",
    val destino: String? = null,
    val estado: EstadoViaje = EstadoViaje.DESCONOCIDO,
    val latitudOrigen: Double? = null,
    val longitudOrigen: Double? = null,
    // Aún no los manda su backend (su ViajeDTO no los tiene); si los añade, la app ya no necesita geocodificar el destino.
    val latitudDestino: Double? = null,
    val longitudDestino: Double? = null,
)

/** Oferta de un viaje a este conductor. [segundosRestantes] es relativo, no depende del reloj del móvil. */
@Serializable
data class OfertaViajeDto(
    val id: Long,
    val viaje: ViajeDTO,
    val segundosRestantes: Int = 20,
    val distanciaRecogidaMetros: Int? = null,
)

/** Entidad Usuario tal como la devuelve GET /usuarios/listar (y anidada dentro de cada Viaje). */
@Serializable
data class Usuario(
    val id: Long,
    val nombre: String? = null,
    val telefono: String? = null,
    val tipo: TipoUsuario = TipoUsuario.DESCONOCIDO,
    val estado: EstadoConductor = EstadoConductor.OFFLINE,
    val matricula: String? = null,
    val modeloVehiculo: String? = null,
)

/** Entidad Viaje tal como la devuelve GET /viaje/listar/viajes, con cliente y conductor anidados. */
@Serializable
data class Viaje(
    val id: Long,
    val cliente: Usuario? = null,
    val conductor: Usuario? = null,
    val origen: String = "",
    val destino: String? = null,
    val latitudOrigen: Double? = null,
    val longitudOrigen: Double? = null,
    val latitudDestino: Double? = null,
    val longitudDestino: Double? = null,
    val estado: EstadoViaje = EstadoViaje.DESCONOCIDO,
    /** Cuándo se pidió el viaje (epoch ms). Solo lo tienen los viajes creados desde el commit c4b44ac. */
    @Serializable(with = InstanteSerializer::class)
    val creadoEn: Long? = null,
    /** Cuándo se finalizó (epoch ms); null si se canceló. Su backend no guarda cuándo se aceptó ni se inició. */
    @Serializable(with = InstanteSerializer::class)
    val finalizadoEn: Long? = null,
)

/**
 * Los `Instant` de su backend llegan como texto ISO-8601 ("2026-11-13T12:21:32.123Z"). Por si su Jackson
 * los mandara como número (segundos con decimales), también se aceptan. Si no se entiende, queda null
 * en vez de romper toda la lista de viajes.
 */
object InstanteSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instante", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): Long? {
        val valor = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive ?: return null
        if (valor is JsonNull) return null
        if (valor.isString) return runCatching { Instant.parse(valor.content).toEpochMilli() }.getOrNull()
        return valor.doubleOrNull?.let { (it * 1000).toLong() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(Instant.ofEpochMilli(value).toString())
    }
}

fun Usuario.aConductorDTO() = ConductorDTO(id, nombre, telefono, matricula, modeloVehiculo, estado)

/** Cuerpo de error de Spring Boot. Solo trae `message` si el backend tiene server.error.include-message=always. */
@Serializable
data class ErrorDTO(val message: String? = null, val error: String? = null)
