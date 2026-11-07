package com.elkrrai.headsup.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.glow

/**
 * Shown on the Places tab while there are no saved places: a picture of what a place is (a pin with a
 * circle), one clear button, a few name starters, and a tip. (The "+" button is hidden here because the
 * big button does the same job.)
 */
@Composable
fun EmptyPlaces(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
        // Centred when there's room; scrolls on short screens instead of clipping.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlaceTile()
                Spacer(Modifier.height(22.dp))
                Text(
                    "No places yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkBright,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "A place is a pin with a circle around it. Reminders and trips use your places to know where to alert you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFA9B8C7),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { vm.openPlaceEditor(null) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OnNeon),
                    modifier = Modifier.glow(NeonCyan, 16.dp).heightIn(min = 56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(22.dp))
                    Text("  Pin your first place", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                SectionLabel("Or start with a name")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work", "Bus stop").forEach { label ->
                        Choice(label, false, onClick = { vm.openPlaceEditor(null, suggestedName = label) })
                    }
                }
            }
        }

        TipCard(
            text = "Tip: the circle's size decides how close you need to be before an alert.",
            icon = R.drawable.ic_info,
        )
    }
}

/** A map tile with a pin and a dashed circle around it. The pin bobs gently; a ring pulses from its tip. */
@Composable
private fun PlaceTile() {
    val transition = rememberInfiniteTransition(label = "placeTile")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "pulse",
    )
    val shape = RoundedCornerShape(28.dp)
    Canvas(Modifier.size(width = 250.dp, height = 164.dp).clip(shape).border(1.dp, Outline, shape)) {
        // Draw in a 250 x 164 coordinate space, scaled to the real size.
        scale(size.width / 250f, Offset.Zero) {
            drawRect(Color(0xFF0E141C))
            val grid = Color(0xFF1B2634)
            for (x in listOf(41f, 83f, 125f, 167f, 209f)) drawLine(grid, Offset(x, 0f), Offset(x, 164f), 1f)
            for (y in listOf(41f, 82f, 123f)) drawLine(grid, Offset(0f, y), Offset(250f, y), 1f)

            // A road
            val road = Path().apply {
                moveTo(-10f, 128f)
                lineTo(90f, 96f)
                lineTo(170f, 110f)
                lineTo(260f, 62f)
            }
            drawPath(road, Color(0xFF2A2145), style = Stroke(width = 14f, cap = StrokeCap.Round))

            // The circle around the place
            drawCircle(NeonCyan.copy(alpha = 0.12f), radius = 46f, center = Offset(125f, 86f))
            drawCircle(
                NeonCyan, radius = 46f, center = Offset(125f, 86f),
                style = Stroke(width = 2.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))),
            )

            // Ring pulsing from the pin's tip
            drawCircle(
                NeonCyan.copy(alpha = 0.7f * (1f - pulse)), radius = 22f * (0.5f + 0.65f * pulse),
                center = Offset(125f, 98f), style = Stroke(width = 2f),
            )

            // The pin, bobbing up and down
            translate(top = -5f * bob) {
                val pin = Path().apply {
                    moveTo(125f, 62f)
                    cubicTo(112.6f, 62f, 102.5f, 72.1f, 102.5f, 84.5f)
                    cubicTo(102.5f, 101.4f, 125f, 126.3f, 125f, 126.3f)
                    cubicTo(125f, 126.3f, 147.5f, 101.4f, 147.5f, 84.5f)
                    cubicTo(147.5f, 72.1f, 137.4f, 62f, 125f, 62f)
                    close()
                }
                drawPath(pin, Color(0xFFFF2EC4))
                drawCircle(Color.White, radius = 8f, center = Offset(125f, 84.5f))
            }
        }
    }
}
