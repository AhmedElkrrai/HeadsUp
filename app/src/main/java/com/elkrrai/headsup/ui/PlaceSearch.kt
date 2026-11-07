package com.elkrrai.headsup.ui

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchHit(val label: String, val lat: Double, val lng: Double)

/** Free-text place search using OpenStreetMap's Nominatim (no API key needed). */
suspend fun searchPlaces(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
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
        emptyList()
    }
}

/** One-shot current location; null if permission is missing or no fix is available. */
@SuppressLint("MissingPermission")
fun currentLocation(ctx: Context, onResult: (Pair<Double, Double>?) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(ctx)
    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { loc -> if (loc != null) onResult(loc.latitude to loc.longitude) else onResult(null) }
        .addOnFailureListener { onResult(null) }
}
