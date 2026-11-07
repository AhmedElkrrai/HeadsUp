package com.elkrrai.headsup.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** OpenStreetMap view: tap to drop the pin; a circle shows the reminder radius. */
@Composable
fun MapPicker(
    pin: GeoPoint?,
    radiusM: Int,
    focus: GeoPoint?,
    focusKey: Int,
    onPick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 3.0
            controller.setZoom(if (pin != null || focus != null) 16.0 else 4.0)
            (pin ?: focus)?.let { controller.setCenter(it) } ?: controller.setCenter(GeoPoint(30.0, 31.2))
        }
    }

    val lifecycle = (ctx as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            mv.overlays.clear()
            mv.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    onPick(p)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            if (pin != null) {
                mv.overlays.add(Polygon(mv).apply {
                    points = Polygon.pointsAsCircle(pin, radiusM.toDouble())
                    fillPaint.color = 0x332196F3
                    outlinePaint.color = 0xFF2196F3.toInt()
                    outlinePaint.strokeWidth = 4f
                })
                mv.overlays.add(Marker(mv).apply {
                    position = pin
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            mv.invalidate()
        },
    )

    LaunchedEffect(focusKey) {
        if (focus != null) {
            mapView.controller.animateTo(focus)
            mapView.controller.setZoom(16.0)
        }
    }
}
