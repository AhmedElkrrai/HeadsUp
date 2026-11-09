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
import com.elkrrai.headsup.data.Radius
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.SurfaceCard
import kotlin.math.roundToInt

/** How early the alarm can ring, in metres. Evenly spaced stops on the trip-radius slider (place editor). */
internal val alarmStops = Radius.TRIP_STOPS

internal fun alarmLabel(m: Int) = if (m >= 1000) "${m / 1000} km" else "$m m"

/** Slider with a few evenly spaced stops (tap or drag), each labelled underneath. */
@Composable
internal fun StepSlider(index: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
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
