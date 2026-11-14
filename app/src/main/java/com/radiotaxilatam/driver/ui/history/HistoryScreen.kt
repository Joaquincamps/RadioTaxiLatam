package com.radiotaxilatam.driver.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radiotaxilatam.driver.data.ConductorRepositorio
import com.radiotaxilatam.driver.data.RegistroViaje
import com.radiotaxilatam.driver.data.model.EstadoViaje
import com.radiotaxilatam.driver.data.model.Viaje
import com.radiotaxilatam.driver.ui.AppViewModelFactory
import com.radiotaxilatam.driver.ui.formatDistance
import com.radiotaxilatam.driver.ui.formatDuracion
import com.radiotaxilatam.driver.ui.formatFecha
import com.radiotaxilatam.driver.ui.formatHora
import com.radiotaxilatam.driver.ui.home.RideAddresses
import com.radiotaxilatam.driver.ui.label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un viaje del historial con sus horas: las del backend si las tiene, y si no las que registró este móvil. */
data class ViajeHistorial(
    val viaje: Viaje,
    val inicio: Long?,
    val fin: Long?,
    val distanciaMetros: Int?,
)

data class HistoryUiState(
    val viajes: List<Viaje> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** El backend no pagina: GET /viaje/listar/viajes trae todos los viajes y se filtran los de este conductor. */
class HistoryViewModel(private val repositorio: ConductorRepositorio) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state
    val registros: StateFlow<Map<Long, RegistroViaje>> = repositorio.registros

    init {
        cargar()
    }

    fun cargar() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repositorio.historial()
                .onSuccess { viajes -> _state.update { it.copy(viajes = viajes, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}

private fun Viaje.conRegistro(registro: RegistroViaje?) = ViajeHistorial(
    viaje = this,
    // Su backend no guarda cuándo se inició: se usa la hora de este móvil. Un viaje cancelado nunca se
    // inició, así que su "inicio" es cuando se aceptó. Si el viaje no se hizo en este móvil, cuándo se pidió.
    inicio = registro?.iniciadoEn ?: registro?.aceptadoEn ?: creadoEn,
    // Para el fin manda la hora del servidor (finalizadoEn); si se canceló no la hay y se usa la del móvil.
    fin = finalizadoEn ?: registro?.terminadoEn,
    distanciaMetros = registro?.distanciaMetros,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registros by viewModel.registros.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de servicios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.viajes, key = { it.id }) { RideHistoryItem(it.conRegistro(registros[it.id])) }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::cargar) { Text("Reintentar") }
                        }
                        state.viajes.isEmpty() -> Text("Aún no tienes servicios realizados")
                    }
                }
            }
        }
    }
}

@Composable
private fun RideHistoryItem(item: ViajeHistorial) {
    val viaje = item.viaje
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.inicio?.let { formatFecha(it) } ?: "Carrera #${viaje.id}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.inicio != null) {
                        Text(
                            "Carrera #${viaje.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    viaje.estado.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (viaje.estado == EstadoViaje.CANCELADO) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            RideAddresses(viaje.origen, viaje.destino)
            val detalle = listOfNotNull(
                horario(item),
                if (viaje.estado == EstadoViaje.FINALIZADO && item.inicio != null && item.fin != null) {
                    formatDuracion((item.fin - item.inicio) / 1000)
                } else null,
                formatDistance(item.distanciaMetros),
            )
            Text(
                detalle.joinToString(" · ").ifEmpty { "Sin horario registrado en este móvil" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (detalle.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "13:12 → 13:34", o solo la hora que se conozca. */
private fun horario(item: ViajeHistorial): String? = when {
    item.inicio != null && item.fin != null -> "${formatHora(item.inicio)} → ${formatHora(item.fin)}"
    item.inicio != null -> formatHora(item.inicio)
    item.fin != null -> formatHora(item.fin)
    else -> null
}
