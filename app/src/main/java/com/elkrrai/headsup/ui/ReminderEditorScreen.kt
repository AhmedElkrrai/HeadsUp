package com.elkrrai.headsup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elkrrai.headsup.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(vm: AppViewModel) {
    val draft = vm.draft ?: return
    val places by Store.places.collectAsState()
    val reminders by Store.reminders.collectAsState()
    val existing = draft.id?.let { id -> reminders.firstOrNull { it.id == id } }
    var menuOpen by remember { mutableStateOf(false) }
    val place = places.firstOrNull { it.id == draft.placeId }

    BackHandler { vm.closeEditor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New reminder" else "Edit reminder") },
                navigationIcon = { TextButton(onClick = { vm.closeEditor() }) { Text("Cancel") } },
                actions = {
                    if (existing != null) {
                        TextButton(onClick = {
                            Store.deleteReminder(existing.id)
                            vm.closeEditor()
                        }) { Text("Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title, onValueChange = { vm.draft = draft.copy(title = it) }, singleLine = true,
                label = { Text("What do you need to do?") }, modifier = Modifier.fillMaxWidth(),
            )

            Text("Remind me near", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(place?.let { "${it.name} (${it.radiusM} m)" } ?: "Choose a place")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    places.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                vm.draft = draft.copy(placeId = p.id)
                                menuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("+ Pin a new place…") },
                        onClick = {
                            menuOpen = false
                            vm.openPlaceEditor(null)
                        },
                    )
                }
            }
            if (place != null) {
                TextButton(onClick = { vm.openPlaceEditor(place.id) }) { Text("Edit ${place.name} (move pin / change circle)") }
            }

            OutlinedTextField(
                value = draft.note, onValueChange = { vm.draft = draft.copy(note = it) },
                label = { Text("Note / shopping list") }, minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            if (existing != null && existing.isSnoozed()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        snoozeLabel(existing),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { Store.clearSnooze(existing.id) }) { Text("Wake now") }
                }
            }
            if (existing != null && !existing.done) {
                Text("Snooze", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Store.snoozeUntilNextVisit(existing.id) }) { Text("Next visit") }
                    OutlinedButton(onClick = { Store.snoozeUntil(existing.id, System.currentTimeMillis() + 3_600_000L) }) { Text("1 hour") }
                    OutlinedButton(onClick = { Store.snoozeUntil(existing.id, System.currentTimeMillis() + 4 * 3_600_000L) }) { Text("4 hours") }
                }
            }

            Button(
                enabled = draft.placeId != null,
                onClick = { vm.saveDraft() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text(if (draft.placeId == null) "Pick a place to save" else "Save") }
        }
    }
}
