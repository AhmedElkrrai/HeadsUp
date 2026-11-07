package com.elkrrai.headsup.trip

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.elkrrai.headsup.MainActivity
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.R
import com.elkrrai.headsup.notify.Notifier
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TripState(
    val name: String,
    val alertM: Int,
    val distanceM: Int? = null,
    val etaMin: Int? = null,
    val arrived: Boolean = false,
)

/**
 * Trip mode: follows your GPS while you ride and rings a loud alarm once you are
 * within [alertM] metres of the destination. Runs as a foreground service so it
 * keeps working with the screen off.
 */
class TripService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var player: MediaPlayer? = null
    private var dest: Location? = null
    private var name = "Destination"
    private var alertM = 1000
    private var arrived = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (intent == null || !intent.hasExtra(EXTRA_LAT) || !Permissions.hasLocation(this)) {
            shutdown()
            return START_NOT_STICKY
        }
        name = intent.getStringExtra(EXTRA_NAME) ?: "Destination"
        alertM = intent.getIntExtra(EXTRA_ALERT_M, 1000)
        dest = Location("dest").apply {
            latitude = intent.getDoubleExtra(EXTRA_LAT, 0.0)
            longitude = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        }
        arrived = false
        stopRinging()
        state.value = TripState(name, alertM)

        ServiceCompat.startForeground(
            this, NOTIF_ID, progressNotification("Waiting for GPS…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        startUpdates()
        return START_REDELIVER_INTENT
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::onLocation)
            }
        }
        callback = cb
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        client.requestLocationUpdates(req, cb, mainLooper)
    }

    private fun onLocation(loc: Location) {
        val d = dest ?: return
        if (arrived || loc.accuracy > 500f) return
        val dist = loc.distanceTo(d)
        val eta = if (loc.hasSpeed() && loc.speed > 1.5f) (dist / loc.speed / 60f).toInt().coerceAtLeast(1) else null
        state.value = TripState(name, alertM, dist.toInt(), eta)

        if (dist <= alertM) {
            arrive(dist.toInt())
        } else {
            val text = "${formatDistance(dist.toInt())} to go" + (eta?.let { " · ~$it min" } ?: "")
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, progressNotification(text))
        }
    }

    private fun arrive(distM: Int) {
        arrived = true
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        state.value = TripState(name, alertM, distM, null, arrived = true)
        startRinging()

        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .putExtra(Notifier.EXTRA_ALARM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, Notifier.CH_TRIP_ALARM)
            .setSmallIcon(R.drawable.ic_stat_headsup)
            .setContentTitle("Get ready — $name is close")
            .setContentText("About ${formatDistance(distM)} away")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Dismiss", stopIntent(this))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    private fun progressNotification(text: String) = NotificationCompat.Builder(this, Notifier.CH_TRIP)
        .setSmallIcon(R.drawable.ic_stat_headsup)
        .setContentTitle("Trip to $name")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(0, "Stop trip", stopIntent(this))
        .build()

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@TripService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE).let { it as? Vibrator }
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun stopRinging() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        @Suppress("DEPRECATION")
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    private fun shutdown() {
        stopRinging()
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        callback?.let { client.removeLocationUpdates(it) }
        state.value = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4242
        private const val ACTION_STOP = "com.elkrrai.headsup.STOP_TRIP"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"
        private const val EXTRA_ALERT_M = "alert_m"

        private val _state = MutableStateFlow<TripState?>(null)
        private val state get() = _state

        /** Current trip, or null when no trip is running. */
        val tripState: StateFlow<TripState?> = _state

        fun start(ctx: Context, name: String, lat: Double, lng: Double, alertM: Int) {
            val i = Intent(ctx, TripService::class.java)
                .putExtra(EXTRA_NAME, name).putExtra(EXTRA_LAT, lat)
                .putExtra(EXTRA_LNG, lng).putExtra(EXTRA_ALERT_M, alertM)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TripService::class.java).setAction(ACTION_STOP))
        }

        private fun stopIntent(ctx: Context) = PendingIntent.getService(
            ctx, 3, Intent(ctx, TripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun formatDistance(m: Int) = if (m >= 1000) String.format("%.1f km", m / 1000.0) else "$m m"
    }
}
