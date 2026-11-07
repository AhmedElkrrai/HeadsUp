package com.elkrrai.headsup.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elkrrai.headsup.MainActivity
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.data.Reminder

object Notifier {
    const val CH_ERRAND = "errands"
    const val CH_TRIP_ALARM = "trip_alarm"
    const val CH_TRIP = "trip_progress"

    const val EXTRA_OPEN_REMINDER = "open_reminder"
    const val EXTRA_ALARM = "alarm"

    /** Set when a place reminder fires; the main screen consumes it to open on Reminders next time it appears. */
    @Volatile
    var reminderAlertPending = false

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ERRAND, "Place reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown when you arrive near a place with a pending reminder"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            // Sound/vibration are driven by TripService so the alarm keeps ringing until dismissed.
            NotificationChannel(CH_TRIP_ALARM, "Trip arrival alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when you are close to your destination"
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TRIP, "Trip in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Distance to your destination while a trip is running"
            }
        )
    }

    fun notifyErrand(ctx: Context, r: Reminder, place: Place) {
        if (!Permissions.hasNotifications(ctx)) return
        reminderAlertPending = true
        val open = PendingIntent.getActivity(
            ctx, r.id.hashCode(),
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_REMINDER, r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = r.note.ifBlank { "You're near ${place.name}" }
        val n = NotificationCompat.Builder(ctx, CH_ERRAND)
            .setSmallIcon(R.drawable.ic_stat_headsup)
            .setContentTitle("${r.title} · ${place.name}")
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Next visit", ReminderActionReceiver.pending(ctx, ReminderActionReceiver.ACTION_NEXT_VISIT, r.id))
            .addAction(0, "1 hour", ReminderActionReceiver.pending(ctx, ReminderActionReceiver.ACTION_1H, r.id))
            .addAction(0, "Done", ReminderActionReceiver.pending(ctx, ReminderActionReceiver.ACTION_DONE, r.id))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(r.id.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    /** "Test alert" button: proves notifications work without walking anywhere. */
    fun notifyTest(ctx: Context) {
        if (!Permissions.hasNotifications(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ERRAND)
            .setSmallIcon(R.drawable.ic_stat_headsup)
            .setContentTitle("Test alert")
            .setContentText("Heads Up is working. Alerts like this appear when you get near a place.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(TEST_NOTIF_ID, n)
        } catch (_: SecurityException) {
        }
    }

    private const val TEST_NOTIF_ID = 999001

    fun cancel(ctx: Context, reminderId: String) {
        NotificationManagerCompat.from(ctx).cancel(reminderId.hashCode())
    }
}
