package com.elkrrai.headsup.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Reminder
import com.elkrrai.headsup.data.Store
import com.elkrrai.headsup.ui.theme.AmberContainer
import com.elkrrai.headsup.ui.theme.AmberEdge
import com.elkrrai.headsup.ui.theme.Bg
import com.elkrrai.headsup.ui.theme.Ink
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonAmber
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.SurfaceCard
import com.elkrrai.headsup.ui.theme.glow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.DateFormat
import java.util.Date

private val CardRadius = RoundedCornerShape(22.dp)
private val Placeholder = Color(0xFF8FA0B2)

/**
 * New / edit reminder. The title is the hero field, the place is a card, the note is optional,
 * snooze lives in its own card (edit only) and Save is pinned to the bottom.
 */
@Composable
fun ReminderEditorScreen(vm: AppViewModel) {
    val draft = vm.draft ?: return
    val ctx = LocalContext.current
    val places by Store.places.collectAsState()
    val reminders by Store.reminders.collectAsState()
    val existing = draft.id?.let { id -> reminders.firstOrNull { it.id == id } }
    val place = places.firstOrNull { it.id == draft.placeId }

    var pickPlace by remember { mutableStateOf(false) }
    var showWhenInfo by remember { mutableStateOf(false) }
    var titleFocused by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // A brand-new, empty reminder opens with the keyboard up on the title.
    LaunchedEffect(Unit) {
        if (draft.id == null && draft.title.isBlank()) titleFocus.requestFocus()
    }

    /**
     * "Next visit" means: skip this visit, remind me next time. That only makes sense while you are at the
     * place. If you're away, the next arrival IS the next visit, so there is nothing to snooze (snoozing would
     * wrongly skip that arrival too). If we can't tell where you are, snooze as before.
     */
    fun snoozeNextVisit(reminderId: String, placeId: String) {
        val target = Store.place(placeId)
        fun apply(here: GeoPoint?) {
            val away = target != null && here != null &&
                here.distanceToAsDouble(GeoPoint(target.lat, target.lng)) > target.radiusM + 25
            if (away) {
                Toast.makeText(
                    ctx,
                    "You're not at ${target?.name} right now, so there's nothing to snooze. You'll be reminded when you arrive.",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Store.snoozeUntilNextVisit(reminderId)
            }
        }
        if (!Permissions.hasLocation(ctx)) {
            apply(null)
        } else {
            currentLocation(ctx) { loc ->
                if (loc != null) apply(GeoPoint(loc.first, loc.second)) else lastKnownLocation(ctx) { apply(it) }
            }
        }
    }

    BackHandler { vm.closeEditor() }

    if (pickPlace) {
        PlacePickerSheet(
            places = places,
            selectedId = draft.placeId,
            onPick = {
                vm.draft = draft.copy(placeId = it)
                pickPlace = false
            },
            onEdit = {
                pickPlace = false
                vm.openPlaceEditor(it)
            },
            onNew = {
                pickPlace = false
                vm.openPlaceEditor(null)
            },
            onDismiss = { pickPlace = false },
        )
    }

    if (showWhenInfo) WhenInfoSheet(onDismiss = { showWhenInfo = false })

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().imePadding()) {
        // ---- top bar ----
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(SurfaceCard).border(1.dp, OutlineStrong, CircleShape)
                    .clickable { vm.closeEditor() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Ink, modifier = Modifier.size(22.dp))
            }
            Text(
                if (existing == null) "New reminder" else "Edit reminder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkBright,
            )
        }

        // ---- content ----
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // What
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("What do you need to do?")
                // Modern input: rounded filled field, leading icon, neon ring + glow while focused, clear button.
                // The typed text is 20 sp; the placeholder is deliberately smaller and lighter (17 sp, regular).
                val fieldShape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (titleFocused) Modifier.glow(NeonCyan, 14.dp, fieldShape) else Modifier)
                        .clip(fieldShape)
                        .background(SurfaceCard)
                        .border(1.5.dp, if (titleFocused) NeonCyan else OutlineStrong, fieldShape)
                        .heightIn(min = 64.dp)
                        .padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp), tint = NeonCyan)
                    }
                    BasicTextField(
                        value = draft.title,
                        onValueChange = { vm.draft = draft.copy(title = it) },
                        singleLine = true,
                        textStyle = TextStyle(color = InkBright, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(NeonCyan),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f)
                            .focusRequester(titleFocus)
                            .onFocusChanged { titleFocused = it.isFocused },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.title.isEmpty()) {
                                    Text("Buy groceries", color = Placeholder, fontSize = 17.sp, fontWeight = FontWeight.Normal)
                                }
                                inner()
                            }
                        },
                    )
                    if (draft.title.isNotEmpty()) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable { vm.draft = draft.copy(title = "") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_close), "Clear", Modifier.size(18.dp), tint = Muted)
                        }
                    }
                }
                if (existing == null && draft.title.isBlank()) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("Groceries", false, onClick = { vm.draft = draft.copy(title = "Groceries") }, hPad = 16.dp)
                        Choice("Pick up prescription", false, onClick = { vm.draft = draft.copy(title = "Pick up prescription") }, hPad = 16.dp)
                    }
                }
            }

            // Snooze (edit only)
            if (existing != null && !existing.done) SnoozeCard(
                existing,
                onNextVisit = { snoozeNextVisit(existing.id, existing.placeId) },
            )

            // Where
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Where")
                Column(Modifier.fillMaxWidth().clip(CardRadius).background(SurfaceCard).border(1.dp, Outline, CardRadius)) {
                    PlaceArt()
                    Row(
                        Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).background(Color(0xFF2A0F25), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(22.dp), tint = NeonMagenta)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                place?.name ?: "No place chosen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (place == null) Muted else InkBright,
                            )
                            Text(
                                place?.let { "${it.radiusM} m circle" } ?: "Pin one on the map",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                            )
                        }
                        OutlinedButton(
                            // With nothing saved yet, a list would only hold "pin new": go straight to the map.
                            onClick = { if (places.isEmpty()) vm.openPlaceEditor(null) else pickPlace = true },
                            border = BorderStroke(1.5.dp, NeonCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) { Text(if (places.isEmpty()) "Pin on map" else "Change", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            // When (new reminders only)
            if (existing == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("When")
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable { showWhenInfo = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_info), "What do these options mean?", Modifier.size(20.dp), tint = NeonCyan)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhenOption("Next visit", R.drawable.ic_repeat, draft.whenNextVisit, { vm.draft = draft.copy(whenNextVisit = true) }, Modifier.weight(1f))
                        WhenOption("Once in place", R.drawable.ic_pin, !draft.whenNextVisit, { vm.draft = draft.copy(whenNextVisit = false) }, Modifier.weight(1f))
                    }
                }
            }

            // Note
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Note · optional")
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { vm.draft = draft.copy(note = it) },
                    placeholder = { Text("Add details, or paste a list", color = Placeholder) },
                    minLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = OutlineStrong,
                        focusedContainerColor = Bg,
                        unfocusedContainerColor = Bg,
                        cursorColor = NeonCyan,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- pinned save ----
        val canSave = draft.placeId != null && draft.title.isNotBlank()
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp).navigationBarsPadding()) {
            Button(
                enabled = canSave,
                onClick = { vm.saveDraft() },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = OnNeon,
                    disabledContainerColor = Color(0xFF1B2430),
                    disabledContentColor = Color(0xFF7F90A2),
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (canSave) Modifier.glow(NeonCyan, 16.dp) else Modifier),
            ) {
                Text(
                    when {
                        draft.title.isBlank() -> "Enter what to do to save"
                        draft.placeId == null -> "Pick a place to save"
                        existing != null -> "Save changes"
                        else -> "Save"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** One of the two "When" choices: icon + label, neon ring and glow when selected. */
@Composable
private fun WhenOption(label: String, icon: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else SurfaceCard,
        contentColor = if (selected) NeonCyan else Ink,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) NeonCyan else OutlineStrong),
        modifier = modifier.heightIn(min = 52.dp).then(if (selected) Modifier.glow(NeonCyan, 12.dp, shape) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (selected) NeonCyan else Muted)
            Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** The (i) next to "When": what each option does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenInfoSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111821),
        contentColor = Ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFF3A4A5E), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("When should it alert you?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = InkBright)
            WhenInfoRow(
                icon = R.drawable.ic_repeat, tint = NeonAmber, tileColor = AmberContainer,
                title = "Next visit",
                body = "If you're at the place right now, this skips this visit and reminds you the next time you arrive, after you've left and come back. " +
                    "If you're away, it just alerts you when you arrive. Handy when the shop is right next to home.",
            )
            WhenInfoRow(
                icon = R.drawable.ic_pin, tint = NeonCyan, tileColor = MaterialTheme.colorScheme.primaryContainer,
                title = "Once in place",
                body = "Alerts you as soon as you arrive at the place. Best for most reminders.",
                tag = "Default",
            )
            Button(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OnNeon),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).glow(NeonCyan, 14.dp),
            ) { Text("Got it", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun WhenInfoRow(icon: Int, tint: Color, tileColor: Color, title: String, body: String, tag: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).background(tileColor, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = InkBright)
                if (tag != null) {
                    Text(
                        tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9C6D3))
        }
    }
}

/** Abstract header for the Where card: a grid, the reminder circle and the pin. Not a real map. */
@Composable
private fun PlaceArt() {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF0E141C))
            val step = 24.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0xFF1B2634), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0xFF1B2634), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += step
            }
            drawCircle(NeonCyan.copy(alpha = 0.16f), radius = 34.dp.toPx(), center = center)
            drawCircle(NeonCyan, radius = 34.dp.toPx(), center = center, style = Stroke(2.2.dp.toPx()))
        }
        Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(30.dp), tint = NeonMagenta)
    }
}

/** Snooze status and one-tap snooze choices. Amber outline while the reminder is snoozed. */
@Composable
private fun SnoozeCard(r: Reminder, onNextVisit: () -> Unit) {
    val snoozed = r.isSnoozed()
    val shape = CardRadius
    Column(
        Modifier.fillMaxWidth().clip(shape).background(SurfaceCard)
            .border(1.dp, if (snoozed) AmberEdge else Outline, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snoozed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(AmberContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(20.dp), tint = NeonAmber)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (r.waitForReentry) "Snoozed until your next visit"
                        else "Snoozed until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.snoozedUntil)),
                        fontWeight = FontWeight.SemiBold,
                        color = NeonAmber,
                    )
                    Text(
                        if (r.waitForReentry) "Silent until you leave and come back" else "Alerts are paused until then",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA9B8C7),
                    )
                }
                OutlinedButton(
                    onClick = { Store.clearSnooze(r.id) },
                    border = BorderStroke(1.5.dp, NeonAmber),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) { Text("Wake now", fontWeight = FontWeight.SemiBold) }
            }
        } else {
            SectionLabel("Snooze")
        }
        SnoozeChip("Next visit", r.waitForReentry, onNextVisit, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SnoozeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) AmberContainer else SurfaceCard,
        contentColor = if (selected) NeonAmber else Ink,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) NeonAmber else OutlineStrong),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
        }
    }
}
