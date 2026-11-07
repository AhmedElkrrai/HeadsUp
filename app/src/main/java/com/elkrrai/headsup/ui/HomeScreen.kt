package com.elkrrai.headsup.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.data.Reminder
import com.elkrrai.headsup.data.Store
import com.elkrrai.headsup.trip.TripService
import java.text.DateFormat
import java.util.Date

fun snoozeLabel(r: Reminder): String = when {
    r.waitForReentry -> "Snoozed until your next visit"
    r.snoozedUntil > System.currentTimeMillis() ->
        "Snoozed until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.snoozedUntil))
    else -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val places by Store.places.collectAsState()
    val reminders by Store.reminders.collectAsState()
    val titles = listOf("Reminders", "Trip", "Places")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Heads Up") }) },
        floatingActionButton = {
            when (vm.tab) {
                0 -> ExtendedFloatingActionButton(onClick = { vm.newReminder() }) { Text("+ Reminder") }
                2 -> ExtendedFloatingActionButton(onClick = { vm.openPlaceEditor(null) }) { Text("+ Place") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!vm.permissions.allGranted) PermissionBanner(vm, onRequestPermissions)
            TabRow(selectedTabIndex = vm.tab) {
                titles.forEachIndexed { i, t -> Tab(selected = vm.tab == i, onClick = { vm.tab = i }, text = { Text(t) }) }
            }
            when (vm.tab) {
                0 -> RemindersTab(vm, places, reminders)
                1 -> TripTab(vm, places, onRequestPermissions)
                else -> PlacesTab(vm, places, reminders)
            }
        }
    }
}

@Composable
private fun PermissionBanner(vm: AppViewModel, onRequest: () -> Unit) {
    val p = vm.permissions
    val text = when {
        !p.location -> "Heads Up needs location access to remind you when you arrive somewhere."
        !p.background -> "Choose \"Allow all the time\" for location so reminders work while your phone is in your pocket."
        else -> "Allow notifications so reminders can alert you."
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequest) { Text("Fix permissions") }
        }
    }
}

// ---------------- Reminders ----------------

@Composable
private fun RemindersTab(vm: AppViewModel, places: List<Place>, reminders: List<Reminder>) {
    val byId = places.associateBy { it.id }
    val pending = reminders.filter { !it.done }.sortedByDescending { it.createdAt }
    val done = reminders.filter { it.done }.sortedByDescending { it.createdAt }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (reminders.isEmpty()) {
            item {
                Text(
                    "No reminders yet.\n\nTap \"+ Reminder\", pin your supermarket on the map, and you'll get a heads-up when you get close. " +
                        "You can also share a shopping list from WhatsApp straight into this app.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(pending, key = { it.id }) { r -> ReminderCard(r, byId[r.placeId], onClick = { vm.editReminder(r.id) }) }
        if (done.isNotEmpty()) {
            item { Text("Done", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
            items(done, key = { it.id }) { r -> ReminderCard(r, byId[r.placeId], onClick = { vm.editReminder(r.id) }) }
        }
    }
}

@Composable
private fun ReminderCard(r: Reminder, place: Place?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.done, onCheckedChange = { Store.setDone(r.id, it) })
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text("Near ${place?.name ?: "(place removed)"}", style = MaterialTheme.typography.bodySmall)
                if (r.note.isNotBlank()) {
                    Text(
                        r.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (r.isSnoozed()) {
                    Text(snoozeLabel(r), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------------- Places ----------------

@Composable
private fun PlacesTab(vm: AppViewModel, places: List<Place>, reminders: List<Reminder>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (places.isEmpty()) item { Text("No places yet. Tap \"+ Place\" to pin one.", style = MaterialTheme.typography.bodyLarge) }
        items(places, key = { it.id }) { p ->
            val n = reminders.count { it.placeId == p.id && !it.done }
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.openPlaceEditor(p.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("Circle ${p.radiusM} m · $n pending reminder${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------- Trip ----------------

@Composable
private fun TripTab(vm: AppViewModel, places: List<Place>, onRequestPermissions: () -> Unit) {
    val ctx: Context = LocalContext.current
    val trip by TripService.tripState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val place = places.firstOrNull { it.id == vm.tripPlaceId }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val t = trip
        if (t != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (t.arrived) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (t.arrived) "Get ready! Almost there" else "Trip in progress", style = MaterialTheme.typography.titleLarge)
                    Text("To: ${t.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            t.distanceM == null -> "Waiting for GPS…"
                            else -> TripService.formatDistance(t.distanceM) + " away" + (t.etaMin?.let { " · about $it min" } ?: "")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("Alarm at ${TripService.formatDistance(t.alertM)} from destination", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { TripService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (t.arrived) "Dismiss alarm" else "Stop trip")
                    }
                }
            }
        } else {
            Text(
                "Riding a bus or train? Pick where you get off and I'll ring an alarm before you get there.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Destination", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(place?.name ?: "Choose destination")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    places.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = {
                            vm.tripPlaceId = p.id
                            menuOpen = false
                        })
                    }
                    DropdownMenuItem(text = { Text("+ Pin a new destination…") }, onClick = {
                        menuOpen = false
                        vm.openPlaceEditor(null)
                    })
                }
            }
            Text("Alarm ${TripService.formatDistance(vm.tripAlertM)} before arriving", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = vm.tripAlertM.toFloat(), onValueChange = { vm.tripAlertM = (it / 100).toInt() * 100 },
                valueRange = 300f..5000f,
            )
            Text(
                "Tip: bigger for a train (time to gather your things), smaller for a bus stop.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                enabled = place != null,
                onClick = {
                    val pl = place ?: return@Button
                    if (!vm.permissions.location) {
                        onRequestPermissions()
                    } else {
                        TripService.start(ctx, pl.name, pl.lat, pl.lng, vm.tripAlertM)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (place == null) "Pick a destination" else "Start trip") }
        }
    }
}
