package com.elkrrai.headsup.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.SurfaceCard
import kotlin.math.roundToInt

/** How early the alarm can ring, in metres. Evenly spaced stops on the slider. */
internal val alarmStops = listOf(500, 1000, 2000, 3000, 5000)

internal fun alarmLabel(m: Int) = if (m >= 1000) "${m / 1000} km" else "$m m"

/**
 * "Wake me" card for the Trip tab: a little map with the alarm ring around the destination (it grows and
 * shrinks as you move the slider) and a stepped slider underneath.
 */
@Composable
internal fun AlarmRadarCard(placeName: String?, alertM: Int, onChange: (Int) -> Unit) {
    // Nothing to measure from until a destination is chosen, so the card is dimmed and can't be changed.
    val enabled = placeName != null
    val shape = RoundedCornerShape(22.dp)
    val index = alarmStops.indexOf(alertM).let { if (it >= 0) it else alarmStops.indexOfFirst { s -> s >= alertM }.coerceAtLeast(0) }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(shape).background(SurfaceCard).border(1.dp, Outline, shape)) {
        Text(
            if (enabled) "Alarm rings when you cross the ring" else "Pick a destination first",
            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA9B8C7),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        )
        RadarMap(index)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                SectionLabel("Wake me", Modifier.weight(1f))
                Text(
                    alarmLabel(alarmStops[index]),
                    fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = NeonCyan,
                )
                Text(
                    "  before ${placeName ?: "arriving"}",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA9B8C7),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            StepSlider(index, enabled) { onChange(alarmStops[it]) }
        }
    }
}

/** The map picture. Drawn in a 342 x 150 space and scaled to the card's width. */
@Composable
private fun RadarMap(index: Int) {
    // Ring radius per stop, in the 342 x 150 space (kept inside the tile at the largest setting).
    val ringTargets = listOf(34f, 46f, 56f, 64f, 70f)
    val ring by animateFloatAsState(ringTargets[index], tween(300, easing = FastOutSlowInEasing), label = "ring")
    val bell = painterResource(R.drawable.ic_bell)

    Box(Modifier.fillMaxWidth().aspectRatio(342f / 150f).background(Color(0xFF0E141C))) {
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 342f, Offset.Zero) {
                val grid = Color(0xFF1B2634)
                for (x in listOf(57f, 114f, 171f, 228f, 285f)) drawLine(grid, Offset(x, 0f), Offset(x, 150f), 1f)
                for (y in listOf(37f, 75f, 112f)) drawLine(grid, Offset(0f, y), Offset(342f, y), 1f)

                val pin = Offset(171f, 75f)

                // The road you travel on
                val road = Path().apply {
                    moveTo(-10f, 157f); lineTo(40f, 134f); lineTo(171f, 75f); lineTo(350f, -6f)
                }
                drawPath(road, Color(0xFF2A2145), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // The alarm ring
                drawCircle(NeonCyan.copy(alpha = 0.12f), radius = ring, center = pin)
                drawCircle(
                    NeonCyan, radius = ring, center = pin,
                    style = Stroke(2.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))),
                )

                // The destination pin (tip on the centre)
                translate(pin.x - 125f * 0.62f, pin.y - 126.3f * 0.62f) {
                    scale(0.62f, Offset.Zero) {
                        val p = Path().apply {
                            moveTo(125f, 62f)
                            cubicTo(112.6f, 62f, 102.5f, 72.1f, 102.5f, 84.5f)
                            cubicTo(102.5f, 101.4f, 125f, 126.3f, 125f, 126.3f)
                            cubicTo(125f, 126.3f, 147.5f, 101.4f, 147.5f, 84.5f)
                            cubicTo(147.5f, 72.1f, 137.4f, 62f, 125f, 62f)
                            close()
                        }
                        drawPath(p, Color(0xFFFF2EC4))
                        drawCircle(Color.White, radius = 8f, center = Offset(125f, 84.5f))
                    }
                }

                // You
                val you = Offset(46f, 131f)
                drawCircle(Color(0xFF3B82F6).copy(alpha = 0.22f), radius = 14f, center = you)
                drawCircle(Color.White, radius = 9f, center = you)
                drawCircle(Color(0xFF3B82F6), radius = 7f, center = you)

                // The bell sits where the route crosses the ring
                val dir = Offset(-0.912f, 0.411f)
                val bellAt = Offset(pin.x + dir.x * ring, pin.y + dir.y * ring)
                drawCircle(NeonCyan.copy(alpha = 0.35f), radius = 20f, center = bellAt)
                drawCircle(NeonCyan, radius = 15f, center = bellAt)
                translate(bellAt.x - 12f, bellAt.y - 12f) {
                    with(bell) { draw(Size(24f, 24f), colorFilter = ColorFilter.tint(OnNeon)) }
                }
            }
        }
    }
}

/** Slider with a few evenly spaced stops (tap or drag), each labelled underneath. */
@Composable
private fun StepSlider(index: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val last = alarmStops.size - 1
    val insetDp = 12.dp
    val animated by animateFloatAsState(index.toFloat() / last, tween(160), label = "thumb")

    fun indexAt(x: Float, inset: Float): Int {
        val frac = ((x - inset) / (widthPx - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return (frac * last).roundToInt()
    }

    Column {
        Box(
            Modifier.fillMaxWidth().height(36.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled) { if (enabled) detectTapGestures { onSelect(indexAt(it.x, insetDp.toPx())) } }
                .pointerInput(enabled) {
                    if (enabled) detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onSelect(indexAt(change.position.x, insetDp.toPx()))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = insetDp.toPx()
                val y = size.height / 2
                val trackW = size.width - 2 * inset
                val x = inset + trackW * animated
                drawLine(OutlineStrong, Offset(inset, y), Offset(size.width - inset, y), 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(NeonCyan, Offset(inset, y), Offset(x, y), 4.dp.toPx(), cap = StrokeCap.Round)
                for (i in 0..last) {
                    val sx = inset + trackW * i / last
                    if (sx > x + 6.dp.toPx()) drawCircle(Color(0xFF3A4A5E), radius = 3.dp.toPx(), center = Offset(sx, y))
                }
                drawCircle(NeonCyan.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = Offset(x, y))
                drawCircle(NeonCyan, radius = 10.dp.toPx(), center = Offset(x, y))
            }
        }
        // Labels centred under their stops
        Layout(
            content = {
                alarmStops.forEachIndexed { i, m ->
                    Text(
                        alarmLabel(m), fontSize = 12.sp,
                        fontWeight = if (i == index) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (i == index) NeonCyan else Color(0xFF8FA0B2),
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val inset = insetDp.roundToPx()
            val track = (constraints.maxWidth - 2 * inset).coerceAtLeast(1)
            val h = placeables.maxOf { it.height }
            layout(constraints.maxWidth, h) {
                placeables.forEachIndexed { i, p ->
                    val cx = inset + track * i / last
                    p.placeRelative((cx - p.width / 2).coerceIn(0, constraints.maxWidth - p.width), 0)
                }
            }
        }
    }
}
