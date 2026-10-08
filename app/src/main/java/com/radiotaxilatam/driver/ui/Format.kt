package com.radiotaxilatam.driver.ui

import com.radiotaxilatam.driver.data.model.EstadoViaje
import java.util.Locale

private val LOCALE: Locale = Locale.forLanguageTag("es-PE")

fun formatDistance(meters: Int?): String? = meters?.let {
    if (it < 1000) "$it m" else String.format(LOCALE, "%.1f km", it / 1000.0)
}

/** Texto para el conductor. [llegado] es el paso "Llegué", que solo existe en la app. */
fun EstadoViaje.label(llegado: Boolean = false): String = when (this) {
    EstadoViaje.ACEPTADO -> if (llegado) "En punto de recogida" else "Aceptada"
    EstadoViaje.EN_CURSO -> "En viaje"
    EstadoViaje.FINALIZADO -> "Completada"
    EstadoViaje.CANCELADO -> "Cancelada"
    EstadoViaje.PENDIENTE, EstadoViaje.BUSCANDO_CONDUCTOR, EstadoViaje.ASIGNADO -> "Pendiente"
    EstadoViaje.DESCONOCIDO -> "Desconocido"
}
