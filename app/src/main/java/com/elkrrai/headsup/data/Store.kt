package com.elkrrai.headsup.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Tiny JSON-file store. Data is a handful of rows, so this beats a database dependency. */
object Store {
    private var file: File? = null
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())

    val places: StateFlow<List<Place>> = _places
    val reminders: StateFlow<List<Reminder>> = _reminders

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "headsup.json").also { load(it) }
    }

    fun place(id: String): Place? = _places.value.firstOrNull { it.id == id }
    fun reminder(id: String): Reminder? = _reminders.value.firstOrNull { it.id == id }

    // ---- places ----

    @Synchronized
    fun upsertPlace(p: Place) {
        _places.value = _places.value.filterNot { it.id == p.id } + p
        save()
    }

    @Synchronized
    fun deletePlace(id: String) {
        _places.value = _places.value.filterNot { it.id == id }
        _reminders.value = _reminders.value.filterNot { it.placeId == id }
        save()
    }

    // ---- reminders ----

    @Synchronized
    fun upsertReminder(r: Reminder) {
        val list = _reminders.value
        _reminders.value = if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r
        save()
    }

    @Synchronized
    fun deleteReminder(id: String) {
        _reminders.value = _reminders.value.filterNot { it.id == id }
        save()
    }

    @Synchronized
    private fun edit(id: String, f: (Reminder) -> Reminder) {
        _reminders.value = _reminders.value.map { if (it.id == id) f(it) else it }
        save()
    }

    fun setDone(id: String, done: Boolean) = edit(id) {
        it.copy(done = done, snoozedUntil = 0L, waitForReentry = false)
    }

    fun snoozeUntil(id: String, until: Long) = edit(id) { it.copy(snoozedUntil = until, waitForReentry = false) }

    /** Silent until the user has left the place's circle and entered it again. */
    fun snoozeUntilNextVisit(id: String) = edit(id) { it.copy(waitForReentry = true, snoozedUntil = 0L) }

    fun clearSnooze(id: String) = edit(id) { it.copy(waitForReentry = false, snoozedUntil = 0L) }

    /** Called when the user leaves a place: "next visit" snoozes re-arm. */
    @Synchronized
    fun rearmAfterExit(placeId: String) {
        if (_reminders.value.none { it.placeId == placeId && it.waitForReentry }) return
        _reminders.value = _reminders.value.map {
            if (it.placeId == placeId && it.waitForReentry) it.copy(waitForReentry = false) else it
        }
        save()
    }

    // ---- persistence ----

    private fun load(f: File) {
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            _places.value = root.optJSONArray("places").toList { o ->
                Place(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lng"), o.optInt("reminderRadiusM", o.optInt("radiusM", Radius.REMINDER_DEFAULT)), o.optInt("tripRadiusM", Radius.TRIP_DEFAULT))
            }
            _reminders.value = root.optJSONArray("reminders").toList { o ->
                Reminder(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    note = o.optString("note", ""),
                    placeId = o.getString("placeId"),
                    done = o.optBoolean("done", false),
                    snoozedUntil = o.optLong("snoozedUntil", 0L),
                    waitForReentry = o.optBoolean("waitForReentry", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        } catch (_: Exception) {
            // Corrupt file: start empty rather than crash.
        }
    }

    private fun save() {
        val f = file ?: return
        val root = JSONObject()
        root.put("places", JSONArray().also { arr ->
            _places.value.forEach {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.lat).put("lng", it.lng).put("reminderRadiusM", it.reminderRadiusM).put("tripRadiusM", it.tripRadiusM))
            }
        })
        root.put("reminders", JSONArray().also { arr ->
            _reminders.value.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("title", it.title).put("note", it.note).put("placeId", it.placeId)
                        .put("done", it.done).put("snoozedUntil", it.snoozedUntil)
                        .put("waitForReentry", it.waitForReentry).put("createdAt", it.createdAt)
                )
            }
        })
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(root.toString())
            tmp.delete()
        }
    }

    private inline fun <T> JSONArray?.toList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { map(getJSONObject(it)) }
    }
}
