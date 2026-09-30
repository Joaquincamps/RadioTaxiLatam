package com.radiotaxilatam.driver.ui

import com.radiotaxilatam.driver.data.model.RideStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val LOCALE: Locale = Locale.forLanguageTag("es-PE")

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE)

fun formatMoney(amount: Double?, currency: String?): String? {
    if (amount == null) return null
    val format = NumberFormat.getCurrencyInstance(LOCALE)
    currency?.let { runCatching { format.currency = Currency.getInstance(it) } }
    return format.format(amount)
}

fun formatDateTime(iso: String?): String? = iso?.let {
    runCatching { dateFormatter.format(Instant.parse(it).atZone(ZoneId.systemDefault())) }.getOrNull()
}

fun formatDistance(meters: Int?): String? = meters?.let {
    if (it < 1000) "$it m" else String.format(LOCALE, "%.1f km", it / 1000.0)
}

val RideStatus.label: String
    get() = when (this) {
        RideStatus.ACCEPTED -> "Aceptada"
        RideStatus.ARRIVED -> "En punto de recogida"
        RideStatus.IN_PROGRESS -> "En viaje"
        RideStatus.COMPLETED -> "Completada"
        RideStatus.CANCELLED -> "Cancelada"
        RideStatus.UNKNOWN -> "Desconocido"
    }
