package com.elkrrai.headsup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Red neon used for the hold-to-confirm halo and colour wash. */
val HoldRed = Color(0xFFFF2D55)

/**
 * Hold-to-confirm for a card: press and hold anywhere on it for [holdMillis] to run [onConfirm].
 *
 * - A short tap never confirms. If the finger lifts quickly, [onTap] runs instead (if given).
 * - While held, the card gets a red neon halo and a colour wash that spreads out from its centre,
 *   reaching the corners exactly when the hold completes. A faint press-in scale adds weight.
 * - Lifting early, or sliding the finger off the card (or letting a parent scroll take over),
 *   cancels, and the halo and wash ease back out. Even a very quick tap plays a small partial fill.
 * - Haptic tick on confirm. For TalkBack, a long-click action runs [onConfirm] directly, since
 *   holding with an assistive service is already a deliberate action.
 *
 * All animation state is read in draw / graphics-layer lambdas, so it never recomposes the card.
 *
 * @param cornerRadius must match the card's corner radius so the halo and wash follow its shape.
 */
@Composable
fun Modifier.holdToConfirm(
    onConfirm: () -> Unit,
    onTap: (() -> Unit)? = null,
    cornerRadius: Dp = 20.dp,
    holdMillis: Int = 1200,
    color: Color = HoldRed,
    confirmLabel: String = "Hold to confirm",
): Modifier {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) } // 0 = idle, 1 = hold complete
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnTap by rememberUpdatedState(onTap)
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    return this
        // Press-in feel.
        .graphicsLayer {
            val s = 1f - 0.02f * progress.value
            scaleX = s
            scaleY = s
        }
        // Red neon halo: stacked, growing, translucent rounded rects behind the card (a cheap soft glow).
        .drawBehind {
            val p = progress.value
            if (p > 0f) {
                val layers = 7
                val maxGrow = 18.dp.toPx() * p
                for (i in layers downTo 1) {
                    val grow = maxGrow * i / layers
                    drawRoundRect(
                        color = color.copy(alpha = 0.11f * p),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(cornerRadius.toPx() + grow),
                    )
                }
            }
        }
        .clip(shape)
        // Colour wash from the centre, plus a neon edge that brightens as it fills.
        .drawWithContent {
            drawContent()
            val p = progress.value
            if (p > 0f) {
                val reach = hypot(size.width, size.height) / 2f // centre to corner
                val radius = (reach * FastOutSlowInEasing.transform(p)).coerceAtLeast(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = 0.50f),
                        1f to color.copy(alpha = 0.22f),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                drawRoundRect(
                    color = color.copy(alpha = p),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        .semantics(mergeDescendants = true) {
            onClick(label = "Open") {
                currentOnTap?.invoke()
                true
            }
            onLongClick(label = confirmLabel) {
                currentOnConfirm()
                true
            }
        }
        .pointerInput(holdMillis) {
            awaitEachGesture {
                // Ignore touches a control inside the card already took (e.g. the "done" checkbox).
                val down = awaitFirstDown(requireUnconsumed = true)
                var confirmed = false

                // The hold timer: runs the fill to 100 % and confirms. Cancelled if the gesture ends first.
                val hold = scope.launch {
                    progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                    confirmed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnConfirm()
                }

                // Stay inside the card (with a little slop for finger jitter) until lift or cancel.
                val slop = viewConfig.touchSlop
                val bounds = Rect(-slop, -slop, size.width + slop, size.height + slop)
                var lifted = false
                var liftTime = down.uptimeMillis
                while (true) {
                    // Final pass: by then a parent that wants the gesture (list scroll) has consumed it.
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed()) {
                        lifted = true
                        liftTime = change.uptimeMillis
                        break
                    }
                    // A parent (e.g. the list scrolling) took the gesture, or the finger slid away.
                    if (change.isConsumed || !bounds.contains(change.position)) break
                }

                if (confirmed) {
                    // Done: ease the effect out once the finger is gone.
                    scope.launch { progress.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                } else {
                    hold.cancel()
                    // A quick lift inside the card is a tap, never a confirm.
                    if (lifted && liftTime - down.uptimeMillis < viewConfig.longPressTimeoutMillis) {
                        currentOnTap?.invoke()
                    }
                    scope.launch {
                        // Make even a very quick tap show a small partial fill, then ease back.
                        if (progress.value < 0.15f) progress.animateTo(0.15f, tween(90, easing = LinearEasing))
                        progress.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
}
