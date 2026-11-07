package com.elkrrai.headsup.geo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Geofences are cleared on reboot / app update. Receiving this broadcast starts the
 * process, and [com.elkrrai.headsup.HeadsUpApp] re-registers them on startup.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GeofenceManager.sync(context)
    }
}
