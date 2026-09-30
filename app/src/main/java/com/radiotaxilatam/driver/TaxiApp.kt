package com.radiotaxilatam.driver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.radiotaxilatam.driver.data.DriverRepository
import com.radiotaxilatam.driver.data.SessionStore
import com.radiotaxilatam.driver.data.mock.MockTaxiApi
import com.radiotaxilatam.driver.data.mvp.JoaquinApi
import com.radiotaxilatam.driver.data.mvp.JoaquinTaxiApi
import com.radiotaxilatam.driver.data.remote.ApiFactory
import com.radiotaxilatam.driver.data.remote.TaxiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TaxiApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    lateinit var repository: DriverRepository
        private set

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    override fun onCreate() {
        super.onCreate()
        val session = SessionStore(this)
        // Lectura única y pequeña del token al arrancar para decidir la pantalla inicial.
        runBlocking { session.load() }

        val onUnauthorized: () -> Unit = {
            appScope.launch {
                repository.clearLocalSession()
                _sessionExpired.emit(Unit)
            }
        }
        val api: TaxiApi = when (BuildConfig.API_MODE) {
            API_MODE_MVP -> JoaquinTaxiApi(ApiFactory.retrofit(session, onUnauthorized).create(JoaquinApi::class.java), session)
            API_MODE_CONTRACT -> ApiFactory.retrofit(session, onUnauthorized).create(TaxiApi::class.java)
            else -> MockTaxiApi()
        }
        repository = DriverRepository(api, session)
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

        // Valores de apiMode en gradle.properties
        const val API_MODE_MOCK = "mock"
        const val API_MODE_MVP = "mvp"
        const val API_MODE_CONTRACT = "contract"
    }
}
