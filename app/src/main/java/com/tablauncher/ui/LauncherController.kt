package com.tablauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import com.tablauncher.TabLauncherApp
import com.tablauncher.data.AppInfo
import com.tablauncher.data.HomeItem
import com.tablauncher.data.WidgetArea
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.min

sealed class Page {
    data object Today : Page()
    data class Home(val index: Int) : Page()
    data object Library : Page()
}

sealed class PageRequest {
    data object FirstHome : PageRequest()
    data object Library : PageRequest()
    data object Today : PageRequest()
    data class Index(val index: Int) : PageRequest()
}

sealed class MenuTarget {
    data class HomeApp(val app: AppInfo, val item: HomeItem.App) : MenuTarget()
    data class HomeFolder(val folder: HomeItem.Folder) : MenuTarget()
    data class DockApp(val app: AppInfo) : MenuTarget()
    data class LibraryApp(val app: AppInfo) : MenuTarget()
    data class FolderApp(val fid: String, val app: AppInfo) : MenuTarget()
}

data class MenuState(val target: MenuTarget, val anchor: Rect, val shortcuts: List<ShortcutInfo>)

data class CategoryView(val title: String, val keys: List<String>)

sealed class DragItem {
    data class Home(val item: HomeItem) : DragItem()
    data class Dock(val key: String) : DragItem()
    data class FolderApp(val fid: String, val key: String) : DragItem()

    val appKey: String?
        get() = when (this) {
            is Home -> (item as? HomeItem.App)?.key
            is Dock -> key
            is FolderApp -> key
        }
}

data class GridInfo(val rect: Rect, val cols: Int, val rows: Int, val start: Int, val capacity: Int)

class DragSession(val item: DragItem, val pointerId: PointerId)

class DragController {
    var session by mutableStateOf<DragSession?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var mergeTarget by mutableStateOf<String?>(null)
    val grids = HashMap<Int, GridInfo>()
    var dockRect = Rect.Zero
    val dockSlots = HashMap<Int, Rect>()
    var insertIndex = -1

    internal var candidate: String? = null
    internal var candidateSince = 0L
    internal var edgeDir = 0
    internal var edgeSince = 0L

    fun reset() {
        session = null
        mergeTarget = null
        candidate = null
        edgeDir = 0
        insertIndex = -1
    }
}

/** UI state and actions shared by all launcher screens. */
class LauncherController(val app: TabLauncherApp) {
    var searchOpen by mutableStateOf(false)
    var customizeOpen by mutableStateOf(false)
    /** While customizing a wallpaper, preview this mode (true = dark) instead of the current one. */
    var previewDark by mutableStateOf<Boolean?>(null)
    var editWidgets by mutableStateOf(false)
    var openFolder by mutableStateOf<String?>(null)
    var openCategory by mutableStateOf<CategoryView?>(null)
    var widgetPicker by mutableStateOf<WidgetArea?>(null)
    var menu by mutableStateOf<MenuState?>(null)
    var renameFolder by mutableStateOf<String?>(null)

    val drag = DragController()
    val pageRequests = MutableSharedFlow<PageRequest>(extraBufferCapacity = 8)
    val resumeTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    var rootView: View? = null
    var pages: List<Page> = emptyList()
    var currentPage = 0

    /** Time of the last finger-down on an icon/widget, so a long-press there doesn't also open Customize. */
    var lastItemPress = 0L

    // Hooks provided by MainActivity.
    var requestPermission: (String) -> Unit = {}
    var addAppWidget: (android.appwidget.AppWidgetProviderInfo, WidgetArea) -> Unit = { _, _ -> }
    var pickWallpaper: (forDark: Boolean) -> Unit = {}
    var requestHomeRole: (Intent) -> Unit = {}

    private val context: Context get() = app

    fun closeOverlays() {
        searchOpen = false
        openFolder = null
        openCategory = null
        widgetPicker = null
        menu = null
        renameFolder = null
    }

    fun onBack(): Boolean {
        when {
            menu != null -> menu = null
            drag.session != null -> drag.reset()
            widgetPicker != null -> widgetPicker = null
            renameFolder != null -> renameFolder = null
            openFolder != null -> openFolder = null
            openCategory != null -> openCategory = null
            searchOpen -> searchOpen = false
            customizeOpen -> customizeOpen = false
            editWidgets -> editWidgets = false
            else -> {
                pageRequests.tryEmit(PageRequest.FirstHome)
                return false
            }
        }
        return true
    }

    fun onHomePressed(alreadyOnTop: Boolean) {
        val hadOverlay = searchOpen || openFolder != null || openCategory != null || customizeOpen || menu != null ||
            widgetPicker != null || editWidgets
        drag.reset()
        closeOverlays()
        customizeOpen = false
        editWidgets = false
        if (alreadyOnTop && !hadOverlay) pageRequests.tryEmit(PageRequest.FirstHome)
    }

    // ---------- launching ----------

    fun launch(appInfo: AppInfo, bounds: Rect?) {
        app.usage.record(appInfo.key)
        val r = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        app.apps.launch(appInfo, r, rootView)
        searchOpen = false
        openFolder = null
        openCategory = null
    }

    fun launchKey(key: String, bounds: Rect?) {
        app.apps.byKey.value[key]?.let { launch(it, bounds) }
    }

    fun startIntent(intent: Intent, failMessage: String = "No app found to open this") {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, failMessage, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, failMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrl(url: String) = startIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun webSearch(query: String) {
        val google = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
        try {
            context.startActivity(google.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            openUrl("https://www.google.com/search?q=" + Uri.encode(query))
        }
        searchOpen = false
    }

    fun playStoreSearch(query: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}&c=apps"))
            .setPackage("com.android.vending")
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            openUrl("https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps")
        }
        searchOpen = false
    }

    fun expandNotifications() {
        try {
            val sb = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Exception) {
            // Not available on this device.
        }
    }

    // ---------- menus ----------

    fun openMenu(target: MenuTarget, anchor: Rect) {
        val shortcuts = when (target) {
            is MenuTarget.HomeApp -> app.apps.shortcuts(target.app)
            is MenuTarget.DockApp -> app.apps.shortcuts(target.app)
            is MenuTarget.LibraryApp -> app.apps.shortcuts(target.app)
            is MenuTarget.FolderApp -> app.apps.shortcuts(target.app)
            is MenuTarget.HomeFolder -> emptyList()
        }
        menu = MenuState(target, anchor, shortcuts)
    }

    // ---------- drag & drop ----------

    fun startDrag(item: DragItem, pointerId: PointerId, rootPos: Offset) {
        menu = null
        if (item is DragItem.FolderApp) openFolder = null
        drag.pointer = rootPos
        drag.session = DragSession(item, pointerId)
    }

    /** Called repeatedly while dragging: live re-ordering, folder hover and page flipping. */
    fun evaluateDrag(rootWidth: Float, edgePx: Float) {
        val s = drag.session ?: return
        val p = drag.pointer
        val now = SystemClock.uptimeMillis()
        val page = pages.getOrNull(currentPage)

        // Flip pages when holding an icon at the screen edge.
        if (page is Page.Home) {
            val dir = when {
                p.x < edgePx -> -1
                p.x > rootWidth - edgePx -> 1
                else -> 0
            }
            if (dir == 0) {
                drag.edgeDir = 0
            } else if (drag.edgeDir != dir) {
                drag.edgeDir = dir; drag.edgeSince = now
            } else if (now - drag.edgeSince > 650) {
                val target = currentPage + dir
                if (pages.getOrNull(target) is Page.Home) pageRequests.tryEmit(PageRequest.Index(target))
                drag.edgeSince = now + 400
            }
        }

        if (drag.dockRect.inflate(12f).contains(p)) {
            drag.mergeTarget = null; drag.candidate = null; drag.insertIndex = -1
            return
        }
        val homeIndex = (page as? Page.Home)?.index ?: return
        val g = drag.grids[homeIndex] ?: return
        if (!g.rect.contains(p)) {
            drag.mergeTarget = null; drag.insertIndex = -1
            return
        }
        val cw = g.rect.width / g.cols
        val ch = g.rect.height / g.rows
        val col = ((p.x - g.rect.left) / cw).toInt().coerceIn(0, g.cols - 1)
        val row = ((p.y - g.rect.top) / ch).toInt().coerceIn(0, g.rows - 1)
        val slot = row * g.cols + col
        val home = app.layout.current.home
        val global = g.start + slot
        val iconCenter = Offset(g.rect.left + (col + 0.5f) * cw, g.rect.top + row * ch + ch * 0.4f)
        val over = home.getOrNull(global)?.takeIf { slot < g.capacity }
        val draggedId = (s.item as? DragItem.Home)?.item?.id
        val near = (p - iconCenter).getDistance() < min(cw, ch) * 0.3f
        val canMerge = over != null && over.id != draggedId && s.item.appKey != null &&
            !(over is HomeItem.App && over.key == s.item.appKey)

        val cand: String
        val action: () -> Unit
        if (canMerge && near) {
            cand = "m:${over!!.id}"
            action = { drag.mergeTarget = over.id }
        } else {
            drag.mergeTarget = null
            if (s.item is DragItem.Home) {
                val from = home.indexOfFirst { it.id == draggedId }
                val to = global.coerceAtMost(home.size - 1)
                cand = "i:$to"
                action = { if (from >= 0 && from != to) app.layout.moveHome(from, to) }
            } else {
                drag.insertIndex = global.coerceAtMost(home.size)
                return
            }
        }
        if (drag.candidate != cand) {
            drag.candidate = cand
            drag.candidateSince = now
        } else if (now - drag.candidateSince > (if (cand.startsWith("m:")) 350 else 160)) {
            action()
        }
    }

    fun drop() {
        val s = drag.session ?: return
        val p = drag.pointer
        val key = s.item.appKey
        val layout = app.layout
        try {
            when {
                key != null && drag.dockRect.inflate(12f).contains(p) -> {
                    val idx = drag.dockSlots.entries.sortedBy { it.key }.count { it.value.center.x < p.x }
                    when (val item = s.item) {
                        is DragItem.Dock -> {
                            val from = layout.current.dock.indexOf(item.key)
                            layout.moveDock(from, if (idx > from) idx - 1 else idx)
                        }
                        is DragItem.FolderApp -> {
                            layout.removeFromFolder(item.fid, key, placeOnHome = false)
                            layout.addToDock(key, idx)
                        }
                        is DragItem.Home -> layout.addToDock(key, idx)
                    }
                }
                drag.mergeTarget != null && key != null -> {
                    val target = drag.mergeTarget!!
                    if (s.item is DragItem.FolderApp) layout.removeFromFolder(s.item.fid, key, placeOnHome = false)
                    val targetKey = (layout.current.home.firstOrNull { it.id == target } as? HomeItem.App)?.key
                    val name = targetKey?.let { app.apps.byKey.value[it]?.category?.title } ?: "Folder"
                    layout.mergeInto(target, key, name)
                }
                drag.insertIndex >= 0 && key != null && s.item !is DragItem.Home -> {
                    when (val item = s.item) {
                        is DragItem.Dock -> layout.dockToHome(key, drag.insertIndex)
                        is DragItem.FolderApp -> {
                            layout.removeFromFolder(item.fid, key, placeOnHome = false)
                            layout.addToHome(key, drag.insertIndex)
                        }
                        else -> {}
                    }
                }
                s.item is DragItem.FolderApp && key != null -> {
                    // Dropped somewhere else: take it out of the folder onto Home.
                    layout.removeFromFolder(s.item.fid, key, placeOnHome = true)
                }
            }
        } finally {
            drag.reset()
        }
    }
}
