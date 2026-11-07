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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonCyanSoft
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.SurfaceCard
import com.elkrrai.headsup.ui.theme.glow

/**
 * Shown on the Reminders tab while there are no reminders: a short welcome, one clear button,
 * a few one-tap starters, and a tip about sharing from WhatsApp. (The "+" button is hidden here
 * because the big button does the same job.)
 */
@Composable
fun EmptyReminders(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
        StatusCard(vm, onRequestPermissions)

        // Centred when there's room; scrolls on short screens instead of clipping.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RippleBadge()
                Spacer(Modifier.height(20.dp))
                Text(
                    "Nothing to remember yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkBright,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Get a nudge when you're near a place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFA9B8C7),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { vm.newReminder() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OnNeon),
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier.glow(NeonCyan, 16.dp).heightIn(min = 56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(22.dp))
                    Text("  Add your first reminder", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(26.dp))
                SectionLabel("Or start from one of these")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Groceries", false, onClick = { vm.newReminder(title = "Groceries") }, hPad = 16.dp)
                    Choice("Pick up prescription", false, onClick = { vm.newReminder(title = "Pick up prescription") }, hPad = 16.dp)
                }
            }
        }

        TipCard()
    }
}

/** A pin inside a dashed circle (like the circle you draw around a place) with a slow ripple. */
@Composable
private fun RippleBadge() {
    val transition = rememberInfiniteTransition(label = "ripple")
    val t = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "ripplePhase",
    )
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            // Three ripples, evenly staggered. The phase is read here (draw phase) so only this canvas redraws.
            for (i in 0..2) {
                val p = (t.value + i / 3f) % 1f
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.6f * (1f - p)),
                    radius = maxR * (0.55f + 0.7f * p),
                    center = center,
                    style = Stroke(2.dp.toPx()),
                )
            }
            val zone = 66.dp.toPx()
            drawCircle(NeonCyanSoft, radius = zone, center = center)
            drawCircle(
                color = NeonCyan.copy(alpha = 0.45f),
                radius = zone,
                center = center,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
            )
        }
        Box(
            Modifier.size(84.dp).glow(NeonCyan, 20.dp).background(SurfaceCard, CircleShape).border(1.dp, OutlineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(40.dp), tint = NeonMagenta)
        }
    }
}

@Composable
private fun TipCard() {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(SurfaceCard).border(1.dp, Outline, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_share), null, Modifier.size(20.dp), tint = NeonCyan)
        }
        Text(
            "Tip: share a list from WhatsApp into Heads Up and it becomes the note.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD5E0EA),
        )
    }
}
