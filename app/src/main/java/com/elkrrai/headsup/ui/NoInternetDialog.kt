package com.elkrrai.headsup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.OnNeon
import com.elkrrai.headsup.ui.theme.OutlineStrong
import com.elkrrai.headsup.ui.theme.glow

/**
 * "No internet" popup, in the same neon style as the delete confirmation: a glowing wifi-off badge, a plain
 * explanation, a full-width Try again button (reruns whatever just failed) and a quiet Cancel.
 */
@Composable
fun NoInternetDialog(
    body: String,
    /** True while the retry is running: the button shows a loader and can't be tapped again. */
    retrying: Boolean = false,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = shape,
            color = Color(0xFF111821),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.38f)),
            modifier = Modifier.padding(horizontal = 24.dp).glow(NeonCyan.copy(alpha = 0.32f), 28.dp, shape),
        ) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(60.dp).glow(NeonCyan, 18.dp).background(Color(0xFF0E2F31), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_wifi_off), null, Modifier.size(30.dp), tint = NeonCyan)
                }

                Text(
                    "No internet connection",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkBright,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB9C6D3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { if (!retrying) onRetry() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = OnNeon),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).glow(NeonCyan, 14.dp),
                    ) {
                        if (retrying) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = OnNeon, strokeWidth = 2.5.dp)
                            Text("  Trying…", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Try again", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, OutlineStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
