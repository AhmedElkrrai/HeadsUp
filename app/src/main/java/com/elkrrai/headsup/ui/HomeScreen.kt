package com.elkrrai.headsup.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.data.Reminder
import com.elkrrai.headsup.data.Store
import com.elkrrai.headsup.notify.Notifier
import com.elkrrai.headsup.trip.TripService
import com.elkrrai.headsup.ui.theme.AmberContainer
import com.elkrrai.headsup.ui.theme.AmberEdge
import com.elkrrai.headsup.ui.theme.Ink
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NavIdle
import com.elkrrai.headsup.ui.theme.NeonAmber
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.SurfaceCard
import com.elkrrai.headsup.ui.theme.SurfaceNav
import com.elkrrai.headsup.ui.theme.glow
import org.osmdroid.util.GeoPoint
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val CheckGrey = Color(0xFF5F7084)
private val CardShape = RoundedCornerShape(20.dp)

fun snoozeLabel(r: Reminder): String = when {
    r.waitForReentry -> "Snoozed until next visit"
    r.snoozedUntil > System.currentTimeMillis() ->
        "Snoozed until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.snoozedUntil))
    else -> ""
}

private val tabTitles = listOf("Reminders", "Trip", "Places")
private val tabIcons = listOf(R.drawable.ic_bell, R.drawable.ic_navigate, R.drawable.ic_pin)

@Composable
fun HomeScreen(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val places by Store.places.collectAsState()
    val reminders by Store.reminders.collectAsState()

    // Where you are (roughly), for the "1.2 km" on each card.
    var here by remember { mutableStateOf(LastLocation.cached(ctx)) }
    LaunchedEffect(vm.permissions.location) {
        if (vm.permissions.location) lastKnownLocation(ctx) { p -> if (p != null) here = p }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // No "+" on an empty Reminders list: the big "Add your first reminder" button does that job.
            if (vm.tab == 2 || (vm.tab == 0 && reminders.isNotEmpty())) {
                // Just a "+": same button on Reminders and Places (the description is for screen readers).
                FloatingActionButton(
                    onClick = { if (vm.tab == 0) vm.newReminder() else vm.openPlaceEditor(null) },
                    containerColor = NeonCyan,
                    contentColor = OnNeon,
                    shape = CircleShape,
                    modifier = Modifier.glow(NeonCyan, 16.dp, CircleShape),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_plus),
                        contentDescription = if (vm.tab == 0) "New reminder" else "New place",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceNav) {
                tabTitles.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = vm.tab == i,
                        onClick = { vm.tab = i },
                        icon = { Icon(painterResource(tabIcons[i]), contentDescription = null) },
                        label = { Text(title, fontWeight = if (vm.tab == i) FontWeight.Bold else FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = NavIdle,
                            unselectedTextColor = NavIdle,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                tabTitles[vm.tab],
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = InkBright,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            )
            when (vm.tab) {
                0 -> RemindersTab(vm, places, reminders, here, onRequestPermissions)
                1 -> TripTab(vm, places, onRequestPermissions)
                else -> PlacesTab(vm, places, reminders)
            }
        }
    }
}

// ---------------- Shared bits ----------------

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = Muted,
        modifier = modifier,
    )
}

/** Pill / rounded choice used for presets and saved-place chips. */
@Composable
internal fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    hPad: androidx.compose.ui.unit.Dp = 18.dp,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) NeonCyan else SurfaceCard,
        contentColor = if (selected) OnNeon else Ink,
        border = if (selected) null else BorderStroke(1.5.dp, OutlineStrong),
        modifier = modifier.heightIn(min = 44.dp).then(if (selected) Modifier.glow(NeonCyan, 10.dp, shape) else Modifier),
    ) {
        Box(Modifier.padding(horizontal = hPad), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---------------- Reminders ----------------

@Composable
internal fun StatusCard(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.permissions
    val ok = p.allGranted
    val shape = RoundedCornerShape(18.dp)
    val accent = if (ok) NeonCyan else MaterialTheme.colorScheme.error
    val container = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer

    // When everything is fine, show the full card for 5 s, then collapse it to its shield badge and
    // slide that away too. It returns on the next app launch, and always stays while something needs fixing.
    LaunchedEffect(ok, vm.statusCollapsed) {
        if (!ok) {
            vm.statusCollapsed = false
        } else if (!vm.statusCollapsed) {
            delay(5_000)
            vm.statusCollapsed = true
        }
    }
    val collapsed = ok && vm.statusCollapsed
    val fraction by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "statusCard",
    )
    // After the card has shrunk to its badge, the badge slides away too (height + fade): the card is gone.
    val shown by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = if (collapsed) tween(260, delayMillis = 380, easing = FastOutSlowInEasing) else tween(200),
        label = "statusCardShown",
    )
    if (shown <= 0.001f) return

    Row(
        Modifier
            .graphicsLayer { alpha = shown }
            .clipToBounds()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, (p.height * shown).roundToInt()) { p.placeRelative(0, 0) }
            }
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .clip(shape)
            .collapsibleWidth(fraction, 66.dp)
            .background(container)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).glow(accent, 8.dp).background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(22.dp), tint = OnNeon)
        }
        Column(Modifier.weight(1f)) {
            Text(if (ok) "Alerts are on" else "Alerts need permission", fontWeight = FontWeight.SemiBold, color = InkBright)
            Text(
                when {
                    ok -> "Location always · Notifications on"
                    !p.location -> "Allow location access"
                    !p.background -> "Choose “Allow all the time”"
                    else -> "Allow notifications"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA9B8C7),
            )
        }
        OutlinedButton(
            onClick = { if (ok) Notifier.notifyTest(ctx) else onRequestPermissions() },
            border = BorderStroke(1.5.dp, accent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            modifier = Modifier.heightIn(min = 44.dp),
        ) { Text(if (ok) "Test alert" else "Fix", fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * Lays its content out at full width but reports a width that runs from [collapsed] (fraction 0)
 * to the full width (fraction 1). Place a clip/border modifier before this one so the shape follows
 * the animated width; the content is simply cut off from the right as it collapses.
 */
private fun Modifier.collapsibleWidth(fraction: Float, collapsed: Dp): Modifier = layout { measurable, constraints ->
    val full = constraints.maxWidth
    val small = minOf(collapsed.roundToPx(), full)
    val placeable = measurable.measure(constraints.copy(minWidth = full, maxWidth = full))
    val width = (small + (full - small) * fraction.coerceIn(0f, 1f)).roundToInt()
    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
}

@Composable
private fun RemindersTab(
    vm: AppViewModel,
    places: List<Place>,
    reminders: List<Reminder>,
    here: GeoPoint?,
    onRequestPermissions: () -> Unit,
) {
    val byId = places.associateBy { it.id }
    val pending = reminders.filter { !it.done }.sortedByDescending { it.createdAt }
    val done = reminders.filter { it.done }.sortedByDescending { it.createdAt }
    var showDone by remember { mutableStateOf(false) }
    // Holding a reminder card for 1.2 s asks for confirmation here; nothing is deleted until the user agrees.
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }

    if (reminders.isEmpty()) {
        EmptyReminders(vm, onRequestPermissions)
        return
    }

    pendingDelete?.let { r ->
        val where = Store.place(r.placeId)
        DeleteConfirmDialog(
            title = "Delete “${r.title}”?",
            intro = if (where != null) "It won't alert you at" else "This reminder will be removed.",
            chips = where?.let { listOf(DeleteChip(it.name, R.drawable.ic_pin, NeonMagenta)) } ?: emptyList(),
            outro = if (where != null) "any more." else null,
            confirmLabel = "Delete reminder",
            onConfirm = {
                Store.deleteReminder(r.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    fun distanceTo(place: Place?): String? {
        if (here == null || place == null) return null
        return TripService.formatDistance(here.distanceToAsDouble(GeoPoint(place.lat, place.lng)).toInt())
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { StatusCard(vm, onRequestPermissions) }
        item { SectionLabel("Waiting for you · ${pending.size}", Modifier.padding(top = 8.dp)) }
        items(pending, key = { it.id }) { r ->
            ReminderCard(r, byId[r.placeId], distanceTo(byId[r.placeId]), onClick = { vm.editReminder(r.id) }, onDelete = { pendingDelete = r })
        }
        if (done.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { showDone = !showDone },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Done · ${done.size}", fontWeight = FontWeight.SemiBold, color = Muted)
                    Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp), tint = Muted)
                }
            }
            if (showDone) items(done, key = { it.id }) { r -> ReminderCard(r, byId[r.placeId], null, onClick = { vm.editReminder(r.id) }, onDelete = { pendingDelete = r }) }
        }
    }
}

@Composable
private fun ReminderCard(r: Reminder, place: Place?, distance: String?, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Outline),
        // Tap opens the reminder; holding for 1.2 s (red neon halo + colour wash) asks to delete it.
        modifier = Modifier.fillMaxWidth().holdToConfirm(
            onConfirm = onDelete,
            onTap = onClick,
            cornerRadius = 20.dp,
            confirmLabel = "Delete reminder",
        ),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { Store.setDone(r.id, !r.done) },
                contentAlignment = Alignment.Center,
            ) {
                if (r.done) {
                    Box(Modifier.size(24.dp).background(NeonCyan, CircleShape), contentAlignment = Alignment.Center) {
                        Text("✓", color = OnNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.size(24.dp).border(2.dp, CheckGrey, CircleShape))
                }
            }
            Column(Modifier.weight(1f).padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        r.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = InkBright,
                        textDecoration = if (r.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    if (distance != null) {
                        Text(distance, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(14.dp), tint = Muted)
                    Text(
                        "${place?.name ?: "(place removed)"}" + (place?.let { " · ${it.radiusM} m circle" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                if (r.note.isNotBlank()) {
                    Text(
                        r.note, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD5E0EA),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (r.isSnoozed()) {
                    Row(
                        Modifier.padding(top = 3.dp).clip(RoundedCornerShape(14.dp)).background(AmberContainer)
                            .border(1.dp, AmberEdge, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(14.dp), tint = NeonAmber)
                        Text(snoozeLabel(r), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonAmber)
                    }
                }
            }
        }
    }
}

// ---------------- Places ----------------

@Composable
private fun PlacesTab(vm: AppViewModel, places: List<Place>, reminders: List<Reminder>) {
    // Holding a card for 1.2 s asks for confirmation here; nothing is deleted until the user agrees.
    var pendingDelete by remember { mutableStateOf<Place?>(null) }

    pendingDelete?.let { place ->
        // Say exactly which reminders go with the place (first three, then "+N more").
        val doomed = reminders.filter { it.placeId == place.id }
        DeleteConfirmDialog(
            title = "Delete “${place.name}”?",
            intro = when (doomed.size) {
                0 -> "This place will be removed."
                1 -> "Its reminder goes with it:"
                else -> "Its ${doomed.size} reminders go with it:"
            },
            chips = doomed.take(3).map { DeleteChip(it.title, R.drawable.ic_bell) } +
                (if (doomed.size > 3) listOf(DeleteChip("+${doomed.size - 3} more")) else emptyList()),
            confirmLabel = "Delete place",
            onConfirm = {
                Store.deletePlace(place.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (places.isEmpty()) {
            item { Text("No places yet. Tap the + button to pin one.", style = MaterialTheme.typography.bodyLarge, color = Muted) }
        }
        items(places, key = { it.id }) { p ->
            val n = reminders.count { it.placeId == p.id && !it.done }
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, Outline),
                // Tap opens the place; holding for 1.2 s (red neon halo + colour wash) asks to delete it.
                modifier = Modifier.fillMaxWidth().holdToConfirm(
                    onConfirm = { pendingDelete = p },
                    onTap = { vm.openPlaceEditor(p.id) },
                    cornerRadius = 20.dp,
                    confirmLabel = "Delete place",
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(22.dp), tint = NeonCyan)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = InkBright)
                        Text(
                            "Circle ${p.radiusM} m · $n pending reminder${if (n == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium, color = Muted,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = Muted)
                }
            }
        }
    }
}

// ---------------- Trip ----------------

private val alarmPresets = listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km", 3000 to "3 km")

@Composable
private fun TripTab(vm: AppViewModel, places: List<Place>, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val trip by TripService.tripState.collectAsState()
    val place = places.firstOrNull { it.id == vm.tripPlaceId }
    val t = trip

    // "Saved places" sheet, and where you are (roughly) for the "850 m away" lines.
    var showSaved by remember { mutableStateOf(false) }
    var here by remember { mutableStateOf(LastLocation.cached(ctx)) }
    LaunchedEffect(vm.permissions.location) {
        if (vm.permissions.location) lastKnownLocation(ctx) { p -> if (p != null) here = p }
    }
    if (showSaved) {
        TripPlaceSheet(
            places = places,
            here = here,
            onPick = {
                vm.tripPlaceId = it
                showSaved = false
            },
            onDismiss = { showSaved = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (t != null) {
            val accent = if (t.arrived) MaterialTheme.colorScheme.error else NeonCyan
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape)
                    .background(if (t.arrived) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, accent.copy(alpha = 0.5f), shape).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel(if (t.arrived) "Almost there" else "Trip in progress")
                Text(t.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = InkBright)
                Text(
                    if (t.distanceM == null) "Waiting for GPS…"
                    else TripService.formatDistance(t.distanceM) + " away" + (t.etaMin?.let { " · about $it min" } ?: ""),
                    style = MaterialTheme.typography.titleMedium, color = accent,
                )
                Text("Alarm at ${TripService.formatDistance(t.alertM)} from destination", style = MaterialTheme.typography.bodySmall, color = Muted)
                Button(
                    onClick = { TripService.stop(ctx) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = OnNeon),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
                ) { Text(if (t.arrived) "Dismiss alarm" else "Stop trip", fontWeight = FontWeight.Bold) }
            }
        } else {
            Text("Pick where you get off. An alarm rings before you get there.", color = Color(0xFFA9B8C7))

            // Destination
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, Outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(Color(0xFF2A0F25), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(22.dp), tint = NeonMagenta)
                        }
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Destination")
                            Text(
                                place?.name ?: "Not chosen yet",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = if (place == null) Muted else InkBright,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val away = place?.let { pl -> here?.distanceToAsDouble(GeoPoint(pl.lat, pl.lng))?.toInt() }
                            if (away != null) {
                                Text(TripService.formatDistance(away) + " away", style = MaterialTheme.typography.bodyMedium, color = Muted)
                            }
                        }
                    }
                    // One card, two ways to choose: from your saved places, or by pinning on the map.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (places.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showSaved = true },
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, NeonCyan),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = NeonCyan,
                                ),
                                // Tight side padding so "Saved places" fits on one line next to its icon.
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_bookmark), null, Modifier.size(18.dp))
                                Text("Saved places", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.openPlaceEditor(null, forTrip = true) },
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, NeonCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_map), null, Modifier.size(18.dp))
                            Text(
                                if (places.isEmpty()) "Choose on the map" else "On the map",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Ring the alarm", Modifier.weight(1f))
                    Text(
                        TripService.formatDistance(vm.tripAlertM) + " before arriving",
                        fontWeight = FontWeight.Bold, color = NeonCyan,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    alarmPresets.forEach { (m, label) ->
                        Choice(label, vm.tripAlertM == m, onClick = { vm.tripAlertM = m }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), hPad = 4.dp)
                    }
                }
            }

        }
    }

    // Pinned to the bottom (just above the navigation bar), outside the scrolling area.
    if (t == null) {
        val shape = RoundedCornerShape(28.dp)
        Button(
            enabled = place != null,
            onClick = {
                val pl = place ?: return@Button
                if (!vm.permissions.location) onRequestPermissions()
                else TripService.start(ctx, pl.name, pl.lat, pl.lng, vm.tripAlertM)
            },
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OnNeon),
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)
                .fillMaxWidth()
                .height(56.dp)
                .then(if (place != null) Modifier.glow(NeonCyan, 16.dp, shape) else Modifier),
        ) {
            Icon(painterResource(R.drawable.ic_navigate), null, Modifier.size(22.dp))
            Text(if (place == null) "  Pick a destination" else "  Start trip", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
    }
}
