package com.elkrrai.headsup

import android.app.Application
import com.elkrrai.headsup.data.Store
import com.elkrrai.headsup.geo.GeofenceManager
import com.elkrrai.headsup.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class HeadsUpApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannels(this)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        // Keep geofences in step with the data. Only re-register when something that
        // affects a geofence changes (not on every snooze / done toggle).
        scope.launch {
            combine(Store.places, Store.reminders) { _, _ ->
                GeofenceManager.activePlaces().map { "${it.id}|${it.lat}|${it.lng}|${it.radiusM}" }.toSet()
            }.distinctUntilChanged().collect { GeofenceManager.sync(this@HeadsUpApp) }
        }
    }
}
