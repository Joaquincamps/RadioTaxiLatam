package com.radiotaxilatam.driver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radiotaxilatam.driver.data.ConductorRepositorio
import com.radiotaxilatam.driver.data.OfertaActiva
import com.radiotaxilatam.driver.data.geo.Punto
import com.radiotaxilatam.driver.data.geo.Ruta
import com.radiotaxilatam.driver.data.geo.RutaServicio
import com.radiotaxilatam.driver.data.model.ConductorDTO
import com.radiotaxilatam.driver.data.model.Viaje
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Ruta de un viaje concreto; [ruta] es null mientras se calcula o si no hay coordenadas para el destino. */
data class RutaDeViaje(val viajeId: Long, val calculando: Boolean, val ruta: Ruta?)

class HomeViewModel(
    private val repositorio: ConductorRepositorio,
    private val rutas: RutaServicio,
) : ViewModel() {
    val conductor: StateFlow<ConductorDTO?> = repositorio.conductor
    val viajeActual: StateFlow<Viaje?> = repositorio.viajeActual
    val oferta: StateFlow<OfertaActiva?> = repositorio.oferta
    val ubicacion: StateFlow<Punto?> = repositorio.ubicacion
    val descansoDesde: StateFlow<Long?> = repositorio.descansoDesde

    private val _rutaOferta = MutableStateFlow<RutaDeViaje?>(null)
    val rutaOferta: StateFlow<RutaDeViaje?> = _rutaOferta

    private val _rutaViaje = MutableStateFlow<RutaDeViaje?>(null)
    val rutaViaje: StateFlow<RutaDeViaje?> = _rutaViaje

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refrescar()
        // Distancia del viaje ofrecido, para que el conductor sepa cuánto recorrido es antes de aceptar.
        viewModelScope.launch {
            repositorio.oferta.distinctUntilChangedBy { it?.oferta?.id }.collectLatest { activa ->
                val v = activa?.oferta?.viaje
                if (v == null) {
                    _rutaOferta.value = null
                    return@collectLatest
                }
                _rutaOferta.value = RutaDeViaje(v.id, calculando = true, ruta = null)
                val ruta = rutas.rutaDeViaje(v.origen, v.latitudOrigen, v.longitudOrigen, v.destino, v.latitudDestino, v.longitudDestino)
                _rutaOferta.value = RutaDeViaje(v.id, calculando = false, ruta = ruta)
            }
        }
        // Ruta del viaje en curso para el mapa; su distancia se guarda para el historial.
        viewModelScope.launch {
            repositorio.viajeActual.distinctUntilChangedBy { it?.id }.collectLatest { v ->
                if (v == null) {
                    _rutaViaje.value = null
                    return@collectLatest
                }
                _rutaViaje.value = RutaDeViaje(v.id, calculando = true, ruta = null)
                val ruta = rutas.rutaDeViaje(v.origen, v.latitudOrigen, v.longitudOrigen, v.destino, v.latitudDestino, v.longitudDestino)
                _rutaViaje.value = RutaDeViaje(v.id, calculando = false, ruta = ruta)
                ruta?.let { repositorio.guardarDistancia(v.id, it.distanciaMetros) }
            }
        }
    }

    fun refrescar() = launchAction { repositorio.refrescar() }

    fun cambiarDisponibilidad(disponible: Boolean) = launchAction { repositorio.cambiarDisponibilidad(disponible) }

    fun aceptar(ofertaId: Long) = launchAction { repositorio.aceptarOferta(ofertaId) }

    fun rechazar(ofertaId: Long) = launchAction { repositorio.rechazarOferta(ofertaId) }

    fun caducar(ofertaId: Long) {
        _messages.trySend("La oferta expiró")
        viewModelScope.launch { repositorio.ofertaCaducada(ofertaId) }
    }

    fun avanzar(viaje: Viaje) = launchAction { repositorio.avanzarViaje(viaje) }

    fun cancelar(viaje: Viaje) = launchAction {
        repositorio.cancelarViaje(viaje).onSuccess { _messages.send("Carrera cancelada") }
    }

    fun devolver(viaje: Viaje) = launchAction {
        repositorio.devolverViaje(viaje).onSuccess { _messages.send("Carrera devuelta: la central buscará otro taxi") }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repositorio.logout()
            onDone()
        }
    }

    private fun launchAction(block: suspend () -> Result<*>) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            block().onFailure { _messages.send(it.message ?: "Error") }
            _working.value = false
        }
    }
}
