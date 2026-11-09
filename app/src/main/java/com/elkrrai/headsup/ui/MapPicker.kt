package com.elkrrai.headsup.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.elkrrai.headsup.R
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
    /** Dashed ring (used for trip alarm distances) instead of a solid one. */
    dashedRing: Boolean = false,
    focus: GeoPoint?,
    focusKey: Int,
    focusZoom: Double = 16.0,
    me: GeoPoint? = null,
    onPick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    /** Called on every real finger touch on the map (pan, zoom, tap), not on programmatic moves. */
    onTouch: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val currentOnTouch by rememberUpdatedState(onTouch)
    val mapView = remember {
        MapView(ctx).apply {
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            setOnTouchListener { _, _ ->
                currentOnTouch()
                false // never consume: the map still handles the gesture
            }
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            // Dark map to match the app: invert the light OpenStreetMap tiles.
            overlayManager.tilesOverlay.setColorFilter(org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS)
            // Open already at street level on the right spot: no world view, no animated jump.
            val start = pin ?: focus
            controller.setZoom(if (start != null) 16.0 else 4.0)
            controller.setCenter(start ?: GeoPoint(30.0, 31.2))
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
                if (radiusM > 0) {
                    val ring = Polygon.pointsAsCircle(pin, radiusM.toDouble())
                    // Soft outer glow, then the neon ring itself.
                    mv.overlays.add(Polygon(mv).apply {
                        points = ring
                        fillPaint.color = 0x00000000
                        outlinePaint.color = 0x3322F5E0
                        outlinePaint.strokeWidth = 18f
                        // Same as the ring below: no info bubble, taps fall through to the map.
                        infoWindow = null
                        setOnClickListener { _, _, _ -> false }
                    })
                    mv.overlays.add(Polygon(mv).apply {
                        points = ring
                        fillPaint.color = 0x2422F5E0
                        outlinePaint.color = 0xFF22F5E0.toInt()
                        outlinePaint.strokeWidth = 5f
                        if (dashedRing) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(34f, 22f), 0f)
                        // Tapping inside the ring would pop osmdroid's empty info bubble; let the tap move the pin instead.
                        infoWindow = null
                        setOnClickListener { _, _, _ -> false }
                    })
                }
                mv.overlays.add(Marker(mv).apply {
                    position = pin
                    icon = ContextCompat.getDrawable(ctx, R.drawable.ic_map_pin)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    // No info bubble (it would be an empty grey box); the tap still reaches the map underneath.
                    setOnMarkerClickListener { _, _ -> false }
                })
            }
            // Blue dot: where you are right now.
            if (me != null) {
                mv.overlays.add(Marker(mv).apply {
                    position = me
                    icon = ContextCompat.getDrawable(ctx, R.drawable.ic_me_dot)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> false }
                })
            }
            mv.invalidate()
        },
    )

    LaunchedEffect(focusKey) {
        if (focusKey > 0 && focus != null) {
            mapView.controller.setZoom(focusZoom)
            mapView.controller.setCenter(focus)
        }
    }
}
