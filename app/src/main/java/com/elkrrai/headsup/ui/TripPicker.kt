package com.elkrrai.headsup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elkrrai.headsup.R
import com.elkrrai.headsup.data.Place
import com.elkrrai.headsup.trip.TripService
import com.elkrrai.headsup.ui.theme.Ink
import com.elkrrai.headsup.ui.theme.InkBright
import com.elkrrai.headsup.ui.theme.Muted
import com.elkrrai.headsup.ui.theme.NeonCyan
import com.elkrrai.headsup.ui.theme.NeonMagenta
import com.elkrrai.headsup.ui.theme.Outline
import com.elkrrai.headsup.ui.theme.SurfaceCard
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * Trip tab: "Saved places" on the destination card. A bottom sheet of your places with how far each is;
 * one tap picks it and the sheet closes. (Pinning a new destination is the card's other button.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripPlaceSheet(
    places: List<Place>,
    here: GeoPoint?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onPickOnMap: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Closest first (when we know where you are).
    val sortedPlaces = remember(places, here) {
        if (here == null) places else places.sortedBy { here.distanceToAsDouble(GeoPoint(it.lat, it.lng)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111821),
        contentColor = Ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFF3A4A5E), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Where are you getting off?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = InkBright)
                Text(
                    if (places.isEmpty()) "You have no saved places yet." else if (here != null) "Closest first. Scroll for more." else "Tap one of your saved places.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA9B8C7),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPickOnMap != null) {
                    val mapShape = RoundedCornerShape(20.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(mapShape)
                            .border(1.5.dp, NeonCyan, mapShape)
                            .clickable { scope.launch { sheetState.hide() }.invokeOnCompletion { onPickOnMap() } }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(painterResource(R.drawable.ic_map), null, Modifier.size(20.dp), tint = NeonCyan)
                        Text("Choose on the map", fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                }
                sortedPlaces.forEach { p ->
                    val away = (here?.distanceToAsDouble(GeoPoint(p.lat, p.lng))?.toInt()?.let { TripService.formatDistance(it) + " away · " } ?: "") +
                        "alarm ${alarmLabel(p.tripRadiusM)}"
                    val shape = RoundedCornerShape(20.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(shape).background(SurfaceCard)
                            .border(1.dp, Outline, shape)
                            // Slide the sheet away first, then pick.
                            .clickable { scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(p.id) } }
                            .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(40.dp).background(Color(0xFF2A0F25), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(22.dp), tint = NeonMagenta)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = InkBright)
                            if (away != null) Text(away, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = Muted)
                    }
                }
            }
        }
    }
}
