package com.radiotaxilatam.driver.ui.mapa

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.radiotaxilatam.driver.R
import com.radiotaxilatam.driver.data.geo.Punto
import com.radiotaxilatam.driver.data.geo.Ruta
import com.radiotaxilatam.driver.data.geo.distanciaEnLineaRecta
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Mapa pequeño (OpenStreetMap) con la ruta del viaje de origen a destino y la posición del taxi.
 * No se mueve con el dedo para no estorbar al hacer scroll; al tocarlo se llama a [onTap] (abrir navegación).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapaViaje(
    ruta: Ruta,
    taxi: Punto?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tapActual by rememberUpdatedState(onTap)
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    view.performClick()
                    tapActual()
                }
                true
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, update = { dibujar(it, ruta, taxi) }, modifier = modifier)
}

private fun dibujar(mapa: MapView, ruta: Ruta, taxi: Punto?) {
    val context = mapa.context
    val camino = ruta.puntos.map { GeoPoint(it.latitud, it.longitud) }
    mapa.overlays.clear()
    mapa.overlays += Polyline(mapa).apply {
        setPoints(camino)
        outlinePaint.color = COLOR_RUTA
        outlinePaint.strokeWidth = 12f
        // Sin ruta por calles se dibuja la línea recta discontinua, para que no parezca el camino real.
        if (!ruta.porCalles) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(30f, 20f), 0f)
    }
    mapa.overlays += marcador(mapa, camino.first(), "Origen", R.drawable.ic_pin_origen, Marker.ANCHOR_CENTER)
    mapa.overlays += marcador(mapa, camino.last(), "Destino", R.drawable.ic_pin_destino, Marker.ANCHOR_BOTTOM)
    // Un taxi lejísimos (GPS erróneo, o pruebas desde otro país) obligaría a encuadrar medio mundo: se ignora.
    val posicionTaxi = taxi
        ?.takeIf { distanciaEnLineaRecta(it, ruta.origen) < DISTANCIA_MAX_TAXI_M }
        ?.let { GeoPoint(it.latitud, it.longitud) }
    posicionTaxi?.let { mapa.overlays += marcador(mapa, it, "Tu taxi", R.drawable.ic_taxi, Marker.ANCHOR_CENTER) }
    mapa.invalidate()

    val caja = BoundingBox.fromGeoPointsSafe(camino + listOfNotNull(posicionTaxi))
    val encuadrar = { mapa.zoomToBoundingBox(caja, false, (48 * context.resources.displayMetrics.density).toInt()) }
    if (mapa.width > 0 && mapa.height > 0) encuadrar() else mapa.addOnFirstLayoutListener { _, _, _, _, _ -> encuadrar() }
}

private fun marcador(mapa: MapView, punto: GeoPoint, titulo: String, icono: Int, anclaVertical: Float) =
    Marker(mapa).apply {
        position = punto
        title = titulo
        icon = ContextCompat.getDrawable(mapa.context, icono)
        setAnchor(Marker.ANCHOR_CENTER, anclaVertical)
        setInfoWindow(null)
    }

private const val COLOR_RUTA = 0xFF1E88E5.toInt()
private const val DISTANCIA_MAX_TAXI_M = 50_000.0
