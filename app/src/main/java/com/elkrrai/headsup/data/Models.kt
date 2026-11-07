package com.elkrrai.headsup.data

/** A saved spot on the map. [radiusM] is the circle used for errand reminders. */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 150,
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
