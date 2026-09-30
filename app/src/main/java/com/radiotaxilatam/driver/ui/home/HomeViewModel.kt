package com.radiotaxilatam.driver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radiotaxilatam.driver.data.ActiveOffer
import com.radiotaxilatam.driver.data.DriverRepository
import com.radiotaxilatam.driver.data.model.Driver
import com.radiotaxilatam.driver.data.model.Ride
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: DriverRepository) : ViewModel() {
    val driver: StateFlow<Driver?> = repository.driver
    val currentRide: StateFlow<Ride?> = repository.currentRide
    val offer: StateFlow<ActiveOffer?> = repository.offer

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() = launchAction { repository.refresh() }

    fun setAvailable(available: Boolean) = launchAction { repository.setAvailable(available) }

    fun accept(offerId: Long) = launchAction { repository.acceptOffer(offerId) }

    fun reject(offerId: Long) = launchAction { repository.rejectOffer(offerId) }

    fun expire(offerId: Long) {
        repository.expireOffer(offerId)
        _messages.trySend("La oferta expiró")
    }

    fun advance(ride: Ride) = launchAction { repository.advanceRide(ride) }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
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
