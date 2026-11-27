package com.tablauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablauncher.data.AppInfo
import com.tablauncher.data.UsageStore

data class LibraryGroup(val title: String, val apps: List<AppInfo>)

fun libraryGroups(apps: List<AppInfo>, usage: UsageStore): List<LibraryGroup> {
    val now = System.currentTimeMillis()
    val byScore = compareByDescending<AppInfo> { usage.score(it.key, now) }.thenBy { it.label.lowercase() }
    val suggestions = apps.filter { usage.score(it.key, now) > 0 }.sortedWith(byScore).take(8)
    val recent = apps.sortedWith(compareBy<AppInfo> { it.isSystem }.thenByDescending { it.installTime }).take(8)
    val cats = apps.groupBy { it.category }
        .map { (cat, list) -> LibraryGroup(cat.title, list.sortedWith(byScore)) }
        .sortedWith(compareByDescending<LibraryGroup> { g -> g.apps.sumOf { usage.score(it.key, now) } }.thenByDescending { it.apps.size })
    return buildList {
        if (suggestions.isNotEmpty()) add(LibraryGroup("Suggestions", suggestions))
        add(LibraryGroup("Recently Added", recent))
        addAll(cats)
    }
}

@Composable
fun AppLibraryPage(m: HomeMetrics) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val all by ctl.app.apps.apps.collectAsState()
    val s by ctl.app.settings.state.collectAsState()
    val usageMap by ctl.app.usage.usage.collectAsState()
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // Only take keyboard focus after a real tap; otherwise Android may focus this box on start-up
    // and jump the launcher to the App Library.
    var allowFocus by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val apps = remember(all, s.hidden) { all.filter { it.key !in s.hidden } }
    val groups = remember(apps, usageMap) { libraryGroups(apps, ctl.app.usage) }

    Column(
        Modifier.fillMaxSize().background(c.page).windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                value = query,
                onChange = { query = it },
                placeholder = "App Library",
                modifier = Modifier.weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            allowFocus = true
                        }
                    }
                    .focusProperties { canFocus = allowFocus }
                    .onFocusChanged { focused = it.isFocused; if (!it.isFocused && query.isEmpty()) allowFocus = false },
            )
            if (focused || query.isNotEmpty()) {
                Text("Cancel", color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(start = 14.dp).clickable { query = ""; focus.clearFocus() })
            }
        }
        Spacer(Modifier.height(18.dp))
        if (focused || query.isNotEmpty()) {
            val results = remember(apps, query) { if (query.isBlank()) apps else searchApps(apps, query) }
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                var lastLetter = ""
                results.forEach { app ->
                    val letter = app.label.take(1).uppercase()
                    if (query.isBlank() && letter != lastLetter) {
                        lastLetter = letter
                        item(key = "h$letter") {
                            Text(letter, color = c.text2, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp))
                        }
                    }
                    item(key = app.key) { AppRow(app) }
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val cols = if (m.landscape) 4 else 3
                val gap = 40.dp
                val tile = ((maxWidth - 96.dp - gap * (cols - 1)) / cols).coerceAtMost(220.dp)
                val gridWidth = tile * cols + gap * (cols - 1)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.width(gridWidth).align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                ) {
                    items(groups, key = { it.title }) { g -> LibraryTile(g, tile) }
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onSubmit: () -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .height(48.dp)
            .shadow(if (c.dark) 0.dp else 4.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(shape)
            .background(c.card)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.text2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = c.text2, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontFamily = LocalFontFamily.current),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
                ),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Clear", tint = c.text2, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).clickable { onChange("") })
        }
        trailing?.invoke()
    }
}

fun searchApps(apps: List<AppInfo>, q: String): List<AppInfo> {
    val query = q.trim().lowercase()
    if (query.isEmpty()) return emptyList()
    fun rank(a: AppInfo): Int {
        val l = a.label.lowercase()
        val initials = l.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
        return when {
            l == query -> 0
            l.startsWith(query) -> 1
            l.split(' ').any { it.startsWith(query) } -> 2
            l.contains(query) -> 3
            initials.startsWith(query) && query.length > 1 -> 4
            a.packageName.lowercase().contains(query) -> 5
            else -> 99
        }
    }
    return apps.map { it to rank(it) }.filter { it.second < 99 }.sortedWith(compareBy({ it.second }, { it.first.label.lowercase() })).map { it.first }
}

@Composable
fun AppRow(app: AppInfo) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val haptic = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .onGloballyPositioned { holder.coords = it }
            .launcherGestures(
                app.key, ctl,
                onTap = { ctl.launch(app, holder.bounds()) },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    holder.bounds()?.let { ctl.openMenu(MenuTarget.LibraryApp(app), it) }
                },
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.category.title + if (app.isWork) " · Work" else "", color = c.text2, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun LibraryTile(g: LibraryGroup, tile: Dp) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val haptic = LocalHapticFeedback.current
    val pad = tile * 0.08f
    val gap = tile * 0.06f
    val big = (tile - pad * 2 - gap) / 2
    val small = (big - gap) / 2
    val shape = RoundedCornerShape(tile * 0.17f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(tile)
                .shadow(if (c.dark) 0.dp else 5.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(shape)
                .background(c.card)
                .padding(pad)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (k in 0..1) {
                        val i = r * 2 + k
                        if (i == 3 && g.apps.size > 4) {
                            // A cluster of small icons opens the whole category.
                            Box(Modifier.size(big).clip(RoundedCornerShape(big * 0.2f)).clickable {
                                ctl.openCategory = CategoryView(g.title, g.apps.map { it.key })
                            }) {
                                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                    g.apps.drop(3).take(4).chunked(2).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { AppIconImage(it, small) } }
                                    }
                                }
                            }
                        } else {
                            val app = g.apps.getOrNull(i)
                            if (app == null) {
                                Spacer(Modifier.size(big))
                            } else {
                                val holder = remember(app.key) { CoordsHolder() }
                                AppIconImage(
                                    app, big,
                                    Modifier.onGloballyPositioned { holder.coords = it }.launcherGestures(
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
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(g.title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.width(tile))
    }
}
