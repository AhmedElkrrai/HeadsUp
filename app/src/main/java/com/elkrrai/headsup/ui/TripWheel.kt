package com.elkrrai.headsup.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.trip.TripService
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonCyanSoft
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.glow
import org.osmdroid.util.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Orbit = 150.dp
private val NodeSize = 56.dp
private val ChosenCircle = 208.dp
private const val MaxNodes = 6

/**
 * The Trip tab before a trip starts. Nothing chosen: a "wheel" of your closest saved places around a centre
 * circle (tap the centre for the full list, the dashed "New" node to pin a new destination). Chosen: the
 * destination in a circle with its alarm distance on the rim (tap the tag to change that distance).
 */
@Composable
internal fun TripSetup(
    place: Place?,
    places: List<Place>,
    here: GeoPoint?,
    onPick: (String) -> Unit,
    onOpenList: () -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onEditAlarm: () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (place == null) Wheel(places, here, onPick, onOpenList, onNew)
            else ChosenDestination(place, onBack, onEditAlarm)
        }

        val away = place?.let { pl -> here?.distanceToAsDouble(GeoPoint(pl.lat, pl.lng))?.toInt() }
        if (away != null) AwayOval(away, Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 14.dp))

        val shape = RoundedCornerShape(28.dp)
        Button(
            enabled = place != null,
            onClick = onStart,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan, contentColor = OnNeon,
                disabledContainerColor = Color(0xFF1B2430), disabledContentColor = Color(0xFF7F90A2),
            ),
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)
                .fillMaxWidth()
                .height(56.dp)
                .then(if (place != null) Modifier.glow(NeonCyan, 16.dp, shape) else Modifier),
        ) {
            Icon(painterResource(R.drawable.ic_navigate), null, Modifier.size(22.dp))
            Text("  Start trip", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- wheel

@Composable
private fun Wheel(places: List<Place>, here: GeoPoint?, onPick: (String) -> Unit, onOpenList: () -> Unit, onNew: () -> Unit) {
    // The closest few go on the wheel; the rest are in the list behind the centre.
    val shown = (if (here == null) places else places.sortedBy { here.distanceToAsDouble(GeoPoint(it.lat, it.lng)) }).take(MaxNodes)
    val slots = shown.size + 1 // + the "New" node
    val orbitColor = Color(0xFF1E2A38)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                orbitColor, radius = Orbit.toPx(), center = center,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
            )
        }

        for (i in 0 until slots) {
            // Clockwise from the top.
            val angle = -PI / 2 + i * 2 * PI / slots
            val dx = (Orbit.value * cos(angle)).toFloat().dp
            val dy = (Orbit.value * sin(angle)).toFloat().dp
            val node = Modifier.offset(x = dx, y = dy)
            if (i < shown.size) {
                PlaceNode(shown[i].name, node) { onPick(shown[i].id) }
            } else {
                NewNode(node, onNew)
            }
        }

        CenterPin(places.size, onOpenList)
    }
}

@Composable
private fun PlaceNode(name: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(NodeSize).background(Color(0xFF2A0F25), CircleShape).border(1.5.dp, Color(0xFF5A1F4D), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(24.dp), tint = NeonMagenta)
        }
        Text(
            name, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDCE6EE),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun NewNode(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(NodeSize).dashedCircle(NeonCyan, 2.dp).background(NeonCyan.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(26.dp), tint = NeonCyan)
        }
        Text("New", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
    }
}

/**
 * The centre of the wheel: a solid, glowing neon button with a pin that bobs over a pulsing ring, a slow ripple
 * that invites a tap, and a badge on its rim showing how many places the list holds. Tap for the full list.
 */
@Composable
private fun CenterPin(placeCount: Int, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "centerPin")
    val bob = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val pulse = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "pulse",
    )
    val circle = 168.dp
    Box(Modifier.size(264.dp), contentAlignment = Alignment.Center) {
        // The ripple: a ring that keeps expanding from the button's edge and fading out.
        Canvas(Modifier.fillMaxSize()) {
            val p = pulse.value
            drawCircle(
                color = NeonCyan.copy(alpha = 0.55f * (1f - p)),
                radius = (circle / 2).toPx() * (1f + 0.55f * p),
                center = center,
                style = Stroke(2.dp.toPx()),
            )
        }

        Box(
            Modifier.size(circle)
                .glow(NeonCyan, 26.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF0F3A3A).copy(alpha = 0.9f), Color(0xFF0F161F)), radius = 260f), CircleShape)
                .border(2.dp, NeonCyan, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(86.dp, 96.dp)) {
                // The pulse ring "on the ground", under the pin's tip.
                val p = pulse.value
                val k = 0.45f + 1.05f * p
                drawOval(
                    color = NeonCyan.copy(alpha = 0.8f * (1f - p)),
                    topLeft = Offset(size.width / 2 - 27.dp.toPx() * k, size.height - 4.dp.toPx() - 10.dp.toPx() * k),
                    size = androidx.compose.ui.geometry.Size(54.dp.toPx() * k, 20.dp.toPx() * k),
                    style = Stroke(2.dp.toPx()),
                )
            }
            Image(
                painterResource(R.drawable.ic_map_pin), contentDescription = "Choose destination",
                modifier = Modifier.size(64.dp).offset(y = (-6).dp).graphicsLayer { translationY = -11.dp.toPx() * bob.value },
            )
        }

        // "All places" badge on the bottom rim: list icon + how many.
        val badge = RoundedCornerShape(18.dp)
        Row(
            Modifier.offset(y = circle / 2)
                .glow(NeonCyan, 10.dp, badge).background(Color(0xFF0B0F14), badge).border(2.dp, NeonCyan, badge)
                .clip(badge).clickable(onClick = onClick)
                .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(painterResource(R.drawable.ic_list), null, Modifier.size(16.dp), tint = NeonCyan)
            Text("$placeCount", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = InkBright, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- chosen

@Composable
private fun ChosenDestination(place: Place, onChange: () -> Unit, onEditAlarm: () -> Unit) {
    // Tapping the circle (or the swap badge on its top rim) goes back to the wheel to pick another place.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Faint rings, waiting for the trip to start and make them radiate.
        Canvas(Modifier.fillMaxSize()) {
            val r = (ChosenCircle / 2).toPx()
            drawCircle(
                Brush.radialGradient(listOf(NeonCyan.copy(alpha = 0.18f), Color.Transparent), center = center, radius = 200.dp.toPx()),
                radius = 200.dp.toPx(), center = center,
            )
            drawCircle(NeonCyan.copy(alpha = 0.10f), radius = r * 1.35f, center = center, style = Stroke(2.dp.toPx()))
            drawCircle(NeonCyan.copy(alpha = 0.10f), radius = r * 1.7f, center = center, style = Stroke(2.dp.toPx()))
        }

        Box(
            Modifier.size(ChosenCircle)
                .glow(NeonCyan, 24.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF0F3A3A).copy(alpha = 0.9f), Color(0xFF0F161F)), radius = 300f), CircleShape)
                .border(2.dp, NeonCyan, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onChange)
                .padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(26.dp), tint = NeonMagenta)
                SectionLabel("Destination")
                Text(
                    place.name,
                    fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = InkBright,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val swap = CircleShape
        Box(
            Modifier.offset(y = -ChosenCircle / 2)
                .size(38.dp).glow(NeonCyan, 10.dp, swap).background(Color(0xFF0B0F14), swap).border(2.dp, NeonCyan, swap)
                .clip(swap).clickable(onClick = onChange),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_repeat), "Choose another destination", Modifier.size(18.dp), tint = NeonCyan)
        }

        // Alarm distance on the rim. Tap to change this place's trip radius.
        val tag = RoundedCornerShape(22.dp)
        Row(
            Modifier.offset(y = ChosenCircle / 2)
                .glow(NeonCyan, 12.dp, tag).background(Color(0xFF0B0F14), tag).border(2.dp, NeonCyan, tag)
                .clip(tag).clickable(onClick = onEditAlarm)
                .padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(18.dp), tint = NeonCyan)
            Text(alarmLabel(place.tripRadiusM), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = InkBright, maxLines = 1)
            Spacer(Modifier.width(1.dp).height(16.dp).background(Color(0xFF2B3A4D)))
            Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(14.dp), tint = Muted)
        }
    }
}

/** Quiet oval with the only thing we know before moving: how far you are now. */
@Composable
private fun AwayOval(awayM: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(40.dp)
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF131C26), Color(0xFF0F1620))), shape)
            .border(1.dp, Color(0xFF222E3D), shape)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            TripService.formatDistance(awayM),
            fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = NeonCyan,
        )
        SectionLabel("Away from you")
    }
}

/** A dashed circular outline (the border modifier can't dash). */
private fun Modifier.dashedCircle(color: Color, width: Dp): Modifier = drawBehind {
    drawCircle(
        color, radius = size.minDimension / 2 - width.toPx() / 2, center = center,
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))),
    )
}
