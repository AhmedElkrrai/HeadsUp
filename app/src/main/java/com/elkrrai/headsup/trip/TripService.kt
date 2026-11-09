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
    /** Smoothed speed, km/h, once there's a reading. */
    val speedKmh: Int? = null,
    /** Distance (m) at the first GPS fix: "passed" is this minus [distanceM]. */
    val startM: Int? = null,
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

    /** Distance at the first GPS fix, so the notification's bar can show how far along the trip is. */
    private var startDistM = 0

    /** Smoothed speed in m/s (0 until the first reading), so the time estimate doesn't jump around. */
    private var speedMs = 0f

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
        startDistM = 0
        speedMs = 0f
        stopRinging()
        state.value = TripState(name, alertM)

        ServiceCompat.startForeground(
            this, NOTIF_ID, progressNotification("Waiting for GPS…", null, 0.96f),
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
        if (startDistM == 0) startDistM = dist.toInt()
        if (loc.hasSpeed()) speedMs = if (speedMs == 0f) loc.speed else 0.7f * speedMs + 0.3f * loc.speed
        val speedKmh = if (speedMs > 0.5f) (speedMs * 3.6f).toInt() else null
        if (arrived) {
            // The alarm keeps ringing until dismissed; keep the screen and the notification's distance live.
            state.value = TripState(name, alertM, dist.toInt(), null, arrived = true, speedKmh = speedKmh, startM = startDistM)
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, alarmNotification(dist.toInt(), first = false))
            return
        }
        val eta = if (speedMs > 1.5f) (dist / speedMs / 60f).toInt().coerceAtLeast(1) else null
        state.value = TripState(name, alertM, dist.toInt(), eta, speedKmh = speedKmh, startM = startDistM)

        if (dist <= alertM) {
            arrive(dist.toInt())
        } else {
            // The bar runs from the first fix (0) to the destination (1); the white tick marks where the alarm rings.
            val total = startDistM.coerceAtLeast(1)
            val frac = ((startDistM - dist) / total).coerceIn(0f, 1f)
            val tick = ((startDistM - alertM).toFloat() / total).coerceIn(0f, 1f)
            val text = "${formatDistance(dist.toInt())} to go · alarm at ${alarmLabel(alertM)}"
            val detail = "${formatDistance(dist.toInt())} to go" + (eta?.let { " · about $it min" } ?: "") +
                "\nAlarm rings at ${alarmLabel(alertM)}"
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, progressNotification(text, frac, tick, detail))
        }
    }

    private fun arrive(distM: Int) {
        arrived = true
        // Location updates stay on while ringing (see onLocation) so the distance keeps updating.
        state.value = state.value?.copy(distanceM = distM, etaMin = null, arrived = true)
            ?: TripState(name, alertM, distM, null, arrived = true)
        startRinging()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, alarmNotification(distM, first = true))
    }

    /** The ringing alarm notification: a dark card with a red neon edge (circle, distance, Dismiss). */
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
        val alarmAt = alarmLabel(alertM)

        val small = RemoteViews(packageName, R.layout.notif_trip_alarm_small).apply {
            setTextViewText(R.id.notif_name, name)
            setTextViewText(R.id.notif_away, "$away away")
            setOnClickPendingIntent(R.id.notif_dismiss, dismiss)
        }
        val big = RemoteViews(packageName, R.layout.notif_trip_alarm_big).apply {
            setTextViewText(R.id.notif_name, name)
            setTextViewText(R.id.notif_away, away)
            setTextViewText(R.id.notif_alarm_at, alarmAt)
            setOnClickPendingIntent(R.id.notif_dismiss, dismiss)
            setOnClickPendingIntent(R.id.notif_open, open)
        }
        return NotificationCompat.Builder(this, Notifier.CH_TRIP_ALARM)
            .setSmallIcon(R.drawable.ic_stat_headsup)
            .setColor(0xFFE53935.toInt())
            // Plain text too, for the lock screen, watches and screen readers.
            .setContentTitle("$name is close")
            .setContentText("$away away. Time to get ready.")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomHeadsUpContentView(small)
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

    /**
     * The quiet "trip in progress" notification: the whole card takes the app's dark teal (colorized); inside it,
     * our own layout with a glowing neon bar ([fraction] 0..1 along the trip, or null while waiting for GPS, with
     * a white tick at [tick] where the alarm rings) and, when expanded, a real Stop trip pill.
     */
    private fun progressNotification(text: String, fraction: Float?, tick: Float, detail: String = text): android.app.Notification {
        val stop = stopIntent(this)
        val bar = AlarmArt.progressBar(fraction, tick)
        val lines = detail.split("\n")

        val small = RemoteViews(packageName, R.layout.notif_trip_progress_small).apply {
            setTextViewText(R.id.notif_title, "Trip to $name")
            setTextViewText(R.id.notif_text, text)
            setImageViewBitmap(R.id.notif_bar, bar)
        }
        val big = RemoteViews(packageName, R.layout.notif_trip_progress_big).apply {
            setTextViewText(R.id.notif_title, "Trip to $name")
            setTextViewText(R.id.notif_text, lines.first())
            if (lines.size > 1) setTextViewText(R.id.notif_text2, lines[1]) else setViewVisibility(R.id.notif_text2, android.view.View.GONE)
            setImageViewBitmap(R.id.notif_bar, bar)
            setOnClickPendingIntent(R.id.notif_stop, stop)
        }
        return NotificationCompat.Builder(this, Notifier.CH_TRIP)
            .setSmallIcon(R.drawable.ic_stat_headsup)
            .setColor(0xFF0F3638.toInt())
            .setColorized(true)
            // Plain text too, for the lock screen, watches and screen readers.
            .setContentTitle("Trip to $name")
            .setContentText(text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
    }

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

        /** Same wording as the app: "1 km", not "1.0 km". */
        fun alarmLabel(m: Int) = if (m >= 1000 && m % 1000 == 0) "${m / 1000} km" else formatDistance(m)

        fun formatDistance(m: Int) = if (m >= 1000) String.format("%.1f km", m / 1000.0) else "$m m"
    }
}
