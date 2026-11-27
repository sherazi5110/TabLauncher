package com.tablauncher.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.tablauncher.data.WeatherKind
import com.tablauncher.data.WeatherRepo

private val Sun = Color(0xFFFFB800)
private val Moon = Color(0xFFC9B8FF)
private val Rain = Color(0xFF3B9BFF)
private val Bolt = Color(0xFFFFC21A)

/** Small hand-drawn weather icons. */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp, dark: Boolean, modifier: Modifier = Modifier) {
    val kind = WeatherRepo.kind(code)
    val cloud = if (dark) Color(0xFFC7CDD8) else Color(0xFFD5DCE8)
    val darkCloud = if (dark) Color(0xFF9AA3B2) else Color(0xFFAEB7C6)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (kind) {
            WeatherKind.CLEAR -> if (isDay) sun(Offset(s / 2, s / 2), s * 0.22f) else moon(Offset(s / 2, s / 2), s * 0.3f)
            WeatherKind.PARTLY -> {
                if (isDay) sun(Offset(s * 0.38f, s * 0.36f), s * 0.17f) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.2f)
                cloud(s, cloud, dy = s * 0.08f)
            }
            WeatherKind.CLOUDY -> cloud(s, cloud)
            WeatherKind.FOG -> for (i in 0..2) {
                drawLine(darkCloud, Offset(s * 0.2f, s * (0.38f + i * 0.14f)), Offset(s * 0.8f, s * (0.38f + i * 0.14f)), s * 0.07f, StrokeCap.Round)
            }
            WeatherKind.RAIN -> {
                cloud(s, darkCloud, dy = -s * 0.08f)
                for (i in 0..2) {
                    val x = s * (0.32f + i * 0.18f)
                    drawLine(Rain, Offset(x, s * 0.7f), Offset(x - s * 0.05f, s * 0.86f), s * 0.06f, StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                cloud(s, cloud, dy = -s * 0.08f)
                for (i in 0..2) drawCircle(Color(0xFF8EC9FF), s * 0.04f, Offset(s * (0.32f + i * 0.18f), s * (0.76f + (i % 2) * 0.06f)))
            }
            WeatherKind.STORM -> {
                cloud(s, darkCloud, dy = -s * 0.1f)
                val p = Path().apply {
                    moveTo(s * 0.52f, s * 0.58f); lineTo(s * 0.4f, s * 0.76f); lineTo(s * 0.5f, s * 0.76f)
                    lineTo(s * 0.44f, s * 0.92f); lineTo(s * 0.62f, s * 0.7f); lineTo(s * 0.52f, s * 0.7f); close()
                }
                drawPath(p, Bolt)
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    drawCircle(Sun, r, c)
    for (i in 0 until 8) rotate(i * 45f, c) {
        drawLine(Sun, Offset(c.x, c.y - r * 1.45f), Offset(c.x, c.y - r * 1.85f), r * 0.28f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val p = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c, r))
        val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.55f, c.y - r * 0.45f), r * 0.85f)) }
        op(this, cut, androidx.compose.ui.graphics.PathOperation.Difference)
    }
    drawPath(p, Moon)
}

private fun DrawScope.cloud(s: Float, color: Color, dy: Float = 0f) {
    drawCircle(color, s * 0.17f, Offset(s * 0.38f, s * 0.55f + dy))
    drawCircle(color, s * 0.22f, Offset(s * 0.56f, s * 0.48f + dy))
    drawRoundRect(color, Offset(s * 0.2f, s * 0.52f + dy), Size(s * 0.62f, s * 0.2f), CornerRadius(s * 0.1f))
}
