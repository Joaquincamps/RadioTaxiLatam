package com.radiotaxilatam.driver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.radiotaxilatam.driver.data.ConductorRepositorio
import com.radiotaxilatam.driver.data.RegistroViajes
import com.radiotaxilatam.driver.data.SessionStore
import com.radiotaxilatam.driver.data.geo.RutaServicio
import com.radiotaxilatam.driver.data.mock.MockTaxiApi
import com.radiotaxilatam.driver.data.remote.ApiFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration

class TaxiApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    lateinit var repositorio: ConductorRepositorio
        private set
    lateinit var rutas: RutaServicio
        private set

    override fun onCreate() {
        super.onCreate()
        val session = SessionStore(this)
        val registro = RegistroViajes(this)
        // Lecturas únicas y pequeñas al arrancar: id del conductor (pantalla inicial) y horas de sus viajes.
        runBlocking {
            session.load()
            registro.cargar()
        }

        val api = if (BuildConfig.USE_MOCK_API) MockTaxiApi() else ApiFactory.create()
        repositorio = ConductorRepositorio(api, session, registro)
        rutas = RutaServicio(this)
        // Los servidores de mapas de OpenStreetMap exigen identificar la app.
        Configuration.getInstance().userAgentValue = packageName
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val online = NotificationChannel(
            CHANNEL_ONLINE, getString(R.string.channel_online_name), NotificationManager.IMPORTANCE_LOW
        )
        val offers = NotificationChannel(
            CHANNEL_OFFERS, getString(R.string.channel_offers_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
            )
        }
        manager.createNotificationChannels(listOf(online, offers))
    }

    companion object {
        const val CHANNEL_ONLINE = "online"
        const val CHANNEL_OFFERS = "offers"
    }
}
