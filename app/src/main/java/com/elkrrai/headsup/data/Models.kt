package com.elkrrai.headsup.data

/** What a place is being used for: errand reminders (small circle) or trip alarms (large circle). */
enum class PlaceMode { Reminder, Trip }

/** Allowed radii, in metres. */
object Radius {
    const val REMINDER_MIN = 60
    const val REMINDER_MAX = 500
    const val REMINDER_DEFAULT = 150

    /** Trip alarm distances (the slider snaps to these). */
    val TRIP_STOPS = listOf(500, 1000, 2000, 3000, 5000)
    const val TRIP_DEFAULT = 1000
}

/**
 * A saved spot on the map. Every place has both circles, so it can be used for errand reminders and as a trip
 * destination: [reminderRadiusM] is where reminders fire, [tripRadiusM] is how far out the trip alarm rings.
 */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val reminderRadiusM: Int = Radius.REMINDER_DEFAULT,
    val tripRadiusM: Int = Radius.TRIP_DEFAULT,
)

/**
 * An errand tied to a [Place]. Fires when you enter the place's circle.
 *
 * Snooze has two flavours:
 *  - [snoozedUntil]: ignore entries until that time.
 *  - [waitForReentry]: ignore until you have left the circle and come back
 *    (e.g. shop is next to home: snooze when leaving, remind on the way back).
 */
data class Reminder(
    val id: String,
    val title: String,
    val note: String = "",
    val placeId: String,
    val done: Boolean = false,
    val snoozedUntil: Long = 0L,
    val waitForReentry: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun isSnoozed(now: Long = System.currentTimeMillis()): Boolean =
        !done && (waitForReentry || snoozedUntil > now)
}
