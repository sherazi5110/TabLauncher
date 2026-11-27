package com.tablauncher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Original built-in wallpapers, drawn in code so they look sharp at any size. Each has a light and dark version. */
object WallpaperRenderer {
    data class Builtin(val id: String, val name: String)

    val builtins = listOf(
        Builtin("waves", "Waves"),
        Builtin("aurora", "Aurora"),
        Builtin("dunes", "Dunes"),
        Builtin("ocean", "Ocean"),
        Builtin("graphite", "Graphite"),
    )

    fun render(id: String, dark: Boolean, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        when (id) {
            "aurora" -> aurora(c, w, h, dark)
            "dunes" -> dunes(c, w, h, dark)
            "ocean" -> ocean(c, w, h, dark)
            "graphite" -> graphite(c, w, h, dark)
            else -> waves(c, w, h, dark)
        }
        return b
    }

    private fun bg(c: Canvas, w: Int, h: Int, a: Int, b: Int, diagonal: Boolean = true) {
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, if (diagonal) w.toFloat() else 0f, h.toFloat(), a, b, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun col(hex: Long) = hex.toInt()

    private fun waves(c: Canvas, w: Int, h: Int, dark: Boolean) {
        if (dark) bg(c, w, h, col(0xFF090D1C), col(0xFF160E2A)) else bg(c, w, h, col(0xFFE8F1FF), col(0xFFFCEFF7))
        val waves = if (dark) listOf(
            0xFF1B2D6B to 0xFF13204D, 0xFF3A1F78 to 0xFF281556, 0xFF6B1D57 to 0xFF4C143E,
            0xFF7A2E1D to 0xFF561F13, 0xFF0F4E5C to 0xFF0A3640,
        ) else listOf(
            0xFFBFE0FF to 0xFFA6D1FF, 0xFFD8CCFF to 0xFFC5B5FF, 0xFFFFC9E2 to 0xFFFFB3D5,
            0xFFFFD9B8 to 0xFFFFC89C, 0xFFC6F0E2 to 0xFFA9E6D1,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        waves.forEachIndexed { i, (a, b) ->
            val base = h * (0.18f + i * 0.17f)
            val amp = h * 0.05f
            val f = 1.4 + i * 0.15
            val ph = i * 1.3
            val path = Path().apply {
                moveTo(0f, h.toFloat())
                var x = 0f
                while (x <= w + 8) {
                    val y = base + amp * sin(x / w * PI * f + ph).toFloat() - (x / w) * h * 0.12f
                    lineTo(x, y)
                    x += 8f
                }
                lineTo(w.toFloat(), h.toFloat())
                close()
            }
            paint.shader = LinearGradient(0f, base - amp, 0f, h.toFloat(), col(a), col(b), Shader.TileMode.CLAMP)
            c.drawPath(path, paint)
        }
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r, p)
    }

    private fun aurora(c: Canvas, w: Int, h: Int, dark: Boolean) {
        val m = max(w, h).toFloat()
        if (dark) {
            bg(c, w, h, col(0xFF07071A), col(0xFF0F1426))
            blob(c, w * 0.15f, h * 0.2f, m * 0.55f, col(0xFF6D28D9), 0.65f)
            blob(c, w * 0.85f, h * 0.3f, m * 0.5f, col(0xFF0EA5E9), 0.5f)
            blob(c, w * 0.6f, h * 0.9f, m * 0.55f, col(0xFFDB2777), 0.45f)
            blob(c, w * 0.1f, h * 0.95f, m * 0.4f, col(0xFF10B981), 0.35f)
        } else {
            bg(c, w, h, col(0xFFF6F2FF), col(0xFFEAF3FF))
            blob(c, w * 0.15f, h * 0.2f, m * 0.55f, col(0xFFFFB8D0), 0.75f)
            blob(c, w * 0.85f, h * 0.25f, m * 0.5f, col(0xFFB9D7FF), 0.8f)
            blob(c, w * 0.65f, h * 0.9f, m * 0.55f, col(0xFFC9F2E3), 0.8f)
            blob(c, w * 0.1f, h * 0.9f, m * 0.45f, col(0xFFFFE3B3), 0.8f)
            blob(c, w * 0.5f, h * 0.5f, m * 0.35f, col(0xFFD6C8FF), 0.6f)
        }
    }

    private fun dunes(c: Canvas, w: Int, h: Int, dark: Boolean) {
        if (dark) bg(c, w, h, col(0xFF1B1033), col(0xFF3B1F4A), diagonal = false)
        else bg(c, w, h, col(0xFFFFE9D6), col(0xFFFFCFB5), diagonal = false)
        val sun = if (dark) col(0xFFF5E6FF) else col(0xFFFFF3C4)
        blob(c, w * 0.72f, h * 0.3f, h * 0.22f, sun, if (dark) 0.5f else 0.9f)
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sun; alpha = if (dark) 200 else 255 }
            .let { c.drawCircle(w * 0.72f, h * 0.3f, h * 0.07f, it) }
        val hills = if (dark) listOf(0xFF5B2A55, 0xFF4A2148, 0xFF36183A, 0xFF24102A)
        else listOf(0xFFF7B58C, 0xFFEE9A72, 0xFFD97F5E, 0xFFB8644A)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        hills.forEachIndexed { i, color ->
            val y0 = h * (0.5f + i * 0.12f)
            val path = Path().apply {
                moveTo(0f, y0 + (if (i % 2 == 0) h * 0.06f else -h * 0.02f))
                cubicTo(w * 0.3f, y0 - h * 0.12f, w * 0.6f, y0 + h * 0.1f, w.toFloat(), y0 - h * (0.04f + 0.02f * i))
                lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
            }
            p.color = col(color)
            c.drawPath(path, p)
        }
    }

    private fun ocean(c: Canvas, w: Int, h: Int, dark: Boolean) {
        if (dark) bg(c, w, h, col(0xFF020617), col(0xFF0C4A6E), diagonal = false)
        else bg(c, w, h, col(0xFFE0F7FF), col(0xFF7CC7F2), diagonal = false)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 6) {
            val base = h * (0.45f + i * 0.1f)
            val path = Path().apply {
                moveTo(0f, h.toFloat())
                var x = 0f
                while (x <= w + 8) {
                    lineTo(x, base + h * 0.025f * sin(x / w * PI * (2.0 + i * 0.4) + i).toFloat())
                    x += 8f
                }
                lineTo(w.toFloat(), h.toFloat()); close()
            }
            p.color = if (dark) Color.argb(40 + i * 10, 56, 189, 248) else Color.argb(40 + i * 12, 14, 116, 199)
            c.drawPath(path, p)
        }
    }

    private fun graphite(c: Canvas, w: Int, h: Int, dark: Boolean) {
        val m = max(w, h).toFloat()
        if (dark) {
            bg(c, w, h, col(0xFF000000), col(0xFF1C1C1E))
            blob(c, w * 0.8f, h * 0.2f, m * 0.5f, col(0xFF3A3A3C), 0.6f)
            blob(c, w * 0.2f, h * 0.9f, m * 0.45f, col(0xFF2C2C2E), 0.6f)
        } else {
            bg(c, w, h, col(0xFFF5F5F7), col(0xFFD1D1D6))
            blob(c, w * 0.8f, h * 0.2f, m * 0.5f, col(0xFFFFFFFF), 0.9f)
            blob(c, w * 0.2f, h * 0.85f, m * 0.45f, col(0xFFC7C7CC), 0.6f)
        }
    }
}
