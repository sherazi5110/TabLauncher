package com.tablauncher.ui.customize

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings.ACTION_HOME_SETTINGS
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AppRegistration
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablauncher.data.AppFont
import com.tablauncher.data.ClockWeight
import com.tablauncher.data.CityResult
import com.tablauncher.data.IconShape
import com.tablauncher.data.IconStyle
import com.tablauncher.data.NewsRepo
import com.tablauncher.data.Settings
import com.tablauncher.data.Shapes
import com.tablauncher.data.SportsRepo
import com.tablauncher.data.SwipeAction
import com.tablauncher.data.ThemeMode
import com.tablauncher.data.WidgetArea
import com.tablauncher.ui.LocalColors
import com.tablauncher.ui.LocalController
import com.tablauncher.ui.SearchField
import com.tablauncher.ui.fontFamily
import com.tablauncher.ui.widgets.cityName
import com.tablauncher.wallpaper.WallpaperRenderer
import kotlinx.coroutines.launch

private enum class Tab(val title: String, val icon: ImageVector) {
    WALLPAPER("Wallpaper", Icons.Rounded.Wallpaper),
    LAYOUT("Layout", Icons.Rounded.Dashboard),
    ICONS("Icons", Icons.Rounded.Apps),
    FONTS("Fonts", Icons.Rounded.TextFields),
    WIDGETS("Widgets", Icons.Rounded.Widgets),
    MORE("More", Icons.Rounded.MoreHoriz),
}

@Composable
fun CustomizePanel(modifier: Modifier = Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    var tab by rememberSaveable { mutableStateOf(Tab.WALLPAPER) }
    Row(modifier.background(c.card).windowInsetsPadding(WindowInsets.systemBars)) {
        // Tab rail
        Column(
            Modifier.width(86.dp).fillMaxHeight().background(c.card2).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier.width(74.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { tab = t }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, null, tint = if (on) c.accent else c.text2)
                    Spacer(Modifier.height(4.dp))
                    Text(t.title, color = if (on) c.accent else c.text2, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tab.title, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(c.accent).clickable { ctl.customizeOpen = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                when (tab) {
                    Tab.WALLPAPER -> WallpaperTab()
                    Tab.LAYOUT -> LayoutTab()
                    Tab.ICONS -> IconsTab()
                    Tab.FONTS -> FontsTab()
                    Tab.WIDGETS -> WidgetsTab()
                    Tab.MORE -> MoreTab()
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ---------- building blocks ----------

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = LocalColors.current.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalColors.current.card2).padding(horizontal = 16.dp, vertical = 6.dp),
        content = content)
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card2).padding(4.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) c.card else Color.Transparent)
                    .clickable { onSelect(o) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), color = if (on) c.text else c.text2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = c.text2, fontSize = 12.5.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Remove, "Less", tint = if (value > range.first) c.accent else c.text2, modifier = Modifier.size(34.dp).clip(CircleShape)
            .background(c.card).clickable(enabled = value > range.first) { onChange(value - 1) }.padding(6.dp))
        Text("$value", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Icon(Icons.Rounded.Add, "More", tint = if (value < range.last) c.accent else c.text2, modifier = Modifier.size(34.dp).clip(CircleShape)
            .background(c.card).clickable(enabled = value < range.last) { onChange(value + 1) }.padding(6.dp))
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, display: (Float) -> String, onChange: (Float) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(display(value), color = c.text2, fontSize = 14.sp)
        }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.card2).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSelect(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    val c = LocalColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, name) ->
            val on = id in selected
            Text(
                name, color = if (on) Color.White else c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) c.accent else c.card2).clickable { onToggle(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}


// ---------- Wallpaper ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperTab() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val st by ctl.app.settings.state.collectAsState()
    val imported by ctl.app.wallpapers.imported.collectAsState()
    val scope = rememberCoroutineScope()
    var editDark by rememberSaveable { mutableStateOf(c.dark) }
    // Show the mode being edited in the preview on the left.
    LaunchedEffect(editDark) { ctl.previewDark = editDark }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ctl.previewDark = null } }
    val selected = if (editDark) st.darkWallpaper else st.lightWallpaper
    fun choose(id: String) = ctl.app.settings.set(if (editDark) Settings.WP_DARK else Settings.WP_LIGHT, id)

    SectionTitle("Theme")
    Segmented(ThemeMode.entries, st.themeMode, { it.title }) { ctl.app.settings.set(Settings.THEME, it) }
    Text("Auto follows your tablet's dark mode setting.", color = c.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))

    SectionTitle("Choose wallpaper for")
    Segmented(listOf(false, true), editDark, { if (it) "Dark mode" else "Light mode" }) { editDark = it }
    Text("The preview shows ${if (editDark) "dark" else "light"} mode while you choose.", color = c.text2, fontSize = 12.5.sp,
        modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(12.dp))

    val ids = WallpaperRenderer.builtins.map { "builtin:${it.id}" } + imported
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
        ids.forEach { id ->
            WallpaperThumb(id, editDark, id == selected, onClick = { choose(id) },
                onDelete = if (id.startsWith("file:")) ({
                    ctl.app.wallpapers.delete(id)
                    if (st.lightWallpaper == id) ctl.app.settings.set(Settings.WP_LIGHT, "builtin:waves")
                    if (st.darkWallpaper == id) ctl.app.settings.set(Settings.WP_DARK, "builtin:waves")
                }) else null)
        }
        // Add your own photo (kept at full resolution)
        Box(
            Modifier.width(118.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)).background(c.card2).clickable { ctl.pickWallpaper(editDark) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(30.dp))
                Text("Add photo", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier.width(118.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)).background(c.card2)
                .then(if (selected == "system") Modifier.border(3.dp, c.accent, RoundedCornerShape(14.dp)) else Modifier)
                .clickable { choose("system") },
            contentAlignment = Alignment.Center,
        ) {
            Text("Current system\nwallpaper", color = c.text2, fontSize = 12.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    SectionTitle("Lock screen")
    Group {
        SwitchRow("Use on Lock screen too", "Also sets the system Home & Lock screen wallpaper, and updates it when the theme switches",
            st.lockScreenSync) { ctl.app.settings.set(Settings.LOCK_SYNC, it) }
    }
    ActionButton("Set Lock screen to this wallpaper now", Icons.Rounded.Wallpaper) {
        scope.launch {
            val ok = ctl.app.wallpapers.applyToSystem(selected, editDark, force = true)
            Toast.makeText(ctx, if (ok) "Lock screen wallpaper set" else "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun WallpaperThumb(id: String, dark: Boolean, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val bmp by produceState<ImageBitmap?>(null, id, dark) {
        value = ctl.app.wallpapers.thumbnail(id, dark, 240, 320)?.asImageBitmap()
    }
    Box(
        Modifier.width(118.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)).background(c.card2)
            .then(if (selected) Modifier.border(3.dp, c.accent, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        val name = WallpaperRenderer.builtins.firstOrNull { "builtin:${it.id}" == id }?.name
        if (name != null) {
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart)
                .padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                .clip(CircleShape).background(c.accent).padding(3.dp))
        }
        if (onDelete != null) {
            Icon(Icons.Rounded.Close, "Delete", tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDelete).padding(4.dp))
        }
    }
}

// ---------- Layout ----------

@Composable
private fun LayoutTab() {
    val ctl = LocalController.current
    val st by ctl.app.settings.state.collectAsState()
    val set = ctl.app.settings::set
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    @Composable
    fun landscapeGrid(title: String) {
        SectionTitle(title)
        Group {
            Stepper("Columns", st.landCols, 4..9) { set(Settings.LAND_COLS, it) }
            Stepper("Rows", st.landRows, 3..7) { set(Settings.LAND_ROWS, it) }
        }
    }
    @Composable
    fun portraitGrid(title: String) {
        SectionTitle(title)
        Group {
            Stepper("Columns", st.portCols, 3..7) { set(Settings.PORT_COLS, it) }
            Stepper("Rows", st.portRows, 4..9) { set(Settings.PORT_ROWS, it) }
        }
    }
    if (landscape) {
        landscapeGrid("App grid – landscape (now)")
        portraitGrid("App grid – portrait (when you rotate)")
    } else {
        portraitGrid("App grid – portrait (now)")
        landscapeGrid("App grid – landscape (when you rotate)")
    }
    SectionTitle("Home Screen")
    Group {
        SwitchRow("Widgets on Home Screen", "Clock and widgets on the first page", st.homeWidgets) { set(Settings.HOME_WIDGETS, it) }
        SwitchRow("Recent apps in Dock", "Shows your last used apps after the divider", st.dockRecents) { set(Settings.DOCK_RECENTS, it) }
        SwitchRow("Add new apps to Home Screen", null, st.addNewApps) { set(Settings.ADD_NEW, it) }
    }
    SectionTitle("Home pages")
    val pages = ctl.packed
    Group {
        pages.forEachIndexed { i, page ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Page ${i + 1}", color = LocalColors.current.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${page.size} item${if (page.size == 1) "" else "s"}" + if (i == 0 && st.homeWidgets) " · with widgets" else "",
                        color = LocalColors.current.text2, fontSize = 12.5.sp)
                }
                if (page.isNotEmpty() && (pages.size > 1 || i > 0)) {
                    Text("Remove", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            ctl.app.layout.removeHomeItems(page.map { it.item.id }.toSet())
                        }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
    Text("Removing a page takes its apps off the Home Screen. They stay in the App Library, and you can add them back from there.",
        color = LocalColors.current.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))

    SectionTitle("Pages")
    Segmented(listOf(false, true), st.libraryOnLeft, { if (it) "App Library on left" else "App Library on right" }) { set(Settings.LIBRARY_LEFT, it) }
    Spacer(Modifier.height(12.dp))
    ActionButton("Edit widgets", Icons.Rounded.Widgets) { ctl.editWidgets = true; ctl.customizeOpen = false }
    ActionButton("Reset Home Screen layout", Icons.Rounded.AppRegistration) {
        ctl.app.layout.resetHome(ctl.app.apps.apps.value, ctl.app.settings.current)
    }
}

// ---------- Icons ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconsTab() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val st by ctl.app.settings.state.collectAsState()
    val set = ctl.app.settings::set
    SectionTitle("Shape")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconShape.entries.forEach { shape ->
            val on = st.iconShape == shape
            Column(
                Modifier.width(78.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else c.card2)
                    .clickable { set(Settings.ICON_SHAPE, shape) }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val accent = c.accent
                Canvas(Modifier.size(44.dp)) {
                    drawPath(Shapes.path(shape, size.minDimension).asComposePath(), accent)
                }
                Spacer(Modifier.height(6.dp))
                Text(shape.title, color = if (on) c.accent else c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    SectionTitle("Size")
    Group {
        SliderRow("Icon size", st.iconScale, 0.7f..1.35f, { "${(it * 100).toInt()}%" }) { set(Settings.ICON_SCALE, it) }
    }
    SectionTitle("Style")
    Segmented(IconStyle.entries, st.iconStyle, { it.title }) { set(Settings.ICON_STYLE, it) }
    Text(
        when (st.iconStyle) {
            IconStyle.AUTO -> "Normal icons in light mode, dark icons in dark mode."
            IconStyle.LIGHT -> "The app's own colourful icon, in your chosen shape."
            IconStyle.DARK -> "Dark background with a coloured symbol."
            IconStyle.TINTED -> "One colour for every icon."
        },
        color = c.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp),
    )
    if (st.iconStyle == IconStyle.TINTED) {
        Spacer(Modifier.height(10.dp))
        val tints = listOf(0xFF5B8CFF, 0xFF34C759, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2, 0xFF64D2FF, 0xFFFFD60A, 0xFFE5E5EA)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tints.forEach { t ->
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(t))
                    .then(if (st.tintColor == t.toInt()) Modifier.border(3.dp, c.text, CircleShape) else Modifier)
                    .clickable { set(Settings.TINT, t.toInt()) })
            }
        }
    }
    SectionTitle("Labels")
    Group {
        SwitchRow("Show app names", null, st.showLabels) { set(Settings.LABELS, it) }
        SliderRow("Label size", st.labelScale, 0.8f..1.3f, { "${(it * 100).toInt()}%" }) { set(Settings.LABEL_SCALE, it) }
    }
}

// ---------- Fonts ----------

@Composable
private fun FontsTab() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val st by ctl.app.settings.state.collectAsState()
    SectionTitle("Launcher font")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFont.entries.forEach { f ->
            val on = st.font == f
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else c.card2)
                    .clickable { ctl.app.settings.set(Settings.FONT, f) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("9:41", fontFamily = fontFamily(f), fontSize = 30.sp, fontWeight = FontWeight.Light, color = c.text, modifier = Modifier.width(86.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.title, fontFamily = fontFamily(f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text("Calendar · Messages · Photos", fontFamily = fontFamily(f), fontSize = 13.sp, color = c.text2, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (on) Icon(Icons.Rounded.Check, null, tint = c.accent)
            }
        }
    }
    SectionTitle("Clock thickness")
    Segmented(ClockWeight.entries, st.clockWeight, { it.title }) { ctl.app.settings.set(Settings.CLOCK_WEIGHT, it) }
}

// ---------- Widgets ----------

private val zones = listOf(
    "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Istanbul", "Europe/Moscow", "Africa/Cairo", "Africa/Lagos",
    "Africa/Johannesburg", "Asia/Dubai", "Asia/Riyadh", "Asia/Karachi", "Asia/Kolkata", "Asia/Dhaka", "Asia/Singapore",
    "Asia/Hong_Kong", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Pacific/Auckland",
    "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Toronto", "America/Sao_Paulo",
)

@Composable
private fun WidgetsTab() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val st by ctl.app.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var cityQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    LaunchedEffect(cityQuery) {
        kotlinx.coroutines.delay(350)
        results = ctl.app.weather.searchCity(cityQuery)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { ActionButton("Add to Home", Icons.Rounded.Add) { ctl.customizeOpen = false; ctl.widgetPicker = WidgetArea.HOME } }
        Box(Modifier.weight(1f)) { ActionButton("Add to Widgets page", Icons.Rounded.Add) { ctl.customizeOpen = false; ctl.widgetPicker = WidgetArea.TODAY } }
    }
    ActionButton("Edit / remove widgets", Icons.Rounded.Widgets) { ctl.editWidgets = true; ctl.customizeOpen = false }

    SectionTitle("Weather location")
    Text("Now: ${st.weatherCity ?: "Your current location"}", color = c.text, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
    SearchField(cityQuery, { cityQuery = it }, "Search a city")
    results.forEach { r ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                ctl.app.settings.edit {
                    putString(Settings.W_LAT, r.lat.toString()); putString(Settings.W_LON, r.lon.toString()); putString(Settings.W_CITY, r.name)
                }
                cityQuery = ""; results = emptyList()
                scope.launch { ctl.app.weather.refresh(force = true) }
            }.padding(12.dp)
        ) {
            Column {
                Text(r.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(r.detail, color = c.text2, fontSize = 12.5.sp)
            }
        }
    }
    ActionButton("Use my current location", Icons.Rounded.MyLocation) {
        ctl.app.settings.edit { remove(Settings.W_LAT); remove(Settings.W_LON); remove(Settings.W_CITY) }
        ctl.requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        scope.launch { ctl.app.weather.refresh(force = true) }
    }
    Spacer(Modifier.height(8.dp))
    Segmented(listOf(true, false), st.celsius, { if (it) "°C" else "°F" }) { ctl.app.settings.set(Settings.CELSIUS, it) }

    SectionTitle("Live scores")
    SportsRepo.leagues.groupBy { it.sport }.forEach { (sport, list) ->
        Text(sport, color = c.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
        ChipSelect(list.map { it.id to it.name }, st.sportsLeagues.toSet()) { id ->
            val next = if (id in st.sportsLeagues) st.sportsLeagues - id else st.sportsLeagues + id
            ctl.app.settings.set(Settings.SPORTS, next)
        }
    }

    SectionTitle("News edition (English)")
    ChipSelect(NewsRepo.editions.map { it.code to it.name }, setOf(st.newsEdition)) { code ->
        ctl.app.settings.set(Settings.NEWS, code)
        ctl.app.news.clear()
    }

    SectionTitle("World clock cities (up to 4)")
    ChipSelect(zones.map { it to cityName(it) }, st.worldClocks.toSet()) { z ->
        val next = if (z in st.worldClocks) st.worldClocks - z else (st.worldClocks + z).takeLast(4)
        ctl.app.settings.set(Settings.CLOCKS, next)
    }
}

// ---------- More ----------

@Composable
private fun MoreTab() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val st by ctl.app.settings.state.collectAsState()
    val apps by ctl.app.apps.apps.collectAsState()
    var showHidden by remember { mutableStateOf(false) }

    SectionTitle("Default Home app")
    val isDefault = remember(st) { isDefaultLauncher(ctx) }
    if (isDefault) {
        Text("TabLauncher is your Home app ✓", color = c.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
    } else {
        ActionButton("Set TabLauncher as default Home app") { requestDefaultLauncher(ctx, ctl) }
    }

    SectionTitle("Gestures")
    Text("Swipe down on Home Screen", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
    Segmented(SwipeAction.entries, st.swipeDown, { it.title.removePrefix("Open ") .replaceFirstChar { ch -> ch.uppercase() } }) {
        ctl.app.settings.set(Settings.SWIPE_DOWN, it)
    }
    Text("Swipe up on Home Screen opens the App Library.", color = c.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))

    SectionTitle("Hidden apps (${st.hidden.size})")
    ActionButton(if (showHidden) "Close list" else "Choose apps to hide") { showHidden = !showHidden }
    if (showHidden) {
        Group {
            apps.forEach { app ->
                val hidden = app.key in st.hidden
                SwitchRow(app.label, null, hidden) { h ->
                    ctl.app.settings.set(Settings.HIDDEN, if (h) st.hidden + app.key else st.hidden - app.key)
                    if (h) ctl.app.layout.update { l ->
                        l.copy(home = l.home.filterNot { it is com.tablauncher.data.HomeItem.App && it.key == app.key }, dock = l.dock - app.key)
                    } else ctl.app.layout.addToHome(app.key)
                }
            }
        }
    }

    SectionTitle("About")
    val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    Text("TabLauncher $version", color = c.text, fontSize = 15.sp)
    Text("Weather: Open-Meteo · News: Google News · Scores: ESPN", color = c.text2, fontSize = 12.5.sp)
}

fun isDefaultLauncher(ctx: Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
    return ri?.activityInfo?.packageName == ctx.packageName
}

fun requestDefaultLauncher(ctx: Context, ctl: com.tablauncher.ui.LauncherController) {
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = ctx.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            ctl.requestHomeRole(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            return
        }
    }
    ctl.startIntent(Intent(ACTION_HOME_SETTINGS))
}
