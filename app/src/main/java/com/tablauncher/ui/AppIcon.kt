package com.tablauncher.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablauncher.data.AppInfo
import com.tablauncher.data.HomeItem
import com.tablauncher.data.IconShape
import com.tablauncher.data.IconSpec
import com.tablauncher.data.ResolvedIconStyle
import com.tablauncher.data.Shapes

val LocalController = staticCompositionLocalOf<LauncherController> { error("No controller") }
val LocalIconSpec = staticCompositionLocalOf { IconSpec(IconShape.SQUIRCLE, ResolvedIconStyle.LIGHT, 0) }
val LocalIconShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(22) }
val LocalLabelScale = staticCompositionLocalOf { 1f }
val LocalShowLabels = staticCompositionLocalOf { true }

fun composeShape(shape: IconShape): Shape = GenericShape { size, _ ->
    val s = minOf(size.width, size.height)
    val p = Shapes.path(shape, s)
    addPath(p.asComposePath(), Offset((size.width - s) / 2, (size.height - s) / 2))
}

@Composable
fun rememberAppIcon(app: AppInfo?): ImageBitmap? {
    val c = LocalController.current
    val spec = LocalIconSpec.current
    val version by c.app.apps.iconVersion.collectAsState()
    if (app == null) return null
    val state = produceState(c.app.apps.peekIcon(app.key, spec), app.key, spec, version) {
        value = c.app.apps.loadIcon(app, spec)
    }
    return state.value
}

private val clockPackages = setOf(
    "com.google.android.deskclock", "com.android.deskclock", "com.zui.deskclock", "com.lenovo.deskclock",
    "com.sec.android.app.clockpackage", "com.oneplus.deskclock", "com.coloros.alarmclock", "com.android.alarmclock",
)
private val calendarPackages = setOf(
    "com.google.android.calendar", "com.android.calendar", "com.zui.calendar", "com.lenovo.calendar",
    "com.samsung.android.calendar", "com.coloros.calendar", "com.simplemobiletools.calendar.pro",
)

enum class LiveIcon { CLOCK, CALENDAR }

fun liveIconFor(app: AppInfo): LiveIcon? = when {
    app.packageName in clockPackages -> LiveIcon.CLOCK
    app.packageName in calendarPackages -> LiveIcon.CALENDAR
    app.isSystem && app.label.equals("Clock", ignoreCase = true) -> LiveIcon.CLOCK
    app.isSystem && app.label.equals("Calendar", ignoreCase = true) -> LiveIcon.CALENDAR
    else -> null
}

@Composable
fun AppIconImage(app: AppInfo?, size: Dp, modifier: Modifier = Modifier) {
    when (app?.let(::liveIconFor)) {
        LiveIcon.CLOCK -> { LiveClockIcon(size, modifier); return }
        LiveIcon.CALENDAR -> { LiveCalendarIcon(size, modifier); return }
        null -> {}
    }
    val bmp = rememberAppIcon(app)
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = app?.label, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).clip(LocalIconShape.current).background(LocalColors.current.card2))
    }
}

@Composable
fun FolderIcon(folder: HomeItem.Folder, size: Dp, modifier: Modifier = Modifier) {
    val byKey by LocalController.current.app.apps.byKey.collectAsState()
    val pad = size * 0.12f
    val gap = size * 0.05f
    val mini = (size - pad * 2 - gap * 2) / 3
    val apps = folder.apps.mapNotNull { byKey[it] }.take(9)
    Box(
        modifier
            .size(size)
            .clip(LocalIconShape.current)
            .background(LocalColors.current.folderBg)
            .padding(pad)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            apps.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { AppIconImage(it, mini) }
                }
            }
        }
    }
}

@Composable
fun IconLabel(text: String, color: Color, shadow: Color, modifier: Modifier = Modifier, maxWidth: Dp? = null) {
    val scale = LocalLabelScale.current
    Text(
        text = text,
        modifier = if (maxWidth != null) modifier.width(maxWidth) else modifier,
        color = color,
        fontSize = (12.5f * scale).sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = TextStyle(shadow = if (shadow.alpha > 0f) Shadow(shadow, Offset(0f, 1f), 4f) else null),
    )
}

/** Icon + label used on Home, in folders, search and the library. */
@Composable
fun IconCell(
    label: String,
    iconSize: Dp,
    labelColor: Color,
    labelShadow: Color,
    modifier: Modifier = Modifier,
    showLabel: Boolean = LocalShowLabels.current,
    icon: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            IconLabel(label, labelColor, labelShadow, maxWidth = iconSize * 1.6f)
        }
    }
}

/**
 * Tap, long-press (menu) and long-press-then-move (drag) handling for launcher items.
 * [onDragStart] receives the pointer id and the position local to this element.
 */
fun Modifier.launcherGestures(
    key: Any?,
    controller: LauncherController,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: ((PointerId, Offset) -> Unit)? = null,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        controller.lastItemPress = SystemClock.uptimeMillis()
        var result: Int
        try {
            result = withTimeout(viewConfiguration.longPressTimeoutMillis) {
                val up = waitForUpOrCancellation()
                if (up != null) { up.consume(); 1 } else 2
            }
        } catch (e: PointerEventTimeoutCancellationException) {
            result = 0
        }
        when (result) {
            1 -> onTap()
            0 -> {
                // An icon inside a big folder and the folder itself both see the press: first one wins.
                val now = SystemClock.uptimeMillis()
                if (now - controller.lastLongPress < 250) return@awaitEachGesture
                controller.lastLongPress = now
                onLongPress()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) { ch.consume(); break }
                    if (onDragStart != null && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        onDragStart(down.id, ch.position)
                        break
                    }
                    ch.consume()
                }
            }
        }
    }
}

/**
 * Long-press anywhere on a widget to edit widgets. Watches touches before the widget's own buttons
 * see them, and only takes over once the press has lasted long enough.
 */
fun Modifier.markItemPress(controller: LauncherController, onLongPress: () -> Unit = {}): Modifier = pointerInput(Unit) {
    val initial = androidx.compose.ui.input.pointer.PointerEventPass.Initial
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = initial)
        controller.lastItemPress = SystemClock.uptimeMillis()
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!ch.pressed || ch.isConsumed) return@withTimeoutOrNull true
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (released == null) {
            onLongPress()
            // Swallow the rest of this touch so buttons inside the widget don't fire.
            while (true) {
                val ev = awaitPointerEvent(initial)
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
    }
}

@Composable
fun rememberIconShape(shape: IconShape): Shape = remember(shape) { composeShape(shape) }

/** Clock app icon that shows the real time. */
@Composable
fun LiveClockIcon(size: Dp, modifier: Modifier = Modifier) {
    val dark = LocalIconSpec.current.style != ResolvedIconStyle.LIGHT
    val now by produceState(java.time.LocalTime.now()) {
        while (true) {
            value = java.time.LocalTime.now()
            kotlinx.coroutines.delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val bg = if (dark) Color(0xFF1C1C1F) else Color.White
    val face = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    androidx.compose.foundation.Canvas(modifier.size(size).clip(LocalIconShape.current).background(bg)) {
        val c = center
        val r = this.size.minDimension * 0.42f
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val inner = if (i % 3 == 0) r * 0.78f else r * 0.84f
            drawLine(face.copy(alpha = if (i % 3 == 0) 1f else 0.5f),
                Offset(c.x + inner * kotlin.math.sin(a).toFloat(), c.y - inner * kotlin.math.cos(a).toFloat()),
                Offset(c.x + r * 0.93f * kotlin.math.sin(a).toFloat(), c.y - r * 0.93f * kotlin.math.cos(a).toFloat()),
                strokeWidth = r * 0.05f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        fun hand(angleDeg: Double, len: Float, width: Float, color: Color) {
            val a = Math.toRadians(angleDeg)
            drawLine(color, c, Offset(c.x + len * kotlin.math.sin(a).toFloat(), c.y - len * kotlin.math.cos(a).toFloat()),
                strokeWidth = width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        val sec = now.second
        val min = now.minute + sec / 60.0
        val hr = (now.hour % 12) + min / 60.0
        hand(hr * 30, r * 0.5f, r * 0.1f, face)
        hand(min * 6, r * 0.75f, r * 0.07f, face)
        hand(sec * 6.0, r * 0.82f, r * 0.03f, Color(0xFFFF9F0A))
        drawCircle(Color(0xFFFF9F0A), r * 0.06f, c)
    }
}

/** Calendar app icon that shows today's date. */
@Composable
fun LiveCalendarIcon(size: Dp, modifier: Modifier = Modifier) {
    val dark = LocalIconSpec.current.style != ResolvedIconStyle.LIGHT
    val today by produceState(java.time.LocalDate.now()) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = java.time.LocalDate.now()
        }
    }
    Column(
        modifier.size(size).clip(LocalIconShape.current).background(if (dark) Color(0xFF2C2C2E) else Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase(java.util.Locale.ENGLISH),
            color = Color(0xFFFF3B30), fontSize = (size.value * 0.16f).sp, fontWeight = FontWeight.SemiBold, lineHeight = (size.value * 0.18f).sp)
        Text("${today.dayOfMonth}", color = if (dark) Color.White else Color(0xFF1C1C1E), fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.Light, lineHeight = (size.value * 0.48f).sp)
    }
}
