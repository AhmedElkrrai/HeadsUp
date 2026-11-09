package com.elkrrai.headsup.trip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.elkrrai.headsup.R

/**
 * Pictures for the trip alarm notification (drawn at run time so the destination name is in them):
 * a wide banner for the expanded notification and a small round icon for the collapsed one.
 */
internal object AlarmArt {
    /** Deep alarm red (a darker, calmer red than the app's pink-red). */
    private const val RED = 0xFFE53935.toInt()
    private const val RED_SOFT = 0xFFFF8A80.toInt()

    /** Wide banner: radiating rings with the destination name in the circle. About 3:1.4. */
    fun banner(name: String): Bitmap {
        val w = 1074
        val h = 504
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = w / 358f
        c.scale(k, k)

        val cx = 179f
        val cy = 84f
        c.drawColor(0xFF0D1118.toInt())

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, 215f, intArrayOf(withAlpha(RED, 0.5f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 358f, 168f, glow)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = RED }
        ring.strokeWidth = 1.4f; ring.alpha = (255 * 0.2f).toInt(); c.drawCircle(cx, cy, 82f, ring)
        ring.strokeWidth = 1.6f; ring.alpha = (255 * 0.45f).toInt(); c.drawCircle(cx, cy, 64f, ring)

        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF2A0C16.toInt() }
        c.drawCircle(cx, cy, 46f, disc)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = RED; strokeWidth = 2.2f }
        c.drawCircle(cx, cy, 46f, edge)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED_SOFT; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f; textAlign = Paint.Align.CENTER
        }
        c.drawText("ALMOST THERE", cx, 78f, label)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        // Shrink long names so they stay inside the circle.
        while (title.measureText(name) > 78f && title.textSize > 10f) title.textSize -= 1f
        c.drawText(name, cx, 78f + 24f, title)
        return bmp
    }

    private const val CYAN = 0xFF22F5E0.toInt()

    /**
     * Picture for the expanded "trip in progress" notification: a tiny map with the destination pin inside its
     * alarm ring, and you (the blue dot) on the road, closer to the ring the further along you are.
     * [progress] is 0..1 (0 = where you started, 1 = at the alarm ring).
     */
    fun radar(distLabel: String, progress: Float, alarmLabel: String): Bitmap {
        val w = 960
        val h = 436
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(w / 330f, w / 330f)
        c.drawColor(0xFF0E141C.toInt())

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B2634.toInt(); strokeWidth = 1f }
        for (x in floatArrayOf(55f, 110f, 165f, 220f, 275f)) c.drawLine(x, 0f, x, 150f, grid)
        for (y in floatArrayOf(37f, 75f, 112f)) c.drawLine(0f, y, 330f, y, grid)

        // The road
        val road = android.graphics.Path().apply {
            moveTo(-10f, 126f); lineTo(90f, 96f); lineTo(210f, 92f); lineTo(340f, 70f)
        }
        c.drawPath(road, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFF2A2145.toInt(); strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })

        // The alarm ring around the destination
        val cx = 270f
        val cy = 72f
        c.drawCircle(cx, cy, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = withAlpha(CYAN, 0.12f) })
        c.drawCircle(cx, cy, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = CYAN; strokeWidth = 2.2f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(7f, 6f), 0f)
        })

        // The destination pin, tip on the ring's centre
        c.save()
        c.translate(cx, cy)
        c.scale(0.55f, 0.55f)
        c.translate(-125f, -126.3f)
        val pin = android.graphics.Path().apply {
            moveTo(125f, 62f)
            cubicTo(112.6f, 62f, 102.5f, 72.1f, 102.5f, 84.5f)
            cubicTo(102.5f, 101.4f, 125f, 126.3f, 125f, 126.3f)
            cubicTo(125f, 126.3f, 147.5f, 101.4f, 147.5f, 84.5f)
            cubicTo(147.5f, 72.1f, 137.4f, 62f, 125f, 62f)
            close()
        }
        c.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFF2EC4.toInt() })
        c.drawCircle(125f, 84.5f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE })
        c.restore()

        // Ring label
        c.drawText(alarmLabel, cx, cy + 50f + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CYAN; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        })

        // You: walk along the road from the start to the ring's edge
        val (yx, yy) = alongRoad(floatArrayOf(46f, 109.2f, 90f, 96f, 210f, 92f, 220f, 90.3f), progress.coerceIn(0f, 1f))
        c.drawCircle(yx, yy, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = withAlpha(0xFF3B82F6.toInt(), 0.25f) })
        c.drawCircle(yx, yy, 6.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF3B82F6.toInt() })
        c.drawCircle(yx, yy, 6.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 2f })

        // Distance bubble above you
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CYAN; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val bw = text.measureText(distLabel) + 26f
        val bx = (yx).coerceIn(bw / 2 + 6f, 330f - bw / 2 - 6f)
        val by = (yy - 34f).coerceAtLeast(22f)
        val r = android.graphics.RectF(bx - bw / 2, by - 15f, bx + bw / 2, by + 15f)
        c.drawRoundRect(r, 15f, 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xD90B0F14.toInt() })
        c.drawRoundRect(r, 15f, 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF222E3D.toInt(); strokeWidth = 1f })
        c.drawText(distLabel, bx, by + 5.5f, text)
        return bmp
    }

    /**
     * The trip-in-progress notification's bar: a faint track, a glowing neon fill for how far along you are
     * ([fraction] 0..1, or null for none yet), and a white tick where the alarm rings ([tick] 0..1).
     */
    fun progressBar(fraction: Float?, tick: Float): Bitmap {
        val w = 900
        val h = 36
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = 6f
        c.drawRoundRect(android.graphics.RectF(0f, 12f, w.toFloat(), 24f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
        })
        if (fraction != null && fraction > 0f) {
            val fillW = (w * fraction.coerceIn(0f, 1f)).coerceAtLeast(2 * r)
            c.drawRoundRect(android.graphics.RectF(0f, 10f, fillW, 26f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(CYAN, 0.6f)
                maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            })
            c.drawRoundRect(android.graphics.RectF(0f, 12f, fillW, 24f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN })
        }
        val x = w * tick.coerceIn(0f, 1f)
        c.drawRoundRect(android.graphics.RectF(x - 2f, 5f, x + 2f, 31f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return bmp
    }

    /** The point [t] (0..1) of the way along a polyline given as x0,y0,x1,y1,… */
    private fun alongRoad(pts: FloatArray, t: Float): Pair<Float, Float> {
        val n = pts.size / 2
        var total = 0f
        for (i in 1 until n) total += kotlin.math.hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
        var left = total * t
        for (i in 1 until n) {
            val dx = pts[2 * i] - pts[2 * i - 2]
            val dy = pts[2 * i + 1] - pts[2 * i - 1]
            val len = kotlin.math.hypot(dx, dy)
            if (left <= len || i == n - 1) {
                val f = if (len == 0f) 0f else (left / len).coerceIn(0f, 1f)
                return Pair(pts[2 * i - 2] + dx * f, pts[2 * i - 1] + dy * f)
            }
            left -= len
        }
        return Pair(pts[0], pts[1])
    }

    /** Small round icon for the collapsed notification: a pin in a dark red circle. */
    fun icon(ctx: Context): Bitmap {
        val s = 192
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val half = s / 2f
        c.drawCircle(half, half, half, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A0F1B.toInt() })
        c.drawCircle(half, half, half - 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = RED; strokeWidth = 8f
        })
        ContextCompat.getDrawable(ctx, R.drawable.ic_pin)?.mutate()?.let { d ->
            d.setTint(RED)
            val p = (s * 0.26f).toInt()
            d.setBounds(p, p, s - p, s - p)
            d.draw(c)
        }
        return bmp
    }

    private fun withAlpha(color: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
