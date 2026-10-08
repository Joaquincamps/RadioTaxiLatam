package com.radiotaxilatam.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.radiotaxilatam.driver.ui.history.HistoryScreen
import com.radiotaxilatam.driver.ui.home.HomeScreen
import com.radiotaxilatam.driver.ui.login.LoginScreen
import com.radiotaxilatam.driver.ui.theme.RadioTaxiTheme

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val HISTORY = "history"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TaxiApp
        val start = if (app.repositorio.haIniciadoSesion) Routes.HOME else Routes.LOGIN

        setContent {
            RadioTaxiTheme {
                val nav = rememberNavController()
                LaunchedEffect(Unit) { app.repositorio.sesionCaducada.collect { nav.goToLogin() } }

                NavHost(navController = nav, startDestination = start) {
                    composable(Routes.LOGIN) {
                        LoginScreen(onLoggedIn = {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        })
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenHistory = { nav.navigate(Routes.HISTORY) },
                            onLoggedOut = { nav.goToLogin() },
                        )
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

private fun NavHostController.goToLogin() {
    navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
}
