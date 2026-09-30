package com.radiotaxilatam.driver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TaxiYellow = Color(0xFFFFC107)
val OnlineGreen = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = Color(0xFF212121),
    onPrimary = Color.White,
    secondary = TaxiYellow,
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFFFFECB3),
    onSecondaryContainer = Color(0xFF3E2C00),
)

private val DarkColors = darkColorScheme(
    primary = TaxiYellow,
    onPrimary = Color(0xFF212121),
    secondary = TaxiYellow,
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFECB3),
)

@Composable
fun RadioTaxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
