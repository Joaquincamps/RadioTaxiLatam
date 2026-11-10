package com.radiotaxilatam.driver.data.geo

import android.content.Context
import android.location.Geocoder
import com.radiotaxilatam.driver.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Punto(val latitud: Double, val longitud: Double)

data class Ruta(
    val puntos: List<Punto>,
    val distanciaMetros: Int,
    val duracionSegundos: Int?,
    /** false si no se pudo calcular por calles y es la línea recta entre origen y destino. */
    val porCalles: Boolean,
) {
    val origen: Punto get() = puntos.first()
    val destino: Punto get() = puntos.last()
}

/**
 * Calcula la ruta de un viaje para el mapa y la distancia de la oferta.
 *
 * - Ruta por calles: servidor público de OSRM (OpenStreetMap). Es un servidor de demostración sin garantías:
 *   vale para el piloto; con muchos conductores habrá que montar uno propio o usar un servicio de pago.
 *   Si falla, se usa la línea recta.
 * - Coordenadas del destino: el backend aún no las manda, así que se buscan a partir de la dirección con el
 *   Geocoder de Android, priorizando resultados cerca del origen.
 */
class RutaServicio(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val rutas = ConcurrentHashMap<String, Ruta>()
    private val direcciones = ConcurrentHashMap<String, Punto>()

    /** Ruta de origen a destino usando las coordenadas que haya y geocodificando las que falten. */
    suspend fun rutaDeViaje(
        origen: String,
        latitudOrigen: Double?,
        longitudOrigen: Double?,
        destino: String?,
        latitudDestino: Double?,
        longitudDestino: Double?,
    ): Ruta? {
        val o = punto(latitudOrigen, longitudOrigen) ?: geocodificar(origen, cerca = null) ?: return null
        val d = punto(latitudDestino, longitudDestino)
            ?: destino?.let { geocodificar(it, cerca = o) }
            ?: return null
        return ruta(o, d)
    }

    suspend fun ruta(origen: Punto, destino: Punto): Ruta = withContext(Dispatchers.IO) {
        val clave = "${origen.latitud},${origen.longitud};${destino.latitud},${destino.longitud}"
        rutas[clave] ?: porCalles(origen, destino)?.also { rutas[clave] = it } ?: lineaRecta(origen, destino)
    }

    private fun porCalles(origen: Punto, destino: Punto): Ruta? = runCatching {
        val url = "$OSRM/route/v1/driving/${origen.longitud},${origen.latitud};" +
            "${destino.longitud},${destino.latitud}?overview=full&geometries=geojson"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) return@runCatching null
            val ruta = AppJson.parseToJsonElement(body).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@runCatching null
            val puntos = ruta.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.map {
                val lngLat = it.jsonArray
                Punto(lngLat[1].jsonPrimitive.double, lngLat[0].jsonPrimitive.double)
            }
            if (puntos.size < 2) return@runCatching null
            Ruta(
                puntos = puntos,
                distanciaMetros = ruta.getValue("distance").jsonPrimitive.double.roundToInt(),
                duracionSegundos = ruta.getValue("duration").jsonPrimitive.double.roundToInt(),
                porCalles = true,
            )
        }
    }.getOrNull()

    @Suppress("DEPRECATION") // La API asíncrona solo existe desde Android 13; minSdk es 26.
    private suspend fun geocodificar(direccion: String, cerca: Punto?): Punto? = withContext(Dispatchers.IO) {
        if (direccion.isBlank() || !Geocoder.isPresent()) return@withContext null
        direcciones[direccion]?.let { return@withContext it }
        runCatching {
            val geocoder = Geocoder(context, Locale.forLanguageTag("es"))
            val cercanos = cerca?.let {
                geocoder.getFromLocationName(
                    direccion, 1,
                    it.latitud - MARGEN_BUSQUEDA, it.longitud - MARGEN_BUSQUEDA,
                    it.latitud + MARGEN_BUSQUEDA, it.longitud + MARGEN_BUSQUEDA,
                )
            }
            val resultado = cercanos?.firstOrNull() ?: geocoder.getFromLocationName(direccion, 1)?.firstOrNull()
            resultado?.let { Punto(it.latitude, it.longitude) }
        }.getOrNull()?.also { direcciones[direccion] = it }
    }

    private fun lineaRecta(origen: Punto, destino: Punto) =
        Ruta(listOf(origen, destino), distanciaEnLineaRecta(origen, destino).roundToInt(), null, porCalles = false)

    private fun punto(latitud: Double?, longitud: Double?) =
        if (latitud != null && longitud != null) Punto(latitud, longitud) else null

    private companion object {
        const val OSRM = "https://router.project-osrm.org"
        const val USER_AGENT = "RadioTaxiConductor/0.1 (Android)"

        /** ~50 km alrededor del origen: el destino de un taxi suele estar en la misma ciudad. */
        const val MARGEN_BUSQUEDA = 0.5
    }
}

/** Distancia en metros entre dos puntos (fórmula del haversine). */
fun distanciaEnLineaRecta(a: Punto, b: Punto): Double {
    val radioTierra = 6_371_000.0
    val dLat = Math.toRadians(b.latitud - a.latitud)
    val dLng = Math.toRadians(b.longitud - a.longitud)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitud)) * cos(Math.toRadians(b.latitud)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * radioTierra * atan2(sqrt(h), sqrt(1 - h))
}
