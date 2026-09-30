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
import com.radiotaxilatam.driver.data.ActiveOffer
import com.radiotaxilatam.driver.data.model.DriverStatus
import com.radiotaxilatam.driver.data.model.Ride
import com.radiotaxilatam.driver.data.model.RideStatus
import com.radiotaxilatam.driver.service.DriverOnlineService
import com.radiotaxilatam.driver.ui.AppViewModelFactory
import com.radiotaxilatam.driver.ui.formatDistance
import com.radiotaxilatam.driver.ui.formatMoney
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
    val driver by viewModel.driver.collectAsStateWithLifecycle()
    val ride by viewModel.currentRide.collectAsStateWithLifecycle()
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPermissionHelp by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.setAvailable(true)
        } else {
            showPermissionHelp = true
        }
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // Mientras el conductor esté en línea (disponible u ocupado) el servicio envía ubicación y consulta carreras.
    val status = driver?.status
    LaunchedEffect(status) {
        if (status != null && status != DriverStatus.OFFLINE && hasLocationPermission(context)) {
            DriverOnlineService.start(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(driver?.name ?: "Conductor", style = MaterialTheme.typography.titleMedium)
                        driver?.let { d ->
                            listOfNotNull(d.vehicleModel, d.vehiclePlate).takeIf { it.isNotEmpty() }?.let {
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
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }, enabled = ride == null) {
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
                enabled = !working && driver != null && ride == null,
                onToggle = { wantOnline ->
                    if (!wantOnline) {
                        viewModel.setAvailable(false)
                    } else if (hasLocationPermission(context)) {
                        viewModel.setAvailable(true)
                    } else {
                        permissionLauncher.launch(requiredPermissions())
                    }
                },
            )

            val currentRide = ride
            when {
                driver == null -> Centered { CircularProgressIndicator() }
                currentRide != null -> CurrentRideCard(
                    ride = currentRide,
                    working = working,
                    onAdvance = { viewModel.advance(currentRide) },
                    onNavigate = { openNavigation(context, currentRide) },
                    onCall = { currentRide.passengerPhone?.let { dial(context, it) } },
                )
                status == DriverStatus.AVAILABLE -> WaitingCard()
                else -> OfflineHint()
            }
        }
    }

    offer?.let { active ->
        OfferDialog(
            active = active,
            onAccept = { viewModel.accept(active.offer.offerId) },
            onReject = { viewModel.reject(active.offer.offerId) },
            onExpired = { viewModel.expire(active.offer.offerId) },
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
private fun AvailabilityCard(status: DriverStatus?, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val online = status == DriverStatus.AVAILABLE || status == DriverStatus.BUSY
    val (title, subtitle) = when (status) {
        DriverStatus.AVAILABLE -> "Disponible" to "Recibiendo carreras"
        DriverStatus.BUSY -> "Ocupado" to "Tienes una carrera en curso"
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
    ride: Ride,
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
                "Carrera #${ride.id} · ${ride.status.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            RideAddresses(ride)
            ride.passengerName?.let { LabeledLine("Pasajero", it) }
            ride.notes?.let { LabeledLine("Notas", it) }
            formatMoney(ride.estimatedFare, ride.currency)?.let { LabeledLine("Tarifa estimada", it) }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Navegar")
                }
                if (ride.passengerPhone != null) {
                    OutlinedButton(onClick = onCall, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Llamar")
                    }
                }
            }
            val actionLabel = when (ride.status) {
                RideStatus.ACCEPTED -> "Llegué al punto de recogida"
                RideStatus.ARRIVED -> "Iniciar viaje"
                RideStatus.IN_PROGRESS -> "Finalizar viaje"
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
fun RideAddresses(ride: Ride) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AddressLine(Icons.Default.Place, "Origen", ride.pickupAddress)
        ride.dropoffAddress?.let { AddressLine(Icons.Default.LocationOn, "Destino", it) }
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
    active: ActiveOffer,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onExpired: () -> Unit,
) {
    val offer = active.offer
    var secondsLeft by remember(offer.offerId) { mutableIntStateOf(active.secondsLeft()) }
    LaunchedEffect(offer.offerId) {
        while (true) {
            secondsLeft = active.secondsLeft()
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
                    progress = { secondsLeft / offer.expiresInSeconds.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Quedan $secondsLeft s para responder", style = MaterialTheme.typography.bodySmall)
                RideAddresses(offer.ride)
                formatDistance(offer.distanceToPickupMeters)?.let { LabeledLine("Distancia al origen", it) }
                formatMoney(offer.ride.estimatedFare, offer.ride.currency)?.let { LabeledLine("Tarifa estimada", it) }
                offer.ride.notes?.let { LabeledLine("Notas", it) }
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
private fun openNavigation(context: Context, ride: Ride) {
    val toDropoff = ride.status == RideStatus.IN_PROGRESS && ride.dropoffAddress != null
    val lat = if (toDropoff) ride.dropoffLat else ride.pickupLat
    val lng = if (toDropoff) ride.dropoffLng else ride.pickupLng
    val address = if (toDropoff) ride.dropoffAddress!! else ride.pickupAddress
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
