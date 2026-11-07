package com.elkrrai.headsup.geo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.data.Store
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceManager {
    private fun pendingIntent(ctx: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 0, Intent(ctx, GeofenceReceiver::class.java), flags)
    }

    /** Places that still have at least one pending (not done) reminder. */
    fun activePlaces() = Store.reminders.value.filter { !it.done }.map { it.placeId }.toSet().let { ids ->
        Store.places.value.filter { it.id in ids }
    }

    /** Re-registers one geofence per place that has a pending reminder. */
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        val client = LocationServices.getGeofencingClient(app)
        val pi = pendingIntent(app)
        val places = activePlaces().take(100) // Android limit per app
        client.removeGeofences(pi).addOnCompleteListener {
            if (places.isEmpty() || !Permissions.canGeofence(app)) return@addOnCompleteListener
            val fences = places.map {
                Geofence.Builder()
                    .setRequestId(it.id)
                    .setCircularRegion(it.lat, it.lng, it.radiusM.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            // Initial trigger 0: don't fire just because you're already inside when it's registered.
            val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
            client.addGeofences(request, pi)
        }
    }
}
