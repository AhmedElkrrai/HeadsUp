package com.elkrrai.headsup.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.trip.TripState
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.glow
import kotlin.math.PI
import kotlin.math.sin

private val CircleSize = 208.dp

/**
 * The Trip tab while a trip is running (and while its alarm rings): the destination in a radiating circle
 * with the alarm distance on its rim, an oval with time left, speed, kilometres left and passed, and one
 * button. The ringing state is the same screen in alarm red, with a shaking bell.
 */
@Composable
internal fun ActiveTrip(t: TripState, onStop: () -> Unit) {
    val ringing = t.arrived
    val accent = if (ringing) HoldRed else NeonCyan

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Radiation(accent, ringing)
            DestinationCircle(t.name, accent, ringing)
            AlarmTag(alarmLabel(t.alertM), accent, Modifier.align(Alignment.Center).offset(y = CircleSize / 2))
        }

        StatsOval(t, accent, Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 14.dp))

        val buttonShape = RoundedCornerShape(28.dp)
        Box(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
            if (ringing) {
                Button(
                    onClick = onStop,
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = HoldRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(56.dp).glow(HoldRed, 18.dp, buttonShape),
                ) {
                    Check(Color.White)
                    Text("  Dismiss alarm", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onStop,
                    shape = buttonShape,
                    border = BorderStroke(1.5.dp, HoldRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HoldRed),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Box(Modifier.size(14.dp).background(HoldRed, RoundedCornerShape(3.dp)))
                    Text("  Stop trip", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * The oval: time left (big), a bar (white = passed, neon = remaining), then speed, remaining (neon)
 * and passed (white).
 */
@Composable
private fun StatsOval(t: TripState, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(46.dp)
    val remaining = t.distanceM
    val start = t.startM ?: remaining
    val passed = if (remaining != null && start != null) (start - remaining).coerceAtLeast(0) else null
    val fraction = if (passed != null && start != null && start > 0) (passed.toFloat() / start).coerceIn(0f, 1f) else 0f

    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF131C26), Color(0xFF0F1620))), shape)
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel("Time remaining")
            Text(
                timeText(t.etaMin),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = accent,
                    shadow = Shadow(accent.copy(alpha = 0.5f), Offset.Zero, 18f),
                ),
            )
        }

        // Passed (white) vs remaining (neon)
        Box(Modifier.fillMaxWidth().height(6.dp).background(accent.copy(alpha = 0.45f), CircleShape)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(InkBright, CircleShape))
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Stat(t.speedKmh?.toString() ?: "–", "km/h", "Speed", InkBright, Modifier.weight(1f))
            Spacer(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 2.dp).background(Outline))
            Stat(remaining?.let { kmValue(it) } ?: "–", "km", "Remaining", accent, Modifier.weight(1f), glow = true)
            Spacer(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 2.dp).background(Outline))
            Stat(passed?.let { kmValue(it) } ?: "–", "km", "Passed", InkBright, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, unit: String, label: String, valueColor: Color, modifier: Modifier = Modifier, glow: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                append(value)
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted)) { append(" $unit") }
            },
            fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1,
            style = if (glow) MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = valueColor,
                shadow = Shadow(valueColor.copy(alpha = 0.5f), Offset.Zero, 12f),
            ) else MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = valueColor),
        )
        SectionLabel(label)
    }
}

/** "1 h 8 min", "45 min", or "–" while there is no speed reading yet. */
private fun timeText(etaMin: Int?) = buildAnnotatedString {
    if (etaMin == null) {
        append("–")
        return@buildAnnotatedString
    }
    val unit = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC7D3DF))
    val h = etaMin / 60
    val m = etaMin % 60
    if (h > 0) {
        append("$h"); withStyle(unit) { append(" h ") }
    }
    append("$m"); withStyle(unit) { append(" min") }
}

/** Metres as a km number for the oval: "23.4", "70", "0.4" (no trailing ".0", no decimals from 100 km). */
private fun kmValue(m: Int): String {
    val km = m / 1000.0
    return if (km >= 100) "%.0f".format(km) else "%.1f".format(km).removeSuffix(".0")
}

/** The alarm distance, as a round tag sitting on the circle's bottom edge. */
@Composable
private fun AlarmTag(label: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.glow(accent, 12.dp, shape).background(Color(0xFF0B0F14), shape).border(2.dp, accent, shape)
            .padding(start = 12.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(18.dp), tint = accent)
        Text(label, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = InkBright, maxLines = 1)
    }
}

/** Soft glow plus rings that keep radiating outward from the circle's edge. */
@Composable
private fun Radiation(accent: Color, ringing: Boolean) {
    val transition = rememberInfiniteTransition(label = "radiation")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (ringing) 1600 else 3600, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = (CircleSize / 2).toPx()
        // Breathing glow behind everything
        val breath = 0.8f + 0.2f * sin(phase.value * 2f * PI.toFloat())
        drawCircle(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.45f * breath), Color.Transparent),
                center = c, radius = 200.dp.toPx(),
            ),
            radius = 200.dp.toPx(), center = c,
        )
        // Three rings, evenly staggered. (Read here, in the draw phase, so only this canvas redraws.)
        for (i in 0..2) {
            val p = (phase.value + i / 3f) % 1f
            drawCircle(
                color = accent.copy(alpha = (if (ringing) 0.85f else 0.75f) * (1f - p)),
                radius = r * (1f + 0.8f * p),
                center = c,
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun DestinationCircle(name: String, accent: Color, ringing: Boolean) {
    val transition = rememberInfiniteTransition(label = "shake")
    val shake = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shake",
    )
    val inner = if (ringing) listOf(Color(0xFF3A0F1B), Color(0xFF150C12)) else listOf(Color(0xFF0F3A3A).copy(alpha = 0.9f), Color(0xFF0F161F))
    Box(
        Modifier.size(CircleSize)
            .glow(accent, 28.dp, CircleShape)
            .background(Brush.radialGradient(inner, center = Offset(104f * 2.6f, 62f * 2.6f), radius = 300f), CircleShape)
            .border(2.dp, accent, CircleShape)
            .padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (ringing) {
                Icon(
                    painterResource(R.drawable.ic_bell), null, tint = accent,
                    modifier = Modifier.size(30.dp).graphicsLayer {
                        // A quick shake, then a pause, like a ringing bell.
                        val p = shake.value
                        rotationZ = if (p < 0.5f) 14f * sin(p * 2f * PI.toFloat() * 4f) * (1f - p * 2f) else 0f
                        transformOrigin = TransformOrigin(0.5f, 0.08f)
                    },
                )
            } else {
                Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(26.dp), tint = NeonMagenta)
            }
            SectionLabel(if (ringing) "Almost there" else "Heading to")
            Text(
                name,
                fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = InkBright,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Check(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.74f)
            lineTo(size.width * 0.8f, size.height * 0.3f)
        }
        drawPath(p, color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
