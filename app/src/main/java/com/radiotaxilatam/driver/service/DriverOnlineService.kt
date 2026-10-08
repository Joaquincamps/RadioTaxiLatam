package com.radiotaxilatam.driver.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.radiotaxilatam.driver.MainActivity
import com.radiotaxilatam.driver.R
import com.radiotaxilatam.driver.TaxiApp
import com.radiotaxilatam.driver.data.model.EstadoConductor
import com.radiotaxilatam.driver.data.model.OfertaViajeDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mantiene al conductor "en línea" mientras la app está en segundo plano:
 * envía su ubicación al backend y consulta nuevas carreras periódicamente.
 */
class DriverOnlineService : LifecycleService() {

    private val repositorio by lazy { (application as TaxiApp).repositorio }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lifecycleScope.launch { repositorio.enviarUbicacion(location.latitude, location.longitude) }
        }
    }

    private var started = false

    @SuppressLint("InlinedApi") // ServiceCompat ignora el tipo en versiones < 29
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ONLINE, buildOnlineNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        if (!started) {
            started = true
            startLocationUpdates()
            startPolling()
            stopWhenOffline()
        }
        return START_STICKY
    }

    private fun startPolling() = lifecycleScope.launch {
        while (isActive) {
            repositorio.consultar().onSuccess { nueva -> nueva?.let(::notifyNewOffer) }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun stopWhenOffline() = lifecycleScope.launch {
        repositorio.conductor.collectLatest { conductor ->
            if (conductor == null || conductor.estado == EstadoConductor.OFFLINE) stopSelf()
        }
    }

    @SuppressLint("MissingPermission") // comprobado en onStartCommand
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled() cubre POST_NOTIFICATIONS
    private fun notifyNewOffer(oferta: OfertaViajeDto) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(this, TaxiApp.CHANNEL_OFFERS)
            .setSmallIcon(R.drawable.ic_stat_taxi)
            .setContentTitle("¡Nueva carrera!")
            .setContentText("Recoger en: ${oferta.viaje.origen}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Recoger en: ${oferta.viaje.origen}" +
                        (oferta.viaje.destino?.let { "\nDestino: $it" } ?: "")
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setTimeoutAfter(oferta.segundosRestantes * 1000L)
            .setContentIntent(openAppIntent())
            .setFullScreenIntent(openAppIntent(), true)
            .build()
        manager.notify(NOTIFICATION_OFFER, notification)
    }

    private fun buildOnlineNotification(): Notification =
        NotificationCompat.Builder(this, TaxiApp.CHANNEL_ONLINE)
            .setSmallIcon(R.drawable.ic_stat_taxi)
            .setContentTitle("Estás en línea")
            .setContentText("Recibirás nuevas carreras aunque la app esté cerrada")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_OFFER)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ONLINE = 1
        private const val NOTIFICATION_OFFER = 2
        private const val POLL_INTERVAL_MS = 4_000L
        private const val LOCATION_INTERVAL_MS = 10_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DriverOnlineService::class.java))
        }
    }
}
