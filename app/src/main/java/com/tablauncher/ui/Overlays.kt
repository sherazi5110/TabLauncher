package com.tablauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.LauncherApps
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tablauncher.data.AppInfo
import com.tablauncher.data.HomeItem
import com.tablauncher.data.LayoutStore
import com.tablauncher.data.Settings
import com.tablauncher.data.WidgetArea
import com.tablauncher.data.WidgetType
import com.tablauncher.ui.widgets.WidgetCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SheetShape = RoundedCornerShape(32.dp)

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Sheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier
            .shadow(24.dp, SheetShape)
            .clip(SheetShape)
            .background(c.card)
            .clickable(interactionSource = null, indication = null) {}
            .padding(28.dp)
    ) { content() }
}

// ---------- folders ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderOverlay(fid: String) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val layout by ctl.app.layout.layout.collectAsState()
    val byKey by ctl.app.apps.byKey.collectAsState()
    val folder = layout.home.firstOrNull { it is HomeItem.Folder && it.fid == fid } as? HomeItem.Folder
    if (folder == null) {
        LaunchedEffect(Unit) { ctl.openFolder = null }
        return
    }
    Scrim({ ctl.openFolder = null }) {
        Sheet(Modifier.fillMaxWidth(0.62f).heightIn(max = 640.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ctl.renameFolder = fid }) {
                    Text(folder.name, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.Edit, "Rename", tint = c.text2, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(20.dp))
                FlowRow(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    folder.apps.mapNotNull { byKey[it] }.forEach { app -> FolderAppCell(fid, app) }
                }
            }
        }
    }
}

@Composable
private fun FolderAppCell(fid: String, app: AppInfo) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val haptic = LocalHapticFeedback.current
    val holder = remember(app.key) { CoordsHolder() }
    IconCell(app.label, 68.dp, c.text, Color.Transparent, Modifier.width(104.dp), showLabel = true) {
        AppIconImage(app, 68.dp, Modifier.onGloballyPositioned { holder.coords = it }.launcherGestures(
            app.key, ctl,
            onTap = { ctl.launch(app, holder.bounds()) },
            onLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                holder.bounds()?.let { ctl.openMenu(MenuTarget.FolderApp(fid, app), it) }
            },
            onDragStart = { id, local -> holder.coords?.let { ctl.startDrag(DragItem.FolderApp(fid, app.key), id, it.localToRoot(local)) } },
        ))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryOverlay(view: CategoryView) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val byKey by ctl.app.apps.byKey.collectAsState()
    val haptic = LocalHapticFeedback.current
    Scrim({ ctl.openCategory = null }) {
        Sheet(Modifier.fillMaxWidth(0.62f).heightIn(max = 680.dp)) {
            Column {
                Text(view.title, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                FlowRow(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    view.keys.mapNotNull { byKey[it] }.forEach { app ->
                        val holder = remember(app.key) { CoordsHolder() }
                        IconCell(app.label, 68.dp, c.text, Color.Transparent, Modifier.width(104.dp), showLabel = true) {
                            AppIconImage(app, 68.dp, Modifier.onGloballyPositioned { holder.coords = it }.launcherGestures(
                                app.key, ctl,
                                onTap = { ctl.launch(app, holder.bounds()) },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    holder.bounds()?.let { ctl.openMenu(MenuTarget.LibraryApp(app), it) }
                                },
                            ))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameFolderDialog(fid: String) {
    val ctl = LocalController.current
    val folder = ctl.app.layout.current.home.firstOrNull { it is HomeItem.Folder && it.fid == fid } as? HomeItem.Folder
    var name by remember(fid) { mutableStateOf(folder?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = { ctl.renameFolder = null },
        title = { Text("Rename folder") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = {
            TextButton({
                ctl.app.layout.renameFolder(fid, name.trim().ifEmpty { "Folder" })
                ctl.renameFolder = null
            }) { Text("Save") }
        },
        dismissButton = { TextButton({ ctl.renameFolder = null }) { Text("Cancel") } },
    )
}

// ---------- long-press menu ----------

private data class MenuEntry(val label: String, val icon: ImageVector?, val image: ImageBitmap? = null, val danger: Boolean = false, val action: () -> Unit)

@Composable
fun MenuPopup(state: MenuState) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val layout = ctl.app.layout
    val settings = ctl.app.settings
    val apps = ctl.app.apps
    val close = { ctl.menu = null }

    val shortcutEntries = state.shortcuts.map { sc ->
        val img = remember(sc.id) {
            runCatching {
                ctx.getSystemService(LauncherApps::class.java).getShortcutIconDrawable(sc, ctx.resources.displayMetrics.densityDpi)
                    ?.toBitmap(64, 64)?.asImageBitmap()
            }.getOrNull()
        }
        MenuEntry((sc.shortLabel ?: sc.longLabel ?: "").toString(), Icons.AutoMirrored.Rounded.Launch, img) { apps.startShortcut(sc); close() }
    }

    fun appEntries(app: AppInfo): List<MenuEntry> = listOfNotNull(
        MenuEntry("App info", Icons.Rounded.Info) { apps.appInfo(app); close() },
        if (!app.isSystem) MenuEntry("Uninstall", Icons.Rounded.Delete, danger = true) { apps.uninstall(app); close() } else null,
    )

    val entries: List<MenuEntry> = shortcutEntries + when (val t = state.target) {
        is MenuTarget.HomeApp -> listOf(
            MenuEntry("Add to Dock", Icons.Rounded.AddCircleOutline) { layout.addToDock(t.app.key); close() },
            MenuEntry("Remove from Home", Icons.Rounded.RemoveCircleOutline) { layout.removeHomeItem(t.item.id); close() },
            MenuEntry("Customize Home Screen", Icons.Rounded.Edit) { close(); ctl.customizeOpen = true },
        ) + appEntries(t.app)
        is MenuTarget.HomeFolder -> listOf(
            MenuEntry("Rename", Icons.Rounded.DriveFileRenameOutline) { close(); ctl.renameFolder = t.folder.fid },
            MenuEntry("Ungroup folder", Icons.Rounded.FolderOff) { layout.ungroupFolder(t.folder.fid); close() },
            MenuEntry("Remove from Home", Icons.Rounded.RemoveCircleOutline) { layout.removeHomeItem(t.folder.id); close() },
            MenuEntry("Customize Home Screen", Icons.Rounded.Edit) { close(); ctl.customizeOpen = true },
        )
        is MenuTarget.DockApp -> listOf(
            MenuEntry("Remove from Dock", Icons.Rounded.RemoveCircleOutline) { layout.removeFromDock(t.app.key); close() },
        ) + appEntries(t.app)
        is MenuTarget.FolderApp -> listOf(
            MenuEntry("Remove from folder", Icons.Rounded.RemoveCircleOutline) { layout.removeFromFolder(t.fid, t.app.key); close() },
        ) + appEntries(t.app)
        is MenuTarget.LibraryApp -> {
            val l = layout.current
            listOfNotNull(
                if (t.app.key !in l.homeKeys()) MenuEntry("Add to Home Screen", Icons.Rounded.Add) {
                    layout.addToHome(t.app.key); close()
                } else null,
                if (t.app.key !in l.dock) MenuEntry("Add to Dock", Icons.Rounded.AddCircleOutline) { layout.addToDock(t.app.key); close() } else null,
                MenuEntry("Hide app", Icons.Rounded.VisibilityOff) {
                    settings.set(Settings.HIDDEN, settings.current.hidden + t.app.key)
                    layout.update { it.copy(home = it.home.filterNot { h -> h is HomeItem.App && h.key == t.app.key }, dock = it.dock - t.app.key) }
                    close()
                },
            ) + appEntries(t.app)
        }
    }
    val title = when (val t = state.target) {
        is MenuTarget.HomeApp -> t.app.label
        is MenuTarget.HomeFolder -> t.folder.name
        is MenuTarget.DockApp -> t.app.label
        is MenuTarget.FolderApp -> t.app.label
        is MenuTarget.LibraryApp -> t.app.label
    }

    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { close() }) {
        Layout(content = {
            Column(
                Modifier
                    .width(270.dp)
                    .shadow(18.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.card)
                    .clickable(interactionSource = null, indication = null) {}
                    .padding(vertical = 6.dp)
            ) {
                Text(title, color = c.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                val folderTarget = state.target as? MenuTarget.HomeFolder
                if (folderTarget != null) {
                    val f = folderTarget.folder
                    Text("Size", color = c.text, fontSize = 15.5.sp, modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 4 to 2, 3 to 3, 4 to 3).forEach { (w, h) ->
                            val on = f.w == w && f.h == h
                            Text("$w×$h", color = if (on) Color.White else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) c.accent else c.card2)
                                    .clickable { layout.resizeFolder(f.fid, w, h); close() }
                                    .padding(horizontal = 11.dp, vertical = 7.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.line))
                }
                entries.forEachIndexed { i, e ->
                    if (i == shortcutEntries.size && i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.line))
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = e.action).padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.label, color = if (e.danger) Color(0xFFFF3B30) else c.text, fontSize = 15.5.sp, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.image != null) Image(e.image, null, Modifier.size(22.dp))
                        else if (e.icon != null) Icon(e.icon, null, tint = if (e.danger) Color(0xFFFF3B30) else c.text2, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }) { measurables, constraints ->
            val p = measurables[0].measure(Constraints(maxHeight = constraints.maxHeight))
            val margin = 16.dp.roundToPx()
            val a = state.anchor
            val x = a.left.toInt().coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
            val below = a.bottom.toInt() + 10
            val y = if (below + p.height < constraints.maxHeight - margin) below
            else (a.top.toInt() - 10 - p.height).coerceAtLeast(margin)
            layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
        }
    }
}

// ---------- widget picker ----------

private data class ProviderEntry(val info: AppWidgetProviderInfo, val label: String, val appLabel: String, val preview: ImageBitmap?)

@Composable
fun WidgetPicker(area: WidgetArea) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val providers by produceState<List<ProviderEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            ctl.app.widgets.providers().mapNotNull { info ->
                runCatching {
                    val appLabel = pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                    val preview = (info.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi)
                        ?: info.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi))
                        ?.let { d ->
                            val w = d.intrinsicWidth.coerceIn(1, 480)
                            val h = d.intrinsicHeight.coerceIn(1, 480)
                            d.toBitmap(w, h).asImageBitmap()
                        }
                    ProviderEntry(info, info.loadLabel(pm) ?: appLabel, appLabel, preview)
                }.getOrNull()
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }
    }
    val existing = ctl.app.layout.current.widgets(area).map { it.type }.toSet()
    Scrim({ ctl.widgetPicker = null }) {
        Sheet(Modifier.fillMaxWidth(0.62f).fillMaxSize(0.85f)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (area == WidgetArea.HOME) "Add to Home Screen" else "Add to Widgets page", color = c.text, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Close, "Close", tint = c.text2, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(15.dp))
                        .clickable { ctl.widgetPicker = null })
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { Text("TABLAUNCHER WIDGETS", color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    items(WidgetType.entries.filter { it != WidgetType.APP_WIDGET }) { t ->
                        PickerRow(t.title, t.description + if (t in existing) " · already added" else "", null, Icons.Rounded.Widgets) {
                            ctl.app.layout.addWidget(area, LayoutStore.builtin(t))
                            ctl.widgetPicker = null
                        }
                    }
                    item { Text("ANDROID WIDGETS", color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                    val list = providers
                    if (list == null) item { Text("Loading…", color = c.text2) }
                    else items(list, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { p ->
                        PickerRow(p.label, p.appLabel, p.preview, Icons.Rounded.Widgets) {
                            ctl.widgetPicker = null
                            ctl.addAppWidget(p.info, area)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, preview: ImageBitmap?, icon: ImageVector, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card2).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)).background(c.card), contentAlignment = Alignment.Center) {
            if (preview != null) Image(preview, null, Modifier.fillMaxSize().padding(4.dp))
            else Icon(icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.Add, "Add", tint = c.accent)
    }
}

// ---------- drag shadow ----------

@Composable
fun DragShadow(item: DragItem, size: androidx.compose.ui.unit.Dp) {
    val ctl = LocalController.current
    val byKey by ctl.app.apps.byKey.collectAsState()
    Box(Modifier.shadow(16.dp, LocalIconShape.current)) {
        when (item) {
            is DragItem.Home -> when (val h = item.item) {
                is HomeItem.App -> AppIconImage(byKey[h.key], size)
                is HomeItem.Folder -> FolderIcon(h, size)
            }
            is DragItem.Dock -> AppIconImage(byKey[item.key], size)
            is DragItem.FolderApp -> AppIconImage(byKey[item.key], size)
        }
    }
}
