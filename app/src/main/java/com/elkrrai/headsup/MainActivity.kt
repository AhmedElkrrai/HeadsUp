package com.elkrrai.headsup

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.elkrrai.headsup.geo.GeofenceManager
import com.elkrrai.headsup.trip.TripService
import com.elkrrai.headsup.notify.Notifier
import com.elkrrai.headsup.ui.AppViewModel
import com.elkrrai.headsup.ui.HomeScreen
import com.elkrrai.headsup.ui.PlaceEditorScreen
import com.elkrrai.headsup.ui.ReminderEditorScreen
import com.elkrrai.headsup.ui.Screen
import com.elkrrai.headsup.ui.theme.HeadsUpTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    // Step 2 (Android 10+): background location must be requested on its own, after foreground location.
    private val backgroundLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
        GeofenceManager.sync(this)
    }

    private val foregroundLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshPermissions()
        if (Permissions.hasLocation(this) && !Permissions.hasBackgroundLocation(this)) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            GeofenceManager.sync(this)
        }
    }

    private fun requestPermissions() {
        when {
            !Permissions.hasLocation(this) || !Permissions.hasNotifications(this) -> {
                val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
                foregroundLauncher.launch(list.toTypedArray())
            }
            !Permissions.hasBackgroundLocation(this) -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so keep system bar icons light even if the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            // If the trip alarm goes off while the app is open, jump to the Trip screen (where you can dismiss it).
            val alarmRinging = TripService.tripState.collectAsState().value?.arrived == true
            LaunchedEffect(alarmRinging) { if (alarmRinging) showTripAlarm() }

            HeadsUpTheme {
                when (val s = vm.screen) {
                    Screen.Home -> HomeScreen(vm, onRequestPermissions = ::requestPermissions)
                    Screen.ReminderEditor -> ReminderEditorScreen(vm)
                    is Screen.PlaceEditor -> PlaceEditorScreen(vm, s.placeId, s.forTrip)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Opening the app (from the launcher, recents…) while a trip alarm is ringing lands on the Trip screen.
        // If a place reminder went off since you last looked, it opens on Reminders instead
        // (unless you're in the middle of editing something).
        if (TripService.tripState.value?.arrived == true) showTripAlarm()
        else if (Notifier.reminderAlertPending) {
            Notifier.reminderAlertPending = false
            if (vm.screen == Screen.Home) vm.tab = 0
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    private fun showTripAlarm() {
        vm.screen = Screen.Home
        vm.tab = 1
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        when {
            // A list shared from WhatsApp (or any app) becomes the note of a new reminder.
            i.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true -> {
                val text = i.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) vm.newReminder(text)
            }
            i.hasExtra(Notifier.EXTRA_OPEN_REMINDER) ->
                i.getStringExtra(Notifier.EXTRA_OPEN_REMINDER)?.let { vm.editReminder(it) }
            // Trip alarm full-screen intent: show over the lock screen and light the display.
            i.getBooleanExtra(Notifier.EXTRA_ALARM, false) -> {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                vm.screen = Screen.Home
                vm.tab = 1
            }
        }
    }
}
