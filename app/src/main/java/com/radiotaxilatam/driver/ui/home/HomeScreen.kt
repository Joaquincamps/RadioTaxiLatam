package com.radiotaxilatam.driver.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radiotaxilatam.driver.data.OfertaActiva
import com.radiotaxilatam.driver.data.model.EstadoConductor
import com.radiotaxilatam.driver.data.model.EstadoViaje
import com.radiotaxilatam.driver.data.model.Viaje
import com.radiotaxilatam.driver.service.DriverOnlineService
import com.radiotaxilatam.driver.ui.AppViewModelFactory
import com.radiotaxilatam.driver.ui.formatDistance
import com.radiotaxilatam.driver.ui.label
import com.radiotaxilatam.driver.ui.theme.OnlineGreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val context = LocalContext.current
    val conductor by viewModel.conductor.collectAsStateWithLifecycle()
    val viaje by viewModel.viajeActual.collectAsStateWithLifecycle()
    val viajeLlegadoId by viewModel.viajeLlegadoId.collectAsStateWithLifecycle()
    val oferta by viewModel.oferta.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPermissionHelp by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            if (conductor?.estado == EstadoConductor.AVAILABLE) DriverOnlineService.start(context)
            else viewModel.cambiarDisponibilidad(true)
        } else {
            showPermissionHelp = true
            // Sin ubicación el backend nunca le ofrecerá viajes: mejor que figure como no disponible.
            if (conductor?.estado == EstadoConductor.AVAILABLE) viewModel.cambiarDisponibilidad(false)
        }
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // Mientras el conductor esté en línea (disponible u ocupado) el servicio envía ubicación y consulta carreras.
    // El login del backend pone al conductor AVAILABLE directamente: si aún no hay permiso de ubicación, se pide.
    val status = conductor?.estado
    LaunchedEffect(status) {
        if (status != null && status != EstadoConductor.OFFLINE) {
            if (hasLocationPermission(context)) DriverOnlineService.start(context)
            else permissionLauncher.launch(requiredPermissions())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(conductor?.nombre ?: "Conductor", style = MaterialTheme.typography.titleMedium)
                        conductor?.let { c ->
                            listOfNotNull(c.modeloVehiculo, c.matricula).takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    it.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historial")
                    }
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }, enabled = viaje == null) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())

            AvailabilityCard(
                status = status,
                enabled = !working && conductor != null && viaje == null,
                onToggle = { wantOnline ->
                    if (!wantOnline) {
                        viewModel.cambiarDisponibilidad(false)
                    } else if (hasLocationPermission(context)) {
                        viewModel.cambiarDisponibilidad(true)
                    } else {
                        permissionLauncher.launch(requiredPermissions())
                    }
                },
            )

            val actual = viaje
            when {
                conductor == null -> Centered { CircularProgressIndicator() }
                actual != null -> CurrentRideCard(
                    viaje = actual,
                    llegado = viajeLlegadoId == actual.id,
                    working = working,
                    onAdvance = { viewModel.avanzar(actual) },
                    onNavigate = { openNavigation(context, actual) },
                    onCall = { actual.cliente?.telefono?.let { dial(context, it) } },
                )
                status == EstadoConductor.AVAILABLE -> WaitingCard()
                else -> OfflineHint()
            }
        }
    }

    oferta?.let { activa ->
        OfferDialog(
            activa = activa,
            onAccept = { viewModel.aceptar(activa.oferta.id) },
            onReject = { viewModel.rechazar(activa.oferta.id) },
            onExpired = { viewModel.caducar(activa.oferta.id) },
        )
    }

    if (showPermissionHelp) {
        AlertDialog(
            onDismissRequest = { showPermissionHelp = false },
            confirmButton = { TextButton(onClick = { showPermissionHelp = false }) { Text("Entendido") } },
            title = { Text("Se necesita tu ubicación") },
            text = {
                Text(
                    "Para asignarte carreras cercanas la central necesita conocer tu ubicación mientras estás " +
                        "disponible. Actívala en Ajustes > Aplicaciones > RadioTaxi Conductor > Permisos."
                )
            },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
private fun AvailabilityCard(status: EstadoConductor?, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val online = status == EstadoConductor.AVAILABLE || status == EstadoConductor.BUSY
    val (title, subtitle) = when (status) {
        EstadoConductor.AVAILABLE -> "Disponible" to "Recibiendo carreras"
        EstadoConductor.BUSY -> "Ocupado" to "Tienes una carrera en curso"
        else -> "No disponible" to "No recibirás carreras"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (online) OnlineGreen else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (online) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = online,
                onCheckedChange = onToggle,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun WaitingCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Esperando carreras…", style = MaterialTheme.typography.titleMedium)
            Text(
                "Te avisaremos con sonido cuando llegue una nueva. Puedes dejar la app en segundo plano.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfflineHint() {
    Text(
        "Activa \"Disponible\" para empezar a recibir carreras.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CurrentRideCard(
    viaje: Viaje,
    llegado: Boolean,
    working: Boolean,
    onAdvance: () -> Unit,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Carrera #${viaje.id} · ${viaje.estado.label(llegado)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            RideAddresses(viaje.origen, viaje.destino)
            viaje.cliente?.nombre?.let { LabeledLine("Pasajero", it) }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Navegar")
                }
                if (viaje.cliente?.telefono != null) {
                    OutlinedButton(onClick = onCall, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Llamar")
                    }
                }
            }
            val actionLabel = when {
                viaje.estado == EstadoViaje.ACEPTADO && !llegado -> "Llegué al punto de recogida"
                viaje.estado == EstadoViaje.ACEPTADO -> "Iniciar viaje"
                viaje.estado == EstadoViaje.EN_CURSO -> "Finalizar viaje"
                else -> null
            }
            actionLabel?.let {
                Button(
                    onClick = onAdvance,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(it, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
fun RideAddresses(origen: String, destino: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AddressLine(Icons.Default.Place, "Origen", origen)
        destino?.let { AddressLine(Icons.Default.LocationOn, "Destino", it) }
    }
}

@Composable
private fun AddressLine(icon: ImageVector, label: String, address: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(address, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun OfferDialog(
    activa: OfertaActiva,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onExpired: () -> Unit,
) {
    val oferta = activa.oferta
    var secondsLeft by remember(oferta.id) { mutableIntStateOf(activa.segundosRestantes()) }
    LaunchedEffect(oferta.id) {
        while (true) {
            secondsLeft = activa.segundosRestantes()
            if (secondsLeft <= 0) {
                onExpired()
                break
            }
            delay(250)
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("¡Nueva carrera!", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { secondsLeft / activa.segundosTotales.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Quedan $secondsLeft s para responder", style = MaterialTheme.typography.bodySmall)
                RideAddresses(oferta.viaje.origen, oferta.viaje.destino)
                formatDistance(oferta.distanciaRecogidaMetros)?.let { LabeledLine("Distancia al origen", it) }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
            ) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Rechazar", color = MaterialTheme.colorScheme.error) }
        },
    )
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Abre Google Maps, Waze u otra app de mapas hacia el siguiente punto de la carrera. */
private fun openNavigation(context: Context, viaje: Viaje) {
    val toDropoff = viaje.estado == EstadoViaje.EN_CURSO && viaje.destino != null
    val lat = if (toDropoff) viaje.latitudDestino else viaje.latitudOrigen
    val lng = if (toDropoff) viaje.longitudDestino else viaje.longitudOrigen
    val address = if (toDropoff) viaje.destino!! else viaje.origen
    val query = if (lat != null && lng != null) "$lat,$lng(${Uri.encode(address)})" else Uri.encode(address)
    startSafely(context, Intent(Intent.ACTION_VIEW, "geo:0,0?q=$query".toUri()))
}

private fun dial(context: Context, phone: String) {
    startSafely(context, Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(phone)}".toUri()))
}

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No hay app que maneje la acción (p. ej. sin app de mapas en el emulador).
    }
}
