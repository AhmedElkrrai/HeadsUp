package com.elkrrai.headsup.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.trip.TripService
import com.elkrrai.headsup.trip.TripState
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.glow
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Trip tab while a trip is running (and while its alarm rings): a big circle with the destination
 * in it, radiating rings around it, how far away you are and where the alarm fires, and one button.
 * The ringing state is the same screen in alarm red with faster, shaking-bell energy.
 */
@Composable
internal fun ActiveTrip(t: TripState, onStop: () -> Unit) {
    val ringing = t.arrived
    val accent = if (ringing) HoldRed else NeonCyan

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Radiation(accent, ringing)
            DestinationCircle(t.name, accent, ringing)
        }

        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp).height(IntrinsicSize.Min)) {
            Stat(
                value = t.distanceM?.let { TripService.formatDistance(it) } ?: "…",
                label = if (t.distanceM == null) "Waiting for GPS" else "Away",
                valueColor = accent,
                glowColor = accent,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(Outline))
            Stat(
                value = alarmLabel(t.alertM),
                label = if (ringing) "Alarm fired at" else "Alarm rings at",
                valueColor = InkBright,
                icon = true,
                modifier = Modifier.weight(1f),
            )
        }
        t.etaMin?.let {
            if (!ringing) Text(
                "About $it min to go",
                style = MaterialTheme.typography.bodyMedium, color = Muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        val buttonShape = RoundedCornerShape(28.dp)
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)) {
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
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, HoldRed),
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

@Composable
private fun Stat(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    icon: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1,
            style = if (glowColor != null) {
                MaterialTheme.typography.displaySmall.copy(
                    fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = valueColor,
                    shadow = androidx.compose.ui.graphics.Shadow(glowColor.copy(alpha = 0.55f), Offset.Zero, 18f),
                )
            } else MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = valueColor),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon) Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(14.dp), tint = Muted)
            SectionLabel(label)
        }
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
        val r = 116.dp.toPx()
        // Breathing glow behind everything
        val breath = 0.8f + 0.2f * sin(phase.value * 2f * PI.toFloat())
        drawCircle(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.45f * breath), Color.Transparent),
                center = c, radius = 220.dp.toPx(),
            ),
            radius = 220.dp.toPx(), center = c,
        )
        // Three rings, evenly staggered. (Read here, in the draw phase, so only this canvas redraws.)
        for (i in 0..2) {
            val p = (phase.value + i / 3f) % 1f
            drawCircle(
                color = accent.copy(alpha = (if (ringing) 0.85f else 0.75f) * (1f - p)),
                radius = r * (1f + 0.9f * p),
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
        Modifier.size(232.dp)
            .glow(accent, 28.dp, CircleShape)
            .background(Brush.radialGradient(inner, center = Offset(116f * 2.6f, 70f * 2.6f), radius = 330f), CircleShape)
            .border(2.dp, accent, CircleShape)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ringing) {
                Icon(
                    painterResource(R.drawable.ic_bell), null, tint = accent,
                    modifier = Modifier.size(34.dp).graphicsLayer {
                        // A quick shake, then a pause, like a ringing bell.
                        val p = shake.value
                        rotationZ = if (p < 0.5f) 14f * sin(p * 2f * PI.toFloat() * 4f) * (1f - p * 2f) else 0f
                        transformOrigin = TransformOrigin(0.5f, 0.08f)
                    },
                )
            } else {
                Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(30.dp), tint = NeonMagenta)
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
