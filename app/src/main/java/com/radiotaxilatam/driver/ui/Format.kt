package com.radiotaxilatam.driver.ui

import com.radiotaxilatam.driver.data.model.EstadoViaje
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOCALE: Locale = Locale.forLanguageTag("es-PE")
private val FECHA = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy", LOCALE)
private val HORA = DateTimeFormatter.ofPattern("HH:mm", LOCALE)

fun formatDistance(meters: Int?): String? = meters?.let {
    if (it < 1000) "$it m" else String.format(LOCALE, "%.1f km", it / 1000.0)
}

/** "8 min", "1 h 05 min". */
fun formatDuracion(segundos: Long?): String? = segundos?.takeIf { it >= 0 }?.let {
    val minutos = (it + 30) / 60
    if (minutos < 60) "$minutos min" else String.format(LOCALE, "%d h %02d min", minutos / 60, minutos % 60)
}

/** "jue. 05/11/2026" con la hora local del móvil. */
fun formatFecha(epochMs: Long): String = FECHA.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatHora(epochMs: Long): String = HORA.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** Texto para el conductor. */
fun EstadoViaje.label(): String = when (this) {
    EstadoViaje.ACEPTADO -> "Aceptada"
    EstadoViaje.LLEGADO -> "En punto de recogida"
    EstadoViaje.EN_CURSO -> "En viaje"
    EstadoViaje.FINALIZADO -> "Completada"
    EstadoViaje.CANCELADO -> "Cancelada"
    EstadoViaje.PENDIENTE, EstadoViaje.BUSCANDO_CONDUCTOR, EstadoViaje.ASIGNADO -> "Pendiente"
    EstadoViaje.DESCONOCIDO -> "Desconocido"
}
