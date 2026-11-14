package com.radiotaxilatam.driver.data.mock

import com.radiotaxilatam.driver.data.model.ConductorDTO
import com.radiotaxilatam.driver.data.model.EstadoConductor
import com.radiotaxilatam.driver.data.model.EstadoConductorDTO
import com.radiotaxilatam.driver.data.model.EstadoViaje
import com.radiotaxilatam.driver.data.model.LoginDTO
import com.radiotaxilatam.driver.data.model.OfertaViajeDto
import com.radiotaxilatam.driver.data.model.TipoUsuario
import com.radiotaxilatam.driver.data.model.UbicacionDTO
import com.radiotaxilatam.driver.data.model.Usuario
import com.radiotaxilatam.driver.data.model.Viaje
import com.radiotaxilatam.driver.data.model.ViajeDTO
import com.radiotaxilatam.driver.data.remote.TaxiApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Backend simulado en memoria que se comporta como el de Joaquín (mismas rutas, DTOs y estados),
 * incluidas sus rarezas: el login pone al conductor AVAILABLE, los errores de login son 500 y
 * cancelar el viaje deja al conductor en BUSY (devolverlo, en cambio, lo deja AVAILABLE).
 * Para iniciar el viaje hay que registrar antes la llegada.
 * Login: cualquier teléfono con contraseña "1234". Mientras el conductor está AVAILABLE
 * llega una oferta nueva cada ~15 s.
 */
class MockTaxiApi : TaxiApi {
    private val mutex = Mutex()
    private var conductor = Usuario(1, "Carlos Quispe", "+51987654321", TipoUsuario.CONDUCTOR, EstadoConductor.OFFLINE, "ABC-123", "Toyota Yaris")
    private val viajes = mutableListOf<Viaje>()
    private var ofertaPendiente: Pair<OfertaViajeDto, Instant>? = null
    private var siguienteOfertaEn: Instant = Instant.now()
    private var siguienteId = 1000L

    init {
        // Historial de ejemplo: un viaje cada ~3 h hacia atrás, de unos 25 min.
        val ahora = System.currentTimeMillis()
        repeat(8) { i ->
            val creado = ahora - (8 - i) * 3 * 3_600_000L
            val cancelado = i == 3
            viajes += viajeAleatorio(siguienteId++).copy(
                conductor = conductor,
                estado = if (cancelado) EstadoViaje.CANCELADO else EstadoViaje.FINALIZADO,
                creadoEn = creado,
                finalizadoEn = if (cancelado) null else creado + 25 * 60_000L,
            )
        }
    }

    private suspend fun <T> call(block: () -> T): T {
        delay(Random.nextLong(150, 500))
        return mutex.withLock(action = block)
    }

    override suspend fun login(loginDTO: LoginDTO): ConductorDTO = call {
        if (loginDTO.password != "1234") throw httpError(500, "Contraseña o telefono incorrecto.")
        if (conductor.estado != EstadoConductor.BUSY) conductor = conductor.copy(estado = EstadoConductor.AVAILABLE)
        conductor = conductor.copy(telefono = loginDTO.movil)
        siguienteOfertaEn = Instant.now().plusSeconds(5)
        conductorDTO()
    }

    override suspend fun cambiarDisponibilidad(id: Long, estadoConductorDTO: EstadoConductorDTO): ConductorDTO = call {
        if (estadoConductorDTO.estado == EstadoConductor.BUSY) throw httpError(400, "Solo puedes cambiar a Available o Offline.")
        if (viajeActivo() != null) throw httpError(409, "No se puede cambiar tu disponibilidad con una carrera activa")
        conductor = conductor.copy(estado = estadoConductorDTO.estado)
        siguienteOfertaEn = Instant.now().plusSeconds(5)
        if (estadoConductorDTO.estado == EstadoConductor.OFFLINE) ofertaPendiente = null
        conductorDTO()
    }

    override suspend fun enviarUbicacion(id: Long, ubicacionDTO: UbicacionDTO): UbicacionDTO = call { ubicacionDTO }

    override suspend fun obtenerTodosLosUsuarios(): List<Usuario> = call { listOf(conductor) }

    override suspend fun obtenerOfertas(id: Long): List<OfertaViajeDto> = call {
        val ahora = Instant.now()
        ofertaPendiente?.let { (_, expira) -> if (ahora.isAfter(expira)) ofertaPendiente = null }
        if (ofertaPendiente == null && conductor.estado == EstadoConductor.AVAILABLE && ahora.isAfter(siguienteOfertaEn)) {
            val viaje = viajeAleatorio(siguienteId++).copy(estado = EstadoViaje.BUSCANDO_CONDUCTOR)
            viajes += viaje
            val oferta = OfertaViajeDto(siguienteId++, viaje.aDTO(), 20, Random.nextInt(300, 2500))
            ofertaPendiente = oferta to ahora.plusSeconds(20)
            siguienteOfertaEn = ahora.plusSeconds(15)
        }
        listOfNotNull(ofertaPendiente?.let { (oferta, expira) ->
            oferta.copy(segundosRestantes = Duration.between(ahora, expira).seconds.toInt())
        })
    }

    override suspend fun aceptarOferta(ofertaId: Long, conductorId: Long): ViajeDTO = call {
        val oferta = ofertaPendiente?.first?.takeIf { it.id == ofertaId }
            ?: throw httpError(410, "La oferta ya no está pendiente.")
        ofertaPendiente = null
        conductor = conductor.copy(estado = EstadoConductor.BUSY)
        actualizar(oferta.viaje.id) { it.copy(conductor = conductor, estado = EstadoViaje.ACEPTADO) }.aDTO()
    }

    override suspend fun rechazarOferta(ofertaId: Long, conductorId: Long): Response<Unit> = call {
        val oferta = ofertaPendiente?.first?.takeIf { it.id == ofertaId }
            ?: throw httpError(410, "La oferta ha caducado.")
        ofertaPendiente = null
        viajes.removeAll { it.id == oferta.viaje.id } // se lo lleva "otro conductor"
        siguienteOfertaEn = Instant.now().plusSeconds(15)
        Response.success(Unit)
    }

    override suspend fun obtenerTodosLosViajes(): List<Viaje> = call { viajes.toList() }

    override suspend fun registrarLlegada(viajeId: Long, conductorId: Long): ViajeDTO = call {
        val viaje = viajes.firstOrNull { it.id == viajeId } ?: throw httpError(404, "El viaje no existe")
        when (viaje.estado) {
            EstadoViaje.LLEGADO -> viaje.aDTO()
            EstadoViaje.ACEPTADO -> actualizar(viajeId) { it.copy(estado = EstadoViaje.LLEGADO) }.aDTO()
            else -> throw httpError(409, "Solo puedes registrar la llegada de un viaje aceptado.")
        }
    }

    override suspend fun iniciarViaje(viajeId: Long, conductorId: Long): ViajeDTO = call {
        val viaje = viajes.firstOrNull { it.id == viajeId } ?: throw httpError(500, "El viaje no existe.")
        if (viaje.estado != EstadoViaje.LLEGADO) throw httpError(409, "Debes registrar la llegada antes de iniciar el viaje.")
        actualizar(viajeId) { it.copy(estado = EstadoViaje.EN_CURSO) }.aDTO()
    }

    override suspend fun finalizarViaje(viajeId: Long, conductorId: Long): ViajeDTO = call {
        val viaje = viajes.firstOrNull { it.id == viajeId } ?: throw httpError(404, "El viaje no existe.")
        if (viaje.estado != EstadoViaje.EN_CURSO) throw httpError(500, "El estado del viaje tiene que ser aceptado")
        conductor = conductor.copy(estado = EstadoConductor.AVAILABLE)
        actualizar(viajeId) { it.copy(estado = EstadoViaje.FINALIZADO, finalizadoEn = System.currentTimeMillis()) }.aDTO()
    }

    override suspend fun cancelarViaje(viajeId: Long, conductorId: Long): ViajeDTO = call {
        val viaje = viajes.firstOrNull { it.id == viajeId } ?: throw httpError(404, "El viaje no existe.")
        if (viaje.conductor?.id != conductorId) throw httpError(403, "El viaje no pertenece a este conductor.")
        actualizar(viajeId) { it.copy(estado = EstadoViaje.CANCELADO) }.aDTO()
    }

    override suspend fun devolverViaje(viajeId: Long, conductorId: Long): Response<Unit> = call {
        val viaje = viajes.firstOrNull { it.id == viajeId } ?: throw httpError(404, "Viaje no encontrado.")
        if (viaje.conductor?.id != conductorId) throw httpError(403, "El viaje no pertenece a este conductor.")
        if (viaje.estado != EstadoViaje.ACEPTADO && viaje.estado != EstadoViaje.LLEGADO) {
            throw httpError(409, "Solo puedes devolver un viaje aceptado o en recogida.")
        }
        // Vuelve a buscar conductor y se lo lleva "otro conductor": a este no se le vuelve a ofrecer.
        viajes.removeAll { it.id == viajeId }
        conductor = conductor.copy(estado = EstadoConductor.AVAILABLE)
        siguienteOfertaEn = Instant.now().plusSeconds(15)
        Response.success<Unit>(204, Unit)
    }

    private fun viajeActivo() = viajes.firstOrNull {
        it.conductor?.id == conductor.id &&
            (it.estado == EstadoViaje.ACEPTADO || it.estado == EstadoViaje.LLEGADO || it.estado == EstadoViaje.EN_CURSO)
    }

    private fun actualizar(viajeId: Long, cambio: (Viaje) -> Viaje): Viaje {
        val i = viajes.indexOfFirst { it.id == viajeId }
        if (i < 0) throw httpError(500, "El viaje no existe.")
        return cambio(viajes[i]).also { viajes[i] = it }
    }

    private fun conductorDTO() = ConductorDTO(
        conductor.id, conductor.nombre, conductor.telefono, conductor.matricula, conductor.modeloVehiculo, conductor.estado,
    )

    private fun Viaje.aDTO() =
        ViajeDTO(id, cliente?.id, this.conductor?.id, origen, destino, estado, latitudOrigen, longitudOrigen, latitudDestino, longitudDestino)

    private fun viajeAleatorio(id: Long): Viaje {
        val (origen, oLat, oLng) = LUGARES.random()
        val (destino, dLat, dLng) = LUGARES.filter { it.first != origen }.random()
        return Viaje(
            id = id,
            cliente = Usuario(
                id = 500 + id,
                nombre = NOMBRES.random(),
                telefono = "+519${Random.nextInt(10_000_000, 99_999_999)}",
                tipo = TipoUsuario.CLIENTE,
            ),
            origen = origen,
            destino = destino,
            latitudOrigen = oLat,
            longitudOrigen = oLng,
            latitudDestino = dLat,
            longitudDestino = dLng,
            estado = EstadoViaje.BUSCANDO_CONDUCTOR,
            creadoEn = System.currentTimeMillis(),
        )
    }

    private fun httpError(code: Int, message: String) = HttpException(
        Response.error<Any>(code, """{"status":$code,"message":"$message"}""".toResponseBody())
    )

    private companion object {
        val LUGARES = listOf(
            Triple("Av. Larco 345, Miraflores", -12.1219, -77.0297),
            Triple("Plaza de Armas, Cercado de Lima", -12.0464, -77.0300),
            Triple("Aeropuerto Jorge Chávez, Callao", -12.0219, -77.1143),
            Triple("Jockey Plaza, Santiago de Surco", -12.0850, -76.9772),
            Triple("Parque Kennedy, Miraflores", -12.1211, -77.0300),
            Triple("Av. Javier Prado Este 2465, San Borja", -12.0892, -77.0010),
            Triple("Real Plaza Salaverry, Jesús María", -12.0893, -77.0521),
            Triple("Barranco, Puente de los Suspiros", -12.1494, -77.0222),
        )
        val NOMBRES = listOf("María López", "José Ramírez", "Ana Torres", "Luis Mendoza", "Rosa Huamán", "Pedro Castillo")
    }
}
