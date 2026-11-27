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

@Composable
fun AppIconImage(app: AppInfo?, size: Dp, modifier: Modifier = Modifier) {
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

/** Marks touches on a widget so a long-press there doesn't open Customize. */
fun Modifier.markItemPress(controller: LauncherController): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        controller.lastItemPress = SystemClock.uptimeMillis()
    }
}

@Composable
fun rememberIconShape(shape: IconShape): Shape = remember(shape) { composeShape(shape) }
