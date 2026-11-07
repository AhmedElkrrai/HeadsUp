package com.elkrrai.headsup.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.elkrrai.headsup.ui.theme.NeonMagenta
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.data.Store
import com.elkrrai.headsup.ui.theme.Bg
import com.elkrrai.headsup.ui.theme.Ink
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.SurfaceCard
import com.elkrrai.headsup.ui.theme.glow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

private val SheetColor = Color(0xFF111821)

/** [base], or "[base] 2", "[base] 3"… the first one no other place already uses. */
private fun uniqueName(base: String, ownId: String): String {
    val taken = Store.places.value.filter { it.id != ownId }.map { it.name }.toSet()
    if (base !in taken) return base
    var n = 2
    while ("$base $n" in taken) n++
    return "$base $n"
}

/**
 * Full-screen map. A round back button and a search pill float on top; a sheet with the
 * name, radius and Save sits at the bottom. [forTrip]: picking a trip destination, so the
 * reminder-circle radius is hidden.
 */
@Composable
fun PlaceEditorScreen(vm: AppViewModel, placeId: String?, forTrip: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(placeId) { placeId?.let { Store.place(it) } }

    var name by remember { mutableStateOf(existing?.name ?: vm.placeNameSuggestion.orEmpty()) }
    var pin by remember { mutableStateOf(existing?.let { GeoPoint(it.lat, it.lng) }) }
    var radius by remember { mutableIntStateOf(existing?.radiusM ?: 150) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Where the map opens. Known instantly from the saved pin or the cached last position,
    // so there is no world-map flash. Only if neither exists do we wait briefly for GPS.
    var start by remember { mutableStateOf(pin ?: LastLocation.cached(ctx)) }
    var ready by remember { mutableStateOf(start != null) }
    var focus by remember { mutableStateOf<GeoPoint?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }
    var focusZoom by remember { mutableDoubleStateOf(16.0) }
    var me by remember { mutableStateOf<GeoPoint?>(null) }
    var userTouchedMap by remember { mutableStateOf(existing != null) }
    var nameFocused by remember { mutableStateOf(false) }

    // Zen mode: only the zen and locate buttons stay on screen, so you can roam the map.
    var zen by remember { mutableStateOf(false) }
    val sheetShown by animateFloatAsState(if (zen) 0f else 1f, tween(300, easing = FastOutSlowInEasing), label = "sheetShown")
    val focusManager = LocalFocusManager.current

    // The "tap the map" hint is shown for 3 s when the screen opens, then hidden. After 10 s with
    // no interaction at all it reappears for 8 s (once per idle period). Any touch or typing hides it.
    var hintVisible by remember { mutableStateOf(true) }
    val lastActivity = remember { longArrayOf(SystemClock.elapsedRealtime()) }
    fun bump() {
        lastActivity[0] = SystemClock.elapsedRealtime()
        if (hintVisible) hintVisible = false
    }
    LaunchedEffect(Unit) {
        delay(3_000)
        hintVisible = false
        var shownFor = -1L
        while (true) {
            delay(500)
            val last = lastActivity[0]
            if (last != shownFor && SystemClock.elapsedRealtime() - last >= 10_000) {
                shownFor = last
                hintVisible = true
                delay(8_000) // the reminder stays up longer than the first hint (3 s)
                hintVisible = false
            }
        }
    }

    fun focusOn(p: GeoPoint, zoom: Double = 16.0) {
        focusZoom = zoom
        focus = p
        focusKey++
    }

    fun doSearch() {
        if (query.isBlank() || searching) return
        searching = true
        message = null
        scope.launch {
            hits = searchPlaces(query)
            searching = false
            if (hits.isEmpty()) message = "No results (or no internet)."
        }
    }

    /** Round locate button: zoom right in on where you are. Doesn't move the pin. */
    fun toggleZen() {
        zen = !zen
        if (zen) {
            focusManager.clearFocus() // close the keyboard
            hits = emptyList()
            message = null
        }
    }

    fun zoomToMe() {
        if (!Permissions.hasLocation(ctx)) {
            message = "Allow location access first (use the status card on the main screen)."
            return
        }
        userTouchedMap = true
        var gotAny = false
        lastKnownLocation(ctx) { p ->
            if (p != null) {
                gotAny = true
                me = p
                focusOn(p, 19.0)
            }
        }
        currentLocation(ctx) { loc ->
            if (loc != null) {
                val p = GeoPoint(loc.first, loc.second)
                me = p
                focusOn(p, 19.0)
                message = null
            } else if (!gotAny) {
                message = "Couldn't get a GPS fix. Try again outdoors."
            }
        }
    }

    /** Drops the destination pin on your position: instantly from the last known fix, then refined. */
    fun pinMyLocation() {
        if (!Permissions.hasLocation(ctx)) {
            message = "Allow location access first (use the status card on the main screen)."
            return
        }
        userTouchedMap = true
        var placedQuickly = false
        lastKnownLocation(ctx) { p ->
            if (p != null) {
                placedQuickly = true
                pin = p
                focusOn(p)
                message = "Using your last known position, refining with GPS…"
            } else {
                message = "Finding you…"
            }
        }
        currentLocation(ctx) { loc ->
            if (loc != null) {
                val p = GeoPoint(loc.first, loc.second)
                pin = p
                focusOn(p)
                message = null
            } else {
                message = if (placedQuickly) null else "Couldn't get a GPS fix. Try again outdoors, or tap the map."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (existing == null && Permissions.hasLocation(ctx)) {
            lastKnownLocation(ctx) { p ->
                if (p != null && start == null) {
                    start = p
                    ready = true
                }
            }
            currentLocation(ctx) { loc ->
                if (loc == null) return@currentLocation
                val p = GeoPoint(loc.first, loc.second)
                if (start == null) {
                    start = p
                    ready = true
                } else if (!userTouchedMap && start!!.distanceToAsDouble(p) > 300) {
                    focusOn(p) // cached position was stale: jump once, silently
                }
            }
        }
        delay(2500)
        ready = true // no location available: show the default map anyway
    }

    // A new place (or trip destination) starts pinned on where you are; tap the map to move it.
    // Silent without location permission.
    LaunchedEffect(Unit) {
        if (existing == null && Permissions.hasLocation(ctx)) pinMyLocation()
    }

    // Back leaves zen mode first, then the screen.
    BackHandler { if (zen) zen = false else vm.closePlaceEditor() }

    Box(
        Modifier.fillMaxSize().background(Bg).pointerInput(Unit) {
            // Watch every touch (without consuming it) to know when the user is idle.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    bump()
                }
            }
        },
    ) {
        if (ready) {
            MapPicker(
                pin = pin, radiusM = if (forTrip) vm.tripAlertM else radius, focus = focus ?: start, focusKey = focusKey,
                focusZoom = focusZoom, me = me,
                onPick = {
                    userTouchedMap = true
                    pin = it
                },
                onTouch = ::bump,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeonCyan)
                Text("Finding your location…", color = Muted, modifier = Modifier.padding(top = 12.dp))
            }
        }

        // ---- top: back button + search pill ----
        AnimatedVisibility(visible = !zen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(48.dp).glow(Color.Black, 6.dp).clip(CircleShape).background(SurfaceCard)
                        .border(1.dp, OutlineStrong, CircleShape).clickable { vm.closePlaceEditor() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Ink, modifier = Modifier.size(22.dp))
                }
                Row(
                    Modifier.weight(1f).height(48.dp).glow(Color.Black, 6.dp, CircleShape).clip(CircleShape).background(SurfaceCard)
                        .border(1.dp, OutlineStrong, CircleShape).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            bump()
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(NeonCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("Search address or place", color = Color(0xFF8FA0B2), fontSize = 16.sp)
                                inner()
                            }
                        },
                    )
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                    } else if (query.isNotBlank()) {
                        // Visible search button (the keyboard's search key works too).
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(NeonCyan).clickable { doSearch() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Search", tint = OnNeon, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (hits.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceCard)
                        .border(1.dp, OutlineStrong, RoundedCornerShape(18.dp)),
                ) {
                    hits.take(4).forEach { hit ->
                        Text(
                            hit.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                val p = GeoPoint(hit.lat, hit.lng)
                                userTouchedMap = true
                                pin = p
                                focusOn(p)
                                if (name.isBlank()) name = hit.label.substringBefore(",")
                                hits = emptyList()
                                focusManager.clearFocus() // close the keyboard
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            message?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = Ink,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(SurfaceCard)
                        .border(1.dp, OutlineStrong, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        }

        // ---- bottom: locate button + sheet ----
        // Only lift the sheet above the keyboard while typing the name; when searching, the
        // search pill is at the top, so the keyboard can simply cover the sheet.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if (nameFocused) Modifier.imePadding() else Modifier),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    // With the sheet gone (zen), keep the buttons clear of the gesture bar.
                    .then(if (zen) Modifier.navigationBarsPadding() else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                // Hint chip: fades in/out (see hintVisible). The Box keeps the locate button at the end.
                Box(Modifier.weight(1f).padding(end = 12.dp), contentAlignment = Alignment.CenterStart) {
                    // Achievement-style pop-up: the badge appears, the pill grows to show the text, and
                    // when it's time to go the text collapses back into the badge, which then shrinks away.
                    val textFraction by animateFloatAsState(
                        targetValue = if (hintVisible) 1f else 0f,
                        animationSpec = if (hintVisible) tween(420, delayMillis = 160, easing = FastOutSlowInEasing) else tween(320, easing = FastOutSlowInEasing),
                        label = "hintText",
                    )
                    val badge by animateFloatAsState(
                        targetValue = if (hintVisible || textFraction > 0.02f) 1f else 0f,
                        animationSpec = tween(200),
                        label = "hintBadge",
                    )
                    if (badge > 0.01f && !zen) {
                        Row(
                            Modifier.graphicsLayer {
                                alpha = badge
                                scaleX = 0.5f + 0.5f * badge
                                scaleY = 0.5f + 0.5f * badge
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }.clip(CircleShape).background(SurfaceCard.copy(alpha = 0.94f))
                                .border(1.dp, OutlineStrong, CircleShape).padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).background(Color(0xFF2A0F25), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(20.dp), tint = NeonMagenta)
                            }
                            Text(
                                if (pin == null) "Tap the map to drop a pin" else "Tap elsewhere to move the pin",
                                style = MaterialTheme.typography.labelLarge,
                                color = Ink,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.horizontalReveal(textFraction).padding(start = 10.dp, end = 14.dp),
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Zen mode: hides everything except this button and the locate button below it, so you can roam the map.
                    Box(
                        Modifier.size(52.dp).then(if (zen) Modifier.glow(NeonCyan, 14.dp) else Modifier)
                            .background(if (zen) NeonCyan else SurfaceCard, CircleShape)
                            .border(1.5.dp, NeonCyan, CircleShape).clip(CircleShape).clickable { toggleZen() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (zen) R.drawable.ic_eye else R.drawable.ic_eye_off),
                            contentDescription = if (zen) "Show controls" else "Zen mode: hide controls",
                            tint = if (zen) OnNeon else NeonCyan,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Box(
                        Modifier.size(52.dp).glow(NeonCyan, 14.dp).background(SurfaceCard, CircleShape)
                            .border(1.5.dp, NeonCyan, CircleShape).clip(CircleShape).clickable { zoomToMe() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_locate), contentDescription = "Zoom to my location", tint = NeonCyan, modifier = Modifier.size(26.dp))
                    }
                }
            }

            val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.collapseVertically(sheetShown).fillMaxWidth().clip(sheetShape).background(SheetColor).border(1.dp, OutlineStrong.copy(alpha = 0.7f), sheetShape)
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(Color(0xFF3A4A5E), RoundedCornerShape(2.dp)))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Name")
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            bump()
                        },
                        singleLine = true,
                        placeholder = { Text(if (forTrip) "Optional" else "Home, Office, Bus stop…", color = Color(0xFF8FA0B2)) },
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = InkBright, fontWeight = FontWeight.Medium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = OutlineStrong,
                            focusedContainerColor = Bg,
                            unfocusedContainerColor = Bg,
                            cursorColor = NeonCyan,
                        ),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { nameFocused = it.isFocused },
                    )
                }

                if (!forTrip) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            SectionLabel("Remind me within", Modifier.weight(1f))
                            Text("$radius m", fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        NeonSlider(
                            value = radius.toFloat(),
                            onValueChange = { radius = ((it / 10f).toInt() * 10).coerceIn(50, 500) },
                            range = 50f..500f,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = pin != null,
                        onClick = {
                            val p = pin ?: return@Button
                            val id = existing?.id ?: Store.newId()
                            // Unnamed places get "Pinned place", then "Pinned place 2", 3… so they can be told apart.
                            val fallback = uniqueName(if (forTrip) "Destination" else "Pinned place", id)
                            Store.upsertPlace(Place(id, name.trim().ifBlank { fallback }, p.latitude, p.longitude, radius))
                            // Only a newly created place becomes the selected one; editing another place shouldn't switch to it.
                            vm.closePlaceEditor(if (existing == null) id else null)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OnNeon),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).then(if (pin != null) Modifier.glow(NeonCyan, 14.dp) else Modifier),
                    ) { Text(if (forTrip) "Use destination" else "Save place", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Shrinks this element's height towards zero as [fraction] goes from 1 to 0, clipping what no longer fits. */
private fun Modifier.collapseVertically(fraction: Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * fraction.coerceIn(0f, 1f)).roundToInt()) { placeable.placeRelative(0, 0) }
    }

/** Shows only [fraction] of this element's natural width (0 = fully collapsed), clipping the rest. */
private fun Modifier.horizontalReveal(fraction: Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = (placeable.width * fraction.coerceIn(0f, 1f)).roundToInt()
        layout(width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** Thin neon slider that matches the design (the stock Material one is too chunky). */
@Composable
private fun NeonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val active = if (enabled) NeonCyan else Color(0xFF3A4A5E)

    fun valueAt(x: Float) = range.start + (x / widthPx).coerceIn(0f, 1f) * span

    Box(
        modifier.fillMaxWidth().height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onValueChange(valueAt(it.x)) }
            }
            .pointerInput(enabled) {
                if (enabled) detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange(valueAt(change.position.x))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val y = size.height / 2
            val x = inset + (size.width - 2 * inset) * fraction
            drawLine(OutlineStrong, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(active, Offset(inset, y), Offset(x, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            if (enabled) drawCircle(NeonCyan.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = Offset(x, y))
            drawCircle(active, radius = 10.dp.toPx(), center = Offset(x, y))
        }
    }
}
