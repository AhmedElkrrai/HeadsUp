package com.elkrrai.headsup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elkrrai.headsup.R
import com.elkrrai.headsup.ui.theme.Ink
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.glow

/** A small pill shown inside the confirmation: what exactly is affected. [icon] is optional. */
data class DeleteChip(val label: String, val icon: Int? = null, val tint: Color = Color(0xFF9AA9B8))

/**
 * Delete confirmation, in the same red neon language as the hold-to-delete gesture: a glowing trash badge,
 * a title naming the item, the things that go with it as chips, a plain "can't be undone" line, and two
 * full-width buttons (red delete, quiet "Keep it").
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    intro: String,
    chips: List<DeleteChip>,
    outro: String? = null,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** The red line under the chips; null hides it. */
    warning: String? = "This can't be undone.",
    dismissLabel: String = "Keep it",
    icon: Int = R.drawable.ic_trash,
) {
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = shape,
            color = Color(0xFF111821),
            border = BorderStroke(1.dp, HoldRed.copy(alpha = 0.38f)),
            modifier = Modifier.padding(horizontal = 24.dp).glow(HoldRed.copy(alpha = 0.5f), 28.dp, shape),
        ) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(60.dp).glow(HoldRed, 18.dp).background(Color(0xFF3A1020), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, Modifier.size(28.dp), tint = HoldRed)
                }

                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkBright,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    intro,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB9C6D3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (chips.isNotEmpty()) {
                    Column(
                        Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        chips.forEach { DeleteChipPill(it) }
                    }
                }
                if (outro != null) {
                    Text(
                        outro,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFB9C6D3),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                if (warning != null) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF8AA0),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConfirm,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = HoldRed, contentColor = Color(0xFF1A0208)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).glow(HoldRed, 14.dp),
                    ) { Text(confirmLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, OutlineStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(dismissLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun DeleteChipPill(chip: DeleteChip) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.clip(shape).background(Color(0xFF1B2430)).border(1.dp, OutlineStrong, shape)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chip.icon != null) Icon(painterResource(chip.icon), null, Modifier.size(16.dp), tint = chip.tint)
        Text(chip.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkBright, maxLines = 1)
    }
}
