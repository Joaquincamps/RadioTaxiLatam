package com.radiotaxilatam.driver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radiotaxilatam.driver.data.ConductorRepositorio
import com.radiotaxilatam.driver.data.OfertaActiva
import com.radiotaxilatam.driver.data.model.ConductorDTO
import com.radiotaxilatam.driver.data.model.Viaje
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repositorio: ConductorRepositorio) : ViewModel() {
    val conductor: StateFlow<ConductorDTO?> = repositorio.conductor
    val viajeActual: StateFlow<Viaje?> = repositorio.viajeActual
    val viajeLlegadoId: StateFlow<Long?> = repositorio.viajeLlegadoId
    val oferta: StateFlow<OfertaActiva?> = repositorio.oferta

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refrescar()
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
