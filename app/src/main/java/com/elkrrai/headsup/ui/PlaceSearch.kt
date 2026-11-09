package com.elkrrai.headsup.ui

import android.annotation.SuppressLint
import android.content.Context
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.data.Store
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchHit(val label: String, val lat: Double, val lng: Double)

/**
 * Free-text place search using OpenStreetMap's Nominatim (no API key needed).
 * An empty list means "nothing found"; null means the request itself failed (no internet, timeout, server error).
 */
suspend fun searchPlaces(query: String): List<SearchHit>? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=6&q=" + URLEncoder.encode(query, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "HeadsUp-Android/1.0")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SearchHit(o.getString("display_name"), o.getDouble("lat"), o.getDouble("lon"))
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Remembers the last position we saw, so the map can open already centred on the user
 * instead of showing the whole world while GPS wakes up.
 */
object LastLocation {
    private const val PREFS = "headsup_prefs"

    fun cached(ctx: Context): GeoPoint? {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("last_lat")) return null
        return GeoPoint(p.getFloat("last_lat", 0f).toDouble(), p.getFloat("last_lng", 0f).toDouble())
    }

    fun save(ctx: Context, lat: Double, lng: Double) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("last_lat", lat.toFloat()).putFloat("last_lng", lng.toFloat()).apply()
    }
}

/**
 * "Next visit" for a brand-new reminder: skip the current visit only if you're at the place right now
 * (then you're reminded the next time you arrive, after leaving). If you're away, the next arrival
 * already IS the next visit, so nothing is skipped. If we can't tell where you are, we don't skip:
 * an extra alert is better than a missed reminder.
 */
fun armNextVisitIfHere(ctx: Context, reminderId: String, placeId: String) {
    val target = Store.place(placeId) ?: return
    if (!Permissions.hasLocation(ctx)) return
    fun apply(here: GeoPoint?) {
        if (here != null && here.distanceToAsDouble(GeoPoint(target.lat, target.lng)) <= target.reminderRadiusM + 25) {
            Store.snoozeUntilNextVisit(reminderId)
        }
    }
    currentLocation(ctx) { loc ->
        if (loc != null) apply(GeoPoint(loc.first, loc.second)) else lastKnownLocation(ctx) { apply(it) }
    }
}

/**
 * "Once in place" for a brand-new reminder: the place's geofence only fires when you walk into the circle, so if
 * you are already inside when you save, nothing would ever alert until you left and came back. In that case alert
 * right away. If we can't tell where you are, we stay quiet (the geofence still alerts on the next arrival).
 */
fun alertNowIfHere(ctx: Context, reminderId: String, placeId: String) {
    val target = Store.place(placeId) ?: return
    if (!Permissions.hasLocation(ctx)) return
    fun apply(here: GeoPoint?) {
        if (here == null || here.distanceToAsDouble(GeoPoint(target.lat, target.lng)) > target.reminderRadiusM) return
        val r = Store.reminder(reminderId) ?: return
        if (r.done || r.isSnoozed()) return
        com.elkrrai.headsup.notify.Notifier.notifyErrand(ctx.applicationContext, r, target)
    }
    currentLocation(ctx) { loc ->
        if (loc != null) apply(GeoPoint(loc.first, loc.second)) else lastKnownLocation(ctx) { apply(it) }
    }
}

/** Fast: the last position Android already knows (may be slightly old, or null). */
@SuppressLint("MissingPermission")
fun lastKnownLocation(ctx: Context, onResult: (GeoPoint?) -> Unit) {
    LocationServices.getFusedLocationProviderClient(ctx).lastLocation
        .addOnSuccessListener { loc ->
            loc?.let { LastLocation.save(ctx, it.latitude, it.longitude) }
            onResult(loc?.let { GeoPoint(it.latitude, it.longitude) })
        }
        .addOnFailureListener { onResult(null) }
}

/** One-shot fresh location; null if permission is missing or no fix is available. */
@SuppressLint("MissingPermission")
fun currentLocation(ctx: Context, onResult: (Pair<Double, Double>?) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(ctx)
    // Accept a fix up to 30 s old, and give up after 12 s instead of waiting indefinitely.
    val request = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMaxUpdateAgeMillis(30_000L)
        .setDurationMillis(12_000L)
        .build()
    client.getCurrentLocation(request, CancellationTokenSource().token)
        .addOnSuccessListener { loc ->
            if (loc != null) {
                LastLocation.save(ctx, loc.latitude, loc.longitude)
                onResult(loc.latitude to loc.longitude)
            } else onResult(null)
        }
        .addOnFailureListener { onResult(null) }
}
