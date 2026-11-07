package com.elkrrai.headsup.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elkrrai.headsup.data.Store

/** Handles the Snooze / Done buttons on an errand notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        when (intent.action) {
            ACTION_NEXT_VISIT -> Store.snoozeUntilNextVisit(id)
            ACTION_1H -> Store.snoozeUntil(id, System.currentTimeMillis() + 60 * 60 * 1000L)
            ACTION_DONE -> Store.setDone(id, true)
        }
        Notifier.cancel(context, id)
    }

    companion object {
        const val ACTION_NEXT_VISIT = "com.elkrrai.headsup.NEXT_VISIT"
        const val ACTION_1H = "com.elkrrai.headsup.SNOOZE_1H"
        const val ACTION_DONE = "com.elkrrai.headsup.DONE"
        private const val EXTRA_ID = "reminder_id"

        fun pending(ctx: Context, action: String, reminderId: String): PendingIntent {
            val i = Intent(ctx, ReminderActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, reminderId)
            return PendingIntent.getBroadcast(
                ctx, (reminderId + action).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
