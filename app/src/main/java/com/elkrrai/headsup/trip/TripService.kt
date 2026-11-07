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
import android.widget.RemoteViews

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
        if (loc.accuracy > 500f) return
        val dist = loc.distanceTo(d)
        if (arrived) {
            // The alarm keeps ringing until dismissed; keep the screen and the notification's distance live.
            state.value = TripState(name, alertM, dist.toInt(), null, arrived = true)
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, alarmNotification(dist.toInt(), first = false))
            return
        }
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
        // Location updates stay on while ringing (see onLocation) so the distance keeps updating.
        state.value = TripState(name, alertM, distM, null, arrived = true)
        startRinging()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, alarmNotification(distM, first = true))
    }

    /** The ringing alarm: a dark neon card (circle, distance, Dismiss) instead of a plain system notification. */
    private fun alarmNotification(distM: Int, first: Boolean): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .putExtra(Notifier.EXTRA_ALARM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dismiss = stopIntent(this)
        val away = formatDistance(distM)

        // Same wording as the app: "1 km", not "1.0 km".
        val alarmAt = if (alertM >= 1000 && alertM % 1000 == 0) "${alertM / 1000} km" else formatDistance(alertM)

        val big = RemoteViews(packageName, R.layout.notif_trip_alarm_big).apply {
            setTextViewText(R.id.notif_title, "$name — wake up!")
            setTextViewText(R.id.notif_text, "You're $away away. The alarm was set for $alarmAt. Pack up and get ready to get off.")
            setOnClickPendingIntent(R.id.notif_dismiss, dismiss)
            setOnClickPendingIntent(R.id.notif_open, open)
        }
        // A "colorized" notification: the whole system card turns deep red (full width, no card inside a card).
        return NotificationCompat.Builder(this, Notifier.CH_TRIP_ALARM)
            .setSmallIcon(R.drawable.ic_stat_headsup)
            .setColor(0xFFC62828.toInt())
            .setColorized(true)
            .setContentTitle("$name — wake up!")
            .setContentText("$away to go. Get your things.")
            // Collapsed / heads-up use the plain title + text above; expanded gets real pill buttons.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(big)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            // Distance refreshes shouldn't pop the banner or buzz again.
            .setOnlyAlertOnce(!first)
            .setContentIntent(open)
            .apply { if (first) setFullScreenIntent(open, true) }
            .build()
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
