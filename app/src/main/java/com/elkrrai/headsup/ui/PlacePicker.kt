package com.elkrrai.headsup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.Permissions
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.trip.TripService
import com.elkrrai.headsup.ui.theme.Ink
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.SurfaceCard
import com.elkrrai.headsup.ui.theme.glow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * "Change" on the Where card: a bottom sheet of place cards. Tap a card to use that place (one tap, the sheet
 * closes). The pencil edits that place; the dashed button pins a new one. Each card shows its circle size and
 * how far away it is, so places with similar names can still be told apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacePickerSheet(
    places: List<Place>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide the sheet away first, then act.
    val closeThen: (() -> Unit) -> Unit = { after -> scope.launch { sheetState.hide() }.invokeOnCompletion { after() } }

    // Where you are (roughly), for the "850 m away" on each card.
    var here by remember { mutableStateOf(LastLocation.cached(ctx)) }
    LaunchedEffect(Unit) {
        if (Permissions.hasLocation(ctx)) lastKnownLocation(ctx) { p -> if (p != null) here = p }
    }

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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Where should it alert you?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = InkBright)
                Text(
                    "Tap a place to use it. Tap the pencil to change a place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA9B8C7),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                places.forEach { p ->
                    val away = here?.distanceToAsDouble(GeoPoint(p.lat, p.lng))?.toInt()?.let { TripService.formatDistance(it) }
                    PlaceChoiceCard(
                        name = p.name,
                        detail = "${p.reminderRadiusM} m circle" + (away?.let { " · $it away" } ?: ""),
                        selected = p.id == selectedId,
                        onSelect = { closeThen { onPick(p.id) } },
                        onEdit = { closeThen { onEdit(p.id) } },
                    )
                }
            }

            // Pin a new place: dashed outline so it reads as "add", not as one more place.
            val addShape = RoundedCornerShape(20.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(addShape).clickable { closeThen(onNew) }
                    .drawBehind {
                        drawRoundRect(
                            color = NeonCyan.copy(alpha = 0.55f),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(20.dp), tint = NeonCyan)
                Text("Pin a new place on the map", fontWeight = FontWeight.Bold, color = NeonCyan)
            }

            Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_info), null, Modifier.size(18.dp).padding(top = 1.dp), tint = Muted)
                Text(
                    "Editing a place with the pencil changes it for every reminder that uses it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
    }
}

/** One place: round check on the left, name + detail, pencil on the right. Neon outline when it's the chosen one. */
@Composable
private fun PlaceChoiceCard(name: String, detail: String, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .then(if (selected) Modifier.glow(NeonCyan, 12.dp, shape) else Modifier)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else SurfaceCard)
            .border(if (selected) 2.dp else 1.dp, if (selected) NeonCyan else Outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 68.dp).clickable(onClick = onSelect).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selected) {
                Box(Modifier.size(24.dp).background(NeonCyan, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", color = OnNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(Modifier.size(24.dp).border(2.dp, Color(0xFF5F7084), CircleShape))
            }
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = InkBright,
                )
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (selected) Color(0xFFA9B8C7) else Muted)
            }
        }
        Box(
            Modifier.padding(horizontal = 8.dp).size(44.dp).clip(CircleShape).background(Color(0x0FFFFFFF)).clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_edit),
                contentDescription = "Edit $name. Changes every reminder that uses it.",
                modifier = Modifier.size(18.dp),
                tint = Color(0xFFC5D1DC),
            )
        }
    }
}
