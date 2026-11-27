package com.tablauncher.ui

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tablauncher.data.IconSpec
import com.tablauncher.data.IconStyle
import com.tablauncher.data.ResolvedIconStyle
import com.tablauncher.data.SwipeAction
import com.tablauncher.data.ThemeMode
import com.tablauncher.ui.customize.CustomizePanel
import com.tablauncher.ui.customize.isDefaultLauncher
import com.tablauncher.ui.customize.requestDefaultLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val popIn = fadeIn(tween(160)) +
    androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.92f)
private val popOut = fadeOut(tween(130)) + androidx.compose.animation.scaleOut(tween(130), targetScale = 0.96f)

private class WallpaperImage(val image: ImageBitmap?, val isDark: Boolean)

@Composable
fun LauncherRoot(ctl: LauncherController) {
    val s by ctl.app.settings.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val themeDark = when (s.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dark = ctl.previewDark.takeIf { ctl.customizeOpen } ?: themeDark
    val wallpaperId = if (dark) s.darkWallpaper else s.lightWallpaper
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val imported by ctl.app.wallpapers.imported.collectAsState()
        val wallpaper by produceState(WallpaperImage(null, dark), wallpaperId, dark, wPx, hPx, imported) {
            value = withContext(Dispatchers.Default) {
                if (wallpaperId == "system") {
                    WallpaperImage(null, systemWallpaperIsDark(context, dark))
                } else {
                    val bmp = ctl.app.wallpapers.load(wallpaperId, dark, wPx, hPx)
                    WallpaperImage(bmp?.asImageBitmap(), bmp?.let { isDarkImage(it) } ?: dark)
                }
            }
        }
        // Keep the system / lock screen wallpaper in step with the launcher, if enabled.
        val realWallpaper = if (themeDark) s.darkWallpaper else s.lightWallpaper
        LaunchedEffect(realWallpaper, themeDark, s.lockScreenSync) {
            if (s.lockScreenSync) ctl.app.wallpapers.applyToSystem(realWallpaper, themeDark)
        }
        SideEffect {
            val w = activity?.window ?: return@SideEffect
            if (wallpaperId == "system") w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }

        val colors = launcherColors(dark, wallpaper.isDark)
        val iconStyle = when (s.iconStyle) {
            IconStyle.AUTO -> if (dark) ResolvedIconStyle.DARK else ResolvedIconStyle.LIGHT
            IconStyle.LIGHT -> ResolvedIconStyle.LIGHT
            IconStyle.DARK -> ResolvedIconStyle.DARK
            IconStyle.TINTED -> ResolvedIconStyle.TINTED
        }
        val spec = IconSpec(s.iconShape, iconStyle, s.tintColor)
        val shape = rememberIconShape(s.iconShape)

        // ---------- sizes ----------
        val landscape = maxWidth > maxHeight
        val cols = if (landscape) s.landCols else s.portCols
        val rows = if (landscape) s.landRows else s.portRows
        val insets = WindowInsets.systemBars.asPaddingValues()
        val topInset = insets.calculateTopPadding()
        val bottomInset = insets.calculateBottomPadding()
        val shortSide = min(maxWidth, maxHeight)
        val base = (shortSide / (if (landscape) 11.5f else 10.5f)).coerceIn(52.dp, 92.dp)
        val dockIcon = (base * 0.88f * s.iconScale).coerceIn(40.dp, 84.dp)
        val hPad = if (landscape) 40.dp else 24.dp
        val widgetCol = (maxWidth * 0.27f).coerceIn(300.dp, 400.dp)
        val topPad = topInset + 12.dp
        val bottomPad = bottomInset + 14.dp + dockIcon * 1.48f + 34.dp
        val gridW = maxWidth - hPad * 2 - if (landscape && s.homeWidgets) widgetCol + 24.dp else 0.dp
        val cellW = gridW / cols
        val cellH = (maxHeight - topPad - bottomPad) / rows
        val labelSpace = if (s.showLabels) (22f * s.labelScale).dp + 6.dp else 0.dp
        val iconSize = (base * s.iconScale).coerceAtMost(cellW * 0.74f).coerceAtMost((cellH - labelSpace) * 0.9f).coerceAtLeast(32.dp)
        val m = HomeMetrics(landscape, cols, rows, s.homeWidgets, iconSize, dockIcon, widgetCol, hPad, topPad, bottomPad)

        // ---------- pages ----------
        val layout by ctl.app.layout.layout.collectAsState()
        val byKey by ctl.app.apps.byKey.collectAsState()
        val dragging = ctl.drag.session != null
        val packed = remember(layout.home, m.cols, m.rows, m.firstRows) { packHome(layout.home, m.cols, m.firstRows, m.rows) }
        SideEffect { ctl.packed = packed }
        val homeCount = packed.size + if (dragging) 1 else 0
        val pages = remember(homeCount, s.libraryOnLeft) {
            buildList {
                add(if (s.libraryOnLeft) Page.Library else Page.Today)
                repeat(homeCount) { add(Page.Home(it)) }
                add(if (s.libraryOnLeft) Page.Today else Page.Library)
            }
        }
        ctl.pages = pages
        val pager = rememberPagerState(initialPage = 1) { pages.size }
        SideEffect { ctl.currentPage = pager.currentPage }
        LaunchedEffect(Unit) {
            ctl.pageRequests.collect { r ->
                val target = when (r) {
                    PageRequest.FirstHome -> 1
                    PageRequest.Library -> ctl.pages.indexOf(Page.Library)
                    PageRequest.Today -> ctl.pages.indexOf(Page.Today)
                    is PageRequest.Index -> r.index
                }
                if (target in ctl.pages.indices) pager.animateScrollToPage(target)
            }
        }
        LaunchedEffect(ctl.customizeOpen) { if (ctl.customizeOpen) pager.animateScrollToPage(1) }
        val dockVisibility by remember(homeCount) {
            derivedStateOf {
                val pos = pager.currentPage + pager.currentPageOffsetFraction
                when {
                    pos < 1f -> 1f - (1f - pos)
                    pos > homeCount.toFloat() -> 1f - (pos - homeCount)
                    else -> 1f
                }.coerceIn(0f, 1f)
            }
        }
        val onHome = pages.getOrNull(pager.currentPage) is Page.Home

        // Status bar icons: follow the wallpaper on Home, the theme elsewhere.
        SideEffect {
            val w = activity?.window ?: return@SideEffect
            val lightBars = if (onHome && !ctl.searchOpen) !wallpaper.isDark else !dark
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
                // Full screen: hide the status & navigation bars; swipe from the edge to show them briefly.
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (s.fullScreen) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }

        // ---------- drag ticking ----------
        val density = LocalDensity.current
        LaunchedEffect(dragging) {
            val edge = with(density) { 44.dp.toPx() }
            while (ctl.drag.session != null) {
                ctl.evaluateDrag(wPx.toFloat(), edge)
                delay(40)
            }
        }

        val customize by animateFloatAsState(
            if (ctl.customizeOpen) 1f else 0f,
            androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 260f),
            label = "customize",
        )
        val panelW = min(600.dp, maxWidth * 0.46f)
        val previewScale = 1f - (1f - ((maxWidth - panelW - 48.dp) / maxWidth)) * customize.coerceIn(0f, 1f)

        LauncherTheme(colors, fontFamily(s.font), clockWeight(s.clockWeight)) {
            CompositionLocalProvider(
                LocalController provides ctl,
                LocalIconSpec provides spec,
                LocalIconShape provides shape,
                LocalLabelScale provides s.labelScale,
                LocalShowLabels provides s.showLabels,
            ) {
                val haptic = LocalHapticFeedback.current
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (customize > 0f) colors.page else Color.Transparent)
                        // While dragging an icon, this sees every finger movement first.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val session = ctl.drag.session ?: continue
                                    val ch = ev.changes.firstOrNull { it.id == session.pointerId } ?: continue
                                    ctl.drag.pointer = ch.position
                                    if (!ch.pressed) ctl.drop()
                                    ch.consume()
                                }
                            }
                        }
                ) {
                    // ----- Home (shrinks into a live preview while customizing) -----
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = previewScale
                                scaleY = previewScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = 24.dp.toPx() * customize
                                clip = customize > 0f
                                this.shape = RoundedCornerShape((28 * customize.coerceIn(0f, 1f)).dp)
                            }
                    ) {
                        wallpaper.image?.let {
                            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !dragging && !ctl.customizeOpen,
                            beyondViewportPageCount = 1,
                            key = { i -> pages.getOrNull(i)?.toString() ?: i },
                        ) { i ->
                            when (val p = pages[i]) {
                                Page.Today -> TodayPage(m)
                                Page.Library -> AppLibraryPage(m)
                                is Page.Home -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        
                                        .pointerInput(s.swipeDown) {
                                            var total = 0f
                                            val threshold = 70.dp.toPx()
                                            detectVerticalDragGestures(
                                                onDragStart = { total = 0f },
                                                onDragEnd = {
                                                    if (total > threshold) when (s.swipeDown) {
                                                        SwipeAction.SEARCH -> ctl.searchOpen = true
                                                        SwipeAction.NOTIFICATIONS -> ctl.expandNotifications()
                                                        SwipeAction.NONE -> {}
                                                    } else if (total < -threshold) ctl.pageRequests.tryEmit(PageRequest.Library)
                                                },
                                                onVerticalDrag = { _, dy -> total += dy },
                                            )
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    if (SystemClock.uptimeMillis() - ctl.lastItemPress > 700 && ctl.drag.session == null && ctl.menu == null) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        ctl.customizeOpen = true
                                                    }
                                                },
                                                onTap = { if (ctl.editWidgets) ctl.editWidgets = false },
                                            )
                                        }
                                ) {
                                    HomePage(p.index, m, packed.getOrNull(p.index).orEmpty(), byKey)
                                }
                            }
                        }
                        // Page dots + dock (only on Home pages)
                        Column(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.graphicsLayer { alpha = dockVisibility }) {
                                PageDots(pages, pager.currentPage, { ctl.pageRequests.tryEmit(PageRequest.Index(it)) })
                            }
                            Spacer(Modifier.padding(top = 10.dp))
                            Dock(dockVisibility, m)
                        }
                        if (ctl.editWidgets && onHome) {
                            Row(
                                Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AddWidgetButton(com.tablauncher.data.WidgetArea.HOME)
                                DoneEditingButton()
                            }
                        }
                        DefaultLauncherBanner(Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp))
                        if (ctl.selecting && onHome) SelectionBar(Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp))
                        if (customize > 0f) {
                            Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { ctl.customizeOpen = false })
                        }
                    }

                    // ----- Customize panel -----
                    AnimatedVisibility(
                        visible = ctl.customizeOpen,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        enter = slideInHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 260f)) { it },
                        exit = slideOutHorizontally(tween(220)) { it },
                    ) {
                        CustomizePanel(Modifier.width(panelW).fillMaxHeight())
                    }

                    // ----- Dragged icon -----
                    ctl.drag.session?.let { session ->
                        val half = with(density) { (iconSize * 0.55f).toPx() }
                        Box(Modifier.offset {
                            IntOffset((ctl.drag.pointer.x - half).roundToInt(), (ctl.drag.pointer.y - half * 1.3f).roundToInt())
                        }.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }) {
                            DragShadow(session.item, iconSize)
                        }
                    }

                    // Folders, categories and menus grow in smoothly instead of popping.
                    var lastFolder by remember { mutableStateOf<String?>(null) }
                    if (ctl.openFolder != null) lastFolder = ctl.openFolder
                    AnimatedVisibility(ctl.openFolder != null, enter = popIn, exit = popOut) { lastFolder?.let { FolderOverlay(it) } }
                    var lastCategory by remember { mutableStateOf<CategoryView?>(null) }
                    if (ctl.openCategory != null) lastCategory = ctl.openCategory
                    AnimatedVisibility(ctl.openCategory != null, enter = popIn, exit = popOut) { lastCategory?.let { CategoryOverlay(it) } }
                    AnimatedVisibility(
                        visible = ctl.searchOpen,
                        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 12 },
                        exit = fadeOut(tween(140)),
                    ) {
                        SearchScreen()
                    }
                    ctl.widgetPicker?.let { WidgetPicker(it) }
                    var lastMenu by remember { mutableStateOf<MenuState?>(null) }
                    if (ctl.menu != null) lastMenu = ctl.menu
                    AnimatedVisibility(ctl.menu != null, enter = popIn, exit = popOut) { lastMenu?.let { MenuPopup(it) } }
                    ctl.renameFolder?.let { RenameFolderDialog(it) }
                    CrashReportDialog()
                }
            }
        }
    }
}

@Composable
private fun DefaultLauncherBanner(modifier: Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("banner_dismissed", false)) }
    val resumeTick by ctl.resumeTick.collectAsState()
    val isDefault = remember(resumeTick) { isDefaultLauncher(ctx) }
    if (dismissed || isDefault) return
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(c.card).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Make TabLauncher your Home app", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Text("Set", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.accent).clickable { requestDefaultLauncher(ctx, ctl) }
                .padding(horizontal = 16.dp, vertical = 7.dp))
        Text("Not now", color = c.text2, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
            prefs.edit().putBoolean("banner_dismissed", true).apply(); dismissed = true
        }.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

private fun isDarkImage(b: Bitmap): Boolean {
    val small = Bitmap.createScaledBitmap(b, 24, 24, true)
    var sum = 0.0
    for (x in 0 until 24) for (y in 0 until 24) {
        val p = small.getPixel(x, y)
        sum += (0.299 * AColor.red(p) + 0.587 * AColor.green(p) + 0.114 * AColor.blue(p)) / 255.0
    }
    if (small != b) small.recycle()
    return sum / (24 * 24) < 0.55
}

private fun systemWallpaperIsDark(context: android.content.Context, fallback: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < 27) return fallback
    val colors = runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        ?: return fallback
    return if (Build.VERSION.SDK_INT >= 31) (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0
    else {
        val p = colors.primaryColor.toArgb()
        (0.299 * AColor.red(p) + 0.587 * AColor.green(p) + 0.114 * AColor.blue(p)) / 255.0 < 0.55
    }
}

@Composable
private fun SelectionBar(modifier: Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val n = ctl.selected.size
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.card).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (n == 0) "Tap apps to select" else "$n selected", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 10.dp))
        @Composable
        fun action(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
            Text(label, color = if (enabled) color else c.text2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(c.card2).clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp))
        }
        action("Remove from Home", Color(0xFFFF3B30), n > 0) {
            ctl.app.layout.removeHomeItems(ctl.selected)
            ctl.endSelection()
        }
        action("Put in a folder", c.accent, n > 1) {
            ctl.app.layout.groupIntoFolder(ctl.selected)
            ctl.endSelection()
        }
        action("Done", c.accent, true) { ctl.endSelection() }
    }
}

/** If the launcher crashed last time, show what went wrong so it can be reported and fixed. */
@Composable
private fun CrashReportDialog() {
    val ctx = LocalContext.current
    val file = remember { java.io.File(ctx.filesDir, com.tablauncher.CRASH_FILE) }
    var text by remember { mutableStateOf(runCatching { if (file.exists()) file.readText() else null }.getOrNull()) }
    val report = text ?: return
    fun close() { file.delete(); text = null }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = ::close,
        title = { Text("TabLauncher crashed last time") },
        text = {
            Column {
                Text("Please take a screenshot of this and send it, so it can be fixed.", fontSize = 14.sp)
                Spacer(Modifier.padding(top = 8.dp))
                Text(report.lines().take(28).joinToString("\n"), fontSize = 11.sp, lineHeight = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.androidx_verticalScroll())
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("TabLauncher crash", report))
                android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        },
        dismissButton = { androidx.compose.material3.TextButton(::close) { Text("Close") } },
    )
}

@Composable
private fun Modifier.androidx_verticalScroll(): Modifier =
    this.then(Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()))
