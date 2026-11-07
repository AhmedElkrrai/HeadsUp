package com.elkrrai.headsup.geo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elkrrai.headsup.data.Store
import com.elkrrai.headsup.notify.Notifier
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val fences = event.triggeringGeofences ?: return

        for (fence in fences) {
            val place = Store.place(fence.requestId) ?: continue
            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    val now = System.currentTimeMillis()
                    Store.reminders.value
                        .filter { it.placeId == place.id && !it.done && !it.isSnoozed(now) }
                        .forEach { Notifier.notifyErrand(context, it, place) }
                }
                // Leaving the circle re-arms "snooze until next visit".
                Geofence.GEOFENCE_TRANSITION_EXIT -> Store.rearmAfterExit(place.id)
            }
        }
    }
}
