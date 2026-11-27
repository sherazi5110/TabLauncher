package com.tablauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.tablauncher.data.AppInfo
import com.tablauncher.data.HomeItem
import com.tablauncher.data.WidgetArea
import com.tablauncher.ui.widgets.ClockHeader
import com.tablauncher.ui.widgets.WidgetView

data class HomeMetrics(
    val landscape: Boolean,
    val cols: Int,
    val rows: Int,
    val showWidgets: Boolean,
    val iconSize: Dp,
    val dockIcon: Dp,
    val widgetColumnWidth: Dp,
    val hPad: Dp,
    val topPad: Dp,
    val bottomPad: Dp,
) {
    /** In portrait the widgets take the top two rows of the first page. */
    val firstRows: Int get() = if (showWidgets && !landscape) (rows - 2).coerceAtLeast(1) else rows
    val firstCap: Int get() = cols * firstRows
    val cap: Int get() = cols * rows

    fun pageCount(items: Int): Int = if (items <= firstCap) 1 else 1 + (items - firstCap + cap - 1) / cap
    fun pageStart(page: Int): Int = if (page == 0) 0 else firstCap + (page - 1) * cap
    fun pageCapacity(page: Int): Int = if (page == 0) firstCap else cap
}

/** Holds a node's latest coordinates without causing recomposition. */
class CoordsHolder {
    var coords: LayoutCoordinates? = null
    fun bounds(): Rect? = coords?.takeIf { it.isAttached }?.let { Rect(it.positionInRoot(), it.size.toSize()) }
}

@Composable
fun HomePage(index: Int, m: HomeMetrics, home: List<HomeItem>, apps: Map<String, AppInfo>) {
    val start = m.pageStart(index)
    val cap = m.pageCapacity(index)
    val items = if (start < home.size) home.subList(start, (start + cap).coerceAtMost(home.size)) else emptyList()
    Box(Modifier.fillMaxSize().padding(start = m.hPad, end = m.hPad, top = m.topPad, bottom = m.bottomPad)) {
        if (index == 0 && m.showWidgets) {
            if (m.landscape) {
                Row(Modifier.fillMaxSize()) {
                    HomeWidgetColumn(Modifier.width(m.widgetColumnWidth).fillMaxHeight())
                    Spacer(Modifier.width(24.dp))
                    HomeGrid(index, start, m.cols, m.firstRows, items, apps, m, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    HomeWidgetRow(Modifier.fillMaxWidth().weight(2f))
                    HomeGrid(index, start, m.cols, m.firstRows, items, apps, m, Modifier.fillMaxWidth().weight(m.firstRows.toFloat()))
                }
            }
        } else {
            HomeGrid(index, start, m.cols, m.rows, items, apps, m, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HomeWidgetColumn(modifier: Modifier) {
    val ctl = LocalController.current
    val layout by ctl.app.layout.layout.collectAsState()
    Column(modifier.verticalScroll(rememberScrollState())) {
        ClockHeader(76.dp, Modifier.padding(start = 8.dp, top = 6.dp, bottom = 14.dp))
        layout.homeWidgets.forEach { w ->
            key(w.id) {
                WidgetView(w, WidgetArea.HOME, Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
            }
        }
        if (ctl.editWidgets) AddWidgetButton(WidgetArea.HOME)
    }
}

@Composable
private fun HomeWidgetRow(modifier: Modifier) {
    val ctl = LocalController.current
    val layout by ctl.app.layout.layout.collectAsState()
    Column(modifier) {
        ClockHeader(68.dp, Modifier.padding(start = 8.dp, top = 6.dp, bottom = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            layout.homeWidgets.forEach { w ->
                key(w.id) { WidgetView(w, WidgetArea.HOME, Modifier.width(300.dp), compact = true) }
            }
            if (ctl.editWidgets) AddWidgetButton(WidgetArea.HOME)
        }
    }
}

@Composable
fun AddWidgetButton(area: WidgetArea, modifier: Modifier = Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .clickable { ctl.widgetPicker = area }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accent)
        Spacer(Modifier.width(6.dp))
        Text("Add widget", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun DoneEditingButton(modifier: Modifier = Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.accent)
            .clickable { ctl.editWidgets = false }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, null, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun HomeGrid(
    pageIndex: Int,
    start: Int,
    cols: Int,
    rows: Int,
    items: List<HomeItem>,
    apps: Map<String, AppInfo>,
    m: HomeMetrics,
    modifier: Modifier,
) {
    val ctl = LocalController.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.onGloballyPositioned { coords ->
            ctl.drag.grids[pageIndex] = GridInfo(Rect(coords.positionInRoot(), coords.size.toSize()), cols, rows, start, cols * rows)
        }
    ) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        items.take(cols * rows).forEachIndexed { i, item ->
            key(item.id) {
                val col = i % cols
                val row = i / cols
                val target = with(density) { IntOffset((cellW * col).roundToPx(), (cellH * row).roundToPx()) }
                val anim by animateIntOffsetAsState(target, spring(stiffness = 500f), label = "cell")
                Box(Modifier.offset { anim }.size(cellW, cellH), contentAlignment = Alignment.Center) {
                    HomeItemCell(item, apps, m.iconSize)
                }
            }
        }
    }
}

@Composable
private fun HomeItemCell(item: HomeItem, apps: Map<String, AppInfo>, iconSize: Dp) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val haptic = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }
    val session = ctl.drag.session
    val dragging = (session?.item as? DragItem.Home)?.item?.id == item.id
    val merge = ctl.drag.mergeTarget == item.id
    val scale by animateFloatAsState(if (merge) 1.15f else 1f, label = "merge")
    when (item) {
        is HomeItem.App -> {
            val app = apps[item.key] ?: return
            IconCell(app.label, iconSize, c.onWallpaper, c.onWallpaperShadow, Modifier.alpha(if (dragging) 0f else 1f)) {
                AppIconImage(
                    app, iconSize,
                    Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .onGloballyPositioned { holder.coords = it }
                        .launcherGestures(
                            listOf(item, app.label), ctl,
                            onTap = { ctl.launch(app, holder.bounds()) },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                holder.bounds()?.let { ctl.openMenu(MenuTarget.HomeApp(app, item), it) }
                            },
                            onDragStart = { id, local ->
                                holder.coords?.let { ctl.startDrag(DragItem.Home(item), id, it.localToRoot(local)) }
                            },
                        ),
                )
            }
        }
        is HomeItem.Folder -> {
            IconCell(item.name, iconSize, c.onWallpaper, c.onWallpaperShadow, Modifier.alpha(if (dragging) 0f else 1f)) {
                FolderIcon(
                    item, iconSize,
                    Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .onGloballyPositioned { holder.coords = it }
                        .launcherGestures(
                            item, ctl,
                            onTap = { ctl.openFolder = item.fid },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                holder.bounds()?.let { ctl.openMenu(MenuTarget.HomeFolder(item), it) }
                            },
                            onDragStart = { id, local ->
                                holder.coords?.let { ctl.startDrag(DragItem.Home(item), id, it.localToRoot(local)) }
                            },
                        ),
                )
            }
        }
    }
}

// ---------- dock ----------

@Composable
fun Dock(visibility: Float, m: HomeMetrics, modifier: Modifier = Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val haptic = LocalHapticFeedback.current
    val layout by ctl.app.layout.layout.collectAsState()
    val byKey by ctl.app.apps.byKey.collectAsState()
    val usage by ctl.app.usage.usage.collectAsState()
    val s by ctl.app.settings.state.collectAsState()

    DisposableEffect(Unit) { onDispose { ctl.drag.dockRect = Rect.Zero; ctl.drag.dockSlots.clear() } }
    if (visibility < 0.02f) {
        ctl.drag.dockRect = Rect.Zero
        return
    }

    val maxPinned = if (m.landscape) 12 else 7
    val pinned = layout.dock.mapNotNull { byKey[it] }.take(maxPinned)
    val recents = if (s.dockRecents) {
        usage.entries.sortedByDescending { it.value.last }.map { it.key }
            .filter { it !in layout.dock && it !in s.hidden }
            .mapNotNull { byKey[it] }
            .take(if (m.landscape) 3 else 2)
    } else emptyList()
    val size = m.dockIcon
    val shape = RoundedCornerShape(size * 0.5f)

    Row(
        modifier
            .graphicsLayer { alpha = visibility; translationY = (1f - visibility) * 60f }
            .onGloballyPositioned { ctl.drag.dockRect = Rect(it.positionInRoot(), it.size.toSize()) }
            .shadow(if (c.dark) 0.dp else 10.dp, shape, ambientColor = Color(0x30000000), spotColor = Color(0x30000000))
            .clip(shape)
            .background(c.card)
            .padding(size * 0.24f),
        horizontalArrangement = Arrangement.spacedBy(size * 0.3f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ctl.drag.dockSlots.keys.retainAll(pinned.indices.toSet())
        pinned.forEachIndexed { i, app ->
            key(app.key) {
                val holder = remember { CoordsHolder() }
                val dragging = (ctl.drag.session?.item as? DragItem.Dock)?.key == app.key
                AppIconImage(
                    app, size,
                    Modifier
                        .alpha(if (dragging) 0.25f else 1f)
                        .onGloballyPositioned { holder.coords = it; holder.bounds()?.let { b -> ctl.drag.dockSlots[i] = b } }
                        .launcherGestures(
                            listOf(app.key, i), ctl,
                            onTap = { ctl.launch(app, holder.bounds()) },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                holder.bounds()?.let { ctl.openMenu(MenuTarget.DockApp(app), it) }
                            },
                            onDragStart = { id, local ->
                                holder.coords?.let { ctl.startDrag(DragItem.Dock(app.key), id, it.localToRoot(local)) }
                            },
                        ),
                )
            }
        }
        if (recents.isNotEmpty()) {
            Box(Modifier.width(1.dp).height(size * 0.8f).background(c.line))
            recents.forEach { app ->
                key("r" + app.key) {
                    val holder = remember { CoordsHolder() }
                    AppIconImage(
                        app, size,
                        Modifier
                            .onGloballyPositioned { holder.coords = it }
                            .launcherGestures(
                                app.key, ctl,
                                onTap = { ctl.launch(app, holder.bounds()) },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    holder.bounds()?.let { ctl.openMenu(MenuTarget.LibraryApp(app), it) }
                                },
                            ),
                    )
                }
            }
        }
        LibraryButton(size) { ctl.pageRequests.tryEmit(PageRequest.Library) }
    }
}

@Composable
fun LibraryButton(size: Dp, onClick: () -> Unit) {
    val c = LocalColors.current
    val colors = listOf(Color(0xFF4A90FF), Color(0xFF4ADE80), Color(0xFFFF9A3D), Color(0xFFFF6B8A))
    val sq = size * 0.26f
    Box(
        Modifier.size(size).clip(LocalIconShape.current).background(c.folderBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.08f)) {
            for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(size * 0.08f)) {
                for (k in 0..1) Box(Modifier.size(sq).clip(RoundedCornerShape(sq * 0.3f)).background(colors[r * 2 + k]))
            }
        }
    }
}

@Composable
fun PageDots(pages: List<Page>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    if (pages.size <= 1) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        pages.forEachIndexed { i, p ->
            val on = i == current
            val color = c.onWallpaper.copy(alpha = if (on) 1f else 0.35f)
            Box(Modifier.size(18.dp).clip(CircleShape).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                when (p) {
                    is Page.Home -> Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    is Page.Today -> Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(color))
                            Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(color))
                        }
                        Box(Modifier.width(9.5.dp).height(4.dp).clip(RoundedCornerShape(1.dp)).background(color))
                    }
                    is Page.Library -> Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(color))
                            Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(color))
                        }
                    }
                }
            }
        }
    }
}
