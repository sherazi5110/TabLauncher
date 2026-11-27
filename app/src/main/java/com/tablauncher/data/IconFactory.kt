package com.tablauncher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Icon look after resolving AUTO against the current light/dark theme. */
enum class ResolvedIconStyle { LIGHT, DARK, TINTED }

object Shapes {
    /** Outline of an icon shape filling a [size] x [size] square. */
    fun path(shape: IconShape, size: Float): Path {
        val p = Path()
        when (shape) {
            IconShape.CIRCLE -> p.addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
            IconShape.ROUNDED -> p.addRoundRect(RectF(0f, 0f, size, size), size * 0.3f, size * 0.3f, Path.Direction.CW)
            IconShape.SQUARE -> p.addRoundRect(RectF(0f, 0f, size, size), size * 0.1f, size * 0.1f, Path.Direction.CW)
            IconShape.TEARDROP -> {
                val r = size / 2
                val small = size * 0.12f
                p.addRoundRect(
                    RectF(0f, 0f, size, size),
                    floatArrayOf(r, r, r, r, small, small, r, r),
                    Path.Direction.CW,
                )
            }
            IconShape.SQUIRCLE -> {
                // Superellipse |x|^n + |y|^n = 1 gives the smooth "continuous corner" look.
                val n = 4.2
                val r = size / 2.0
                val steps = 96
                for (i in 0..steps) {
                    val t = 2 * Math.PI * i / steps
                    val c = cos(t)
                    val s = sin(t)
                    val x = r + r * sign(c) * abs(c).pow(2 / n)
                    val y = r + r * sign(s) * abs(s).pow(2 / n)
                    if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                }
                p.close()
            }
        }
        return p
    }
}

/** Draws app icons in the chosen shape and style (light, dark or tinted). */
object IconFactory {

    fun render(d: Drawable, size: Int, shape: IconShape, style: ResolvedIconStyle, tint: Int): Bitmap {
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(content)
        if (d is AdaptiveIconDrawable) drawAdaptive(c, d, size, style, tint) else drawLegacy(c, d, size, style, tint)

        // Cut the content to the icon shape with smooth (anti-aliased) edges.
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        Canvas(mask).drawPath(Shapes.path(shape, size.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG))
        c.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        mask.recycle()
        return content
    }

    private fun darkBackground(c: Canvas, size: Int) {
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFF38383C.toInt(), 0xFF1C1C1F.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    }

    private fun layerBitmap(d: Drawable?, size: Int): Bitmap? {
        d ?: return null
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = size / 4 // adaptive layers are 1.5x the visible area
        d.setBounds(-inset, -inset, size + inset, size + inset)
        d.draw(Canvas(b))
        return b
    }

    private fun drawAdaptive(c: Canvas, d: AdaptiveIconDrawable, size: Int, style: ResolvedIconStyle, tint: Int) {
        val inset = size / 4
        when (style) {
            ResolvedIconStyle.LIGHT -> {
                if (d.background == null) c.drawColor(Color.WHITE)
                d.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
                d.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
            }
            ResolvedIconStyle.DARK -> {
                darkBackground(c, size)
                val fg = layerBitmap(d.foreground, size) ?: return
                val bgColor = averageColor(layerBitmap(d.background, 24))
                if (isMostlyWhite(fg)) {
                    // A white symbol on a coloured background: colour the symbol instead.
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                        colorFilter = PorterDuffColorFilter(vivid(bgColor), PorterDuff.Mode.SRC_IN)
                    }
                    c.drawBitmap(fg, 0f, 0f, paint)
                } else {
                    c.drawBitmap(fg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                fg.recycle()
            }
            ResolvedIconStyle.TINTED -> {
                darkBackground(c, size)
                val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
                if (mono != null) {
                    val b = layerBitmap(mono, size)!!
                    c.drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                        colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
                    })
                    b.recycle()
                } else {
                    val fg = layerBitmap(d.foreground, size) ?: return
                    c.drawBitmap(fg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = tintFilter(tint) })
                    fg.recycle()
                }
            }
        }
    }

    private fun drawLegacy(c: Canvas, d: Drawable, size: Int, style: ResolvedIconStyle, tint: Int) {
        val full = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(full))

        // Find the visible part of the icon and how "solid" it is.
        var minX = size; var minY = size; var maxX = -1; var maxY = -1
        val px = IntArray(size * size)
        full.getPixels(px, 0, size, 0, 0, size, size)
        for (y in 0 until size) for (x in 0 until size) {
            if ((px[y * size + x] ushr 24) > 40) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) { full.recycle(); return }
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        var opaque = 0
        for (y in minY..maxY) for (x in minX..maxX) if ((px[y * size + x] ushr 24) > 200) opaque++
        val fill = opaque.toFloat() / (bw * bh)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (style == ResolvedIconStyle.TINTED) paint.colorFilter = tintFilter(tint)
        val src = android.graphics.Rect(minX, minY, maxX + 1, maxY + 1)

        if (fill > 0.7f && style != ResolvedIconStyle.TINTED) {
            // The icon is already a solid shape (circle, rounded square…): stretch it to fill our
            // shape, and paint the corners with the colour of its edge so nothing looks cut off.
            fun at(x: Int, y: Int) = px[y.coerceIn(0, size - 1) * size + x.coerceIn(0, size - 1)]
            val ix = (bw * 0.08f).toInt(); val iy = (bh * 0.08f).toInt()
            val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
            val edge = listOf(at(cx, minY + iy), at(cx, maxY - iy), at(minX + ix, cy), at(maxX - ix, cy))
                .filter { (it ushr 24) > 200 }
            if (edge.isNotEmpty()) {
                c.drawColor(Color.rgb(edge.map { Color.red(it) }.average().toInt(),
                    edge.map { Color.green(it) }.average().toInt(), edge.map { Color.blue(it) }.average().toInt()))
            }
            val over = if (fill > 0.88f) size * 0.03f else 0f // trim the old rim of square-ish icons
            c.drawBitmap(full, src, RectF(-over, -over, size + over, size + over), paint)
            if (style == ResolvedIconStyle.DARK) c.drawColor(Color.argb(70, 0, 0, 0))
        } else {
            // A symbol without its own background: centre it on a plain tile.
            if (style == ResolvedIconStyle.LIGHT) c.drawColor(Color.WHITE) else darkBackground(c, size)
            val scale = size * 0.68f / maxOf(bw, bh)
            val w = bw * scale; val h = bh * scale
            c.drawBitmap(full, src, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), paint)
        }
        full.recycle()
    }

    private fun tintFilter(tint: Int): ColorMatrixColorFilter {
        // Grey-scale, brighten, then multiply by the tint colour.
        val m = ColorMatrix().apply { setSaturation(0f) }
        val boost = 1.35f
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    boost * Color.red(tint) / 255f, 0f, 0f, 0f, 0f,
                    0f, boost * Color.green(tint) / 255f, 0f, 0f, 0f,
                    0f, 0f, boost * Color.blue(tint) / 255f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return ColorMatrixColorFilter(m)
    }

    private fun averageColor(b: Bitmap?): Int {
        b ?: return 0xFF8E8E93.toInt()
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (x in 0 until b.width) for (y in 0 until b.height) {
            val p = b.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++
        }
        b.recycle()
        if (n == 0L) return 0xFF8E8E93.toInt()
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun isMostlyWhite(b: Bitmap): Boolean {
        var opaque = 0
        var white = 0
        val hsv = FloatArray(3)
        val step = (b.width / 48).coerceAtLeast(1)
        var x = 0
        while (x < b.width) {
            var y = 0
            while (y < b.height) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) > 128) {
                    opaque++
                    Color.colorToHSV(p, hsv)
                    if (hsv[1] < 0.18f && hsv[2] > 0.75f) white++
                }
                y += step
            }
            x += step
        }
        return opaque > 0 && white.toFloat() / opaque > 0.8f
    }

    private fun vivid(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < 0.2f) return 0xFFE5E5EA.toInt()
        hsv[1] = hsv[1].coerceAtLeast(0.55f)
        hsv[2] = hsv[2].coerceAtLeast(0.9f)
        return Color.HSVToColor(hsv)
    }
}
