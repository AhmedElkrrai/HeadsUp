package com.elkrrai.headsup.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.data.Reminder
import com.elkrrai.headsup.data.Store

sealed interface Screen {
    data object Home : Screen
    data object ReminderEditor : Screen
    data class PlaceEditor(val placeId: String?) : Screen
}

/** Unsaved state of the reminder editor (kept here so it survives a trip to the place editor). */
data class Draft(
    val id: String?,
    val title: String,
    val note: String,
    val placeId: String?,
)

data class PermissionState(val location: Boolean, val background: Boolean, val notifications: Boolean) {
    val allGranted get() = location && background && notifications
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var screen by mutableStateOf<Screen>(Screen.Home)
    var tab by mutableIntStateOf(0)
    var draft by mutableStateOf<Draft?>(null)
    var tripPlaceId by mutableStateOf<String?>(null)
    var tripAlertM by mutableIntStateOf(1000)
    var permissions by mutableStateOf(readPermissions())

    fun refreshPermissions() {
        permissions = readPermissions()
    }

    private fun readPermissions(): PermissionState {
        val c = getApplication<Application>()
        return PermissionState(Permissions.hasLocation(c), Permissions.hasBackgroundLocation(c), Permissions.hasNotifications(c))
    }

    /** New reminder; [sharedText] is the list shared from WhatsApp, if any. */
    fun newReminder(sharedText: String? = null) {
        val place = Store.reminders.value.maxByOrNull { it.createdAt }?.placeId?.takeIf { Store.place(it) != null }
            ?: Store.places.value.firstOrNull()?.id
        draft = Draft(
            id = null,
            title = if (sharedText != null) "Groceries" else "",
            note = sharedText?.trim().orEmpty(),
            placeId = place,
        )
        screen = Screen.ReminderEditor
        tab = 0
    }

    fun editReminder(id: String) {
        val r = Store.reminder(id) ?: return
        draft = Draft(r.id, r.title, r.note, r.placeId)
        screen = Screen.ReminderEditor
        tab = 0
    }

    fun saveDraft() {
        val d = draft ?: return
        val placeId = d.placeId ?: return
        val existing = d.id?.let { Store.reminder(it) }
        Store.upsertReminder(
            (existing ?: Reminder(id = Store.newId(), title = "", placeId = placeId)).copy(
                title = d.title.trim().ifBlank { "Reminder" },
                note = d.note.trim(),
                placeId = placeId,
            )
        )
        closeEditor()
    }

    fun closeEditor() {
        draft = null
        screen = Screen.Home
    }

    fun openPlaceEditor(placeId: String?) {
        screen = Screen.PlaceEditor(placeId)
    }

    /** After saving/cancelling a place: back to the reminder editor if one is in progress. */
    fun closePlaceEditor(savedPlaceId: String? = null) {
        if (savedPlaceId != null) {
            draft = draft?.copy(placeId = savedPlaceId)
            tripPlaceId = savedPlaceId
        }
        screen = if (draft != null) Screen.ReminderEditor else Screen.Home
    }
}
