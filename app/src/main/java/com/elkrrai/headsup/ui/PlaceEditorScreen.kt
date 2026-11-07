package com.elkrrai.headsup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.data.Store
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorScreen(vm: AppViewModel, placeId: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(placeId) { placeId?.let { Store.place(it) } }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var pin by remember { mutableStateOf(existing?.let { GeoPoint(it.lat, it.lng) }) }
    var radius by remember { mutableIntStateOf(existing?.radiusM ?: 150) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<GeoPoint?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    fun focusOn(p: GeoPoint) {
        focus = p
        focusKey++
    }

    fun useMyLocation() {
        if (!Permissions.hasLocation(ctx)) {
            message = "Allow location access first (use the banner on the main screen)."
            return
        }
        message = "Finding you…"
        currentLocation(ctx) { loc ->
            if (loc == null) {
                message = "Couldn't get a GPS fix. Try again outdoors."
            } else {
                message = null
                val p = GeoPoint(loc.first, loc.second)
                pin = p
                focusOn(p)
            }
        }
    }

    // Start centred on the user when adding a new place.
    remember {
        if (existing == null && Permissions.hasLocation(ctx)) {
            currentLocation(ctx) { loc -> loc?.let { focusOn(GeoPoint(it.first, it.second)) } }
        }
        true
    }

    BackHandler { vm.closePlaceEditor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New place" else "Edit place") },
                navigationIcon = { TextButton(onClick = { vm.closePlaceEditor() }) { Text("Cancel") } },
                actions = {
                    if (existing != null) {
                        TextButton(onClick = {
                            Store.deletePlace(existing.id)
                            vm.closePlaceEditor()
                        }) { Text("Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name (e.g. Carrefour, Bus stop)") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search address or place") }, modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    enabled = query.isNotBlank() && !searching,
                    onClick = {
                        searching = true
                        message = null
                        scope.launch {
                            hits = searchPlaces(query)
                            searching = false
                            if (hits.isEmpty()) message = "No results (or no internet)."
                        }
                    },
                ) { Text(if (searching) "…" else "Search") }
            }
            hits.take(4).forEach { hit ->
                Text(
                    hit.label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val p = GeoPoint(hit.lat, hit.lng)
                        pin = p
                        focusOn(p)
                        if (name.isBlank()) name = hit.label.substringBefore(",")
                        hits = emptyList()
                    }.padding(vertical = 4.dp),
                )
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            MapPicker(
                pin = pin, radiusM = radius, focus = focus, focusKey = focusKey,
                onPick = { pin = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Text(
                if (pin == null) "Tap the map to drop a pin" else "Reminder circle: $radius m",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = radius.toFloat(), onValueChange = { radius = (it / 10).toInt() * 10 },
                valueRange = 50f..500f, enabled = pin != null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                OutlinedButton(onClick = ::useMyLocation, modifier = Modifier.weight(1f)) { Text("My location") }
                Button(
                    enabled = pin != null,
                    onClick = {
                        val p = pin ?: return@Button
                        val id = existing?.id ?: Store.newId()
                        Store.upsertPlace(Place(id, name.trim().ifBlank { "Pinned place" }, p.latitude, p.longitude, radius))
                        vm.closePlaceEditor(id)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save place") }
            }
        }
    }
}
