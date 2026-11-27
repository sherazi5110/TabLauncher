package com.tablauncher.ui.widgets

import android.Manifest
import android.appwidget.AppWidgetHostView
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.tablauncher.data.Match
import com.tablauncher.data.MatchState
import com.tablauncher.data.NewsRepo
import com.tablauncher.data.SportsRepo
import com.tablauncher.data.WeatherRepo
import com.tablauncher.data.WidgetArea
import com.tablauncher.data.WidgetItem
import com.tablauncher.data.WidgetType
import com.tablauncher.ui.LocalColors
import com.tablauncher.ui.LocalController
import com.tablauncher.ui.markItemPress
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ---------- shared pieces ----------

val CardShape = RoundedCornerShape(22.dp)

/** True when a widget sits in a fixed-height row and should fill it. */
val LocalWidgetFill = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun WidgetCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        modifier
            .then(if (LocalWidgetFill.current) Modifier.fillMaxHeight() else Modifier)
            .shadow(if (c.dark) 0.dp else 5.dp, CardShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(CardShape)
            .background(c.card)
            .then(if (c.dark) Modifier.border(BorderStroke(1.dp, Color(0xFF2C2C30)), CardShape) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun CardHeader(title: String, dot: Color? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(title.uppercase(Locale.ENGLISH), color = c.text2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Text(
        text,
        color = if (selected) Color.White else c.text2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.accent else c.card2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun rememberNow(intervalMs: Long = 60_000): Long {
    val state = produceState(System.currentTimeMillis()) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(intervalMs - now % intervalMs + 50)
            value = System.currentTimeMillis()
        }
    }
    return state.value
}

fun timeFormatter(context: Context): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Locale.ENGLISH)

fun formatTemp(c: Double, celsius: Boolean): String = "${(if (celsius) c else c * 9 / 5 + 32).roundToInt()}°"

fun relativeTime(t: Long): String {
    if (t <= 0) return ""
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} hr ago"
        else -> "${m / (24 * 60)} d ago"
    }
}

// ---------- clock header (Home) ----------

@Composable
fun ClockHeader(big: Dp, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val ctl = LocalController.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val zdt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    Column(modifier.clickable(interactionSource = null, indication = null) {
        ctl.startIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }) {
        val shadow = if (c.onWallpaperShadow.alpha > 0) androidx.compose.ui.graphics.Shadow(c.onWallpaperShadow, Offset(0f, 2f), 8f) else null
        Text(
            zdt.format(timeFormatter(ctx)),
            color = c.onWallpaper,
            fontSize = big.value.sp,
            fontWeight = com.tablauncher.ui.LocalClockWeight.current,
            lineHeight = big.value.sp,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
        )
        Text(
            zdt.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)),
            color = c.onWallpaper.copy(alpha = 0.9f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
        )
    }
}

// ---------- widget dispatcher with edit controls ----------

@Composable
fun WidgetView(item: WidgetItem, area: WidgetArea, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctl = LocalController.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(modifier.markItemPress(ctl) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        ctl.editWidgets = true
    }) {
        when (item.type) {
            WidgetType.WEATHER -> WeatherWidget(big = area == WidgetArea.TODAY && !compact, compact = compact)
            WidgetType.UP_NEXT -> UpNextWidget(compact)
            WidgetType.WORLD_CLOCK -> WorldClockWidget(compact)
            WidgetType.BATTERY -> BatteryWidget()
            WidgetType.NEWS -> NewsWidget(maxItems = if (area == WidgetArea.TODAY) 8 else 4)
            WidgetType.SCORES -> ScoresWidget(maxItems = if (area == WidgetArea.TODAY) 6 else 4)
            WidgetType.APP_WIDGET -> AppWidgetCard(item)
        }
        if (ctl.editWidgets) EditControls(item, area, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun EditControls(item: WidgetItem, area: WidgetArea, modifier: Modifier) {
    val ctl = LocalController.current
    val layout = ctl.app.layout
    Row(
        modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE6202024))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun btn(icon: ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
            Icon(icon, desc, tint = tint, modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick).padding(6.dp))
        }
        btn(Icons.Rounded.KeyboardArrowUp, "Move up") { layout.moveWidget(area, item.id, -1) }
        btn(Icons.Rounded.KeyboardArrowDown, "Move down") { layout.moveWidget(area, item.id, 1) }
        if (item.type == WidgetType.APP_WIDGET) {
            val h = if (item.heightDp > 0) item.heightDp else 200
            btn(Icons.Rounded.Remove, "Smaller") { layout.resizeWidget(area, item.id, h - 40) }
            btn(Icons.Rounded.Add, "Bigger") { layout.resizeWidget(area, item.id, h + 40) }
        }
        btn(Icons.Rounded.Close, "Remove", tint = Color(0xFFFF6B6B)) {
            if (item.type == WidgetType.APP_WIDGET && item.appWidgetId >= 0) ctl.app.widgets.delete(item.appWidgetId)
            layout.removeWidget(area, item.id)
        }
    }
}

// ---------- weather ----------

@Composable
fun WeatherWidget(big: Boolean, compact: Boolean = false) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val data by ctl.app.weather.state.collectAsState()
    val needsLocation by ctl.app.weather.needsLocation.collectAsState()
    val s by ctl.app.settings.state.collectAsState()
    LaunchedEffect(s.weatherLat, s.weatherLon) {
        while (true) {
            ctl.app.weather.refresh()
            delay(15 * 60_000)
        }
    }
    WidgetCard(Modifier.fillMaxWidth()) {
        CardHeader("Weather")
        val d = data
        if (d == null) {
            Spacer(Modifier.height(10.dp))
            if (needsLocation) {
                Text("Tap to use your location, or set a city in Customize → Widgets.", color = c.text2, fontSize = 14.sp,
                    modifier = Modifier.clickable { ctl.requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION) })
            } else {
                Text("Loading weather…", color = c.text2, fontSize = 14.sp)
            }
            return@WidgetCard
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.city, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatTemp(d.tempC, s.celsius), color = c.text, fontSize = if (big) 56.sp else 44.sp, fontWeight = FontWeight.Light,
                    lineHeight = if (big) 60.sp else 48.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                WeatherGlyph(d.code, d.isDay, if (big) 52.dp else 40.dp, c.dark)
                Text(WeatherRepo.describe(d.code), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("H:${formatTemp(d.highC, s.celsius)} L:${formatTemp(d.lowC, s.celsius)}", color = c.text2, fontSize = 12.5.sp)
            }
        }
        if (compact) return@WidgetCard
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            d.hours.take(if (big) 7 else 6).forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h.label, color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    WeatherGlyph(h.code, h.isDay, 22.dp, c.dark, Modifier.padding(vertical = 4.dp))
                    Text(formatTemp(h.tempC, s.celsius), color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (big) {
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(c.line))
            val lo = d.days.minOfOrNull { it.minC } ?: 0.0
            val hi = d.days.maxOfOrNull { it.maxC } ?: 1.0
            d.days.drop(1).take(4).forEach { day ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                    WeatherGlyph(day.code, true, 22.dp, c.dark)
                    Text(formatTemp(day.minC, s.celsius), color = c.text2, fontSize = 14.sp, modifier = Modifier.width(44.dp).padding(start = 8.dp))
                    BoxWithConstraints(Modifier.weight(1f).height(5.dp)) {
                        val span = (hi - lo).coerceAtLeast(1.0)
                        val start = ((day.minC - lo) / span).toFloat()
                        val end = ((day.maxC - lo) / span).toFloat()
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(c.card2))
                        Box(
                            Modifier.padding(start = maxWidth * start).width(maxWidth * (end - start).coerceAtLeast(0.05f)).height(5.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF5BC0FF), Color(0xFFFFC23A))))
                        )
                    }
                    Text(formatTemp(day.maxC, s.celsius), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

// ---------- calendar ----------

@Composable
fun UpNextWidget(compact: Boolean = false) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val fill = LocalWidgetFill.current
    val events by ctl.app.calendar.events.collectAsState()
    val granted by ctl.app.calendar.hasPermission.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            ctl.app.calendar.refresh()
            delay(5 * 60_000)
        }
    }
    WidgetCard(Modifier.fillMaxWidth()) {
        CardHeader("Up next", dot = Color(0xFFFF3B30)) {
            Text("+ New event", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    ctl.startIntent(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI), "No calendar app found")
                }.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        when {
            !granted -> Text("Tap to show your calendar events", color = c.text2, fontSize = 14.sp,
                modifier = Modifier.clickable { ctl.requestPermission(Manifest.permission.READ_CALENDAR) })
            events.isEmpty() -> Text("No upcoming events", color = c.text2, fontSize = 14.sp)
            else -> events.take(if (compact || fill) 2 else 3).forEachIndexed { i, e ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clickable {
                    ctl.startIntent(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)))
                }) {
                    Box(Modifier.width(3.dp).height(38.dp).clip(CircleShape).background(Color(e.color or 0xFF000000.toInt())))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(e.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(eventTime(ctx, e.begin, e.end, e.allDay) + if (e.location.isNotBlank()) " · ${e.location}" else "",
                            color = c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (fill || (!compact && events.isEmpty())) {
            Spacer(Modifier.height(14.dp))
            MonthCalendar()
        }
    }
}

/** This month at a glance; tap a day to open it in your calendar app. */
@Composable
fun MonthCalendar() {
    val ctl = LocalController.current
    val c = LocalColors.current
    rememberNow()
    val today = java.time.LocalDate.now()
    val first = today.withDayOfMonth(1)
    val firstDow = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val offset = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val days = today.lengthOfMonth()
    Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val d = firstDow.plus(i.toLong())
            Text(d.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH), color = c.text2, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    val cells = offset + days
    for (week in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            for (i in 0 until 7) {
                val day = week * 7 + i - offset + 1
                Box(Modifier.weight(1f).height(26.dp), contentAlignment = Alignment.Center) {
                    if (day in 1..days) {
                        val isToday = day == today.dayOfMonth
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent).clickable {
                                val millis = today.withDayOfMonth(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                ctl.startIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/$millis")),
                                    "No calendar app found")
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$day", color = if (isToday) Color.White else c.text, fontSize = 12.5.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

private fun eventTime(ctx: Context, begin: Long, end: Long, allDay: Boolean): String {
    val zone = ZoneId.systemDefault()
    val b = Instant.ofEpochMilli(begin).atZone(zone)
    val e = Instant.ofEpochMilli(end).atZone(zone)
    val today = ZonedDateTime.now(zone).toLocalDate()
    val day = when (b.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "Tomorrow "
        else -> b.format(DateTimeFormatter.ofPattern("EEE ", Locale.ENGLISH))
    }
    if (allDay) return day.trim().ifEmpty { "Today" } + " · All day"
    val fmt = if (DateFormat.is24HourFormat(ctx)) DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)
    else DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    return "$day${b.format(fmt)} – ${e.format(fmt)}"
}

// ---------- world clock ----------

@Composable
fun WorldClockWidget(compact: Boolean = false) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    val s by ctl.app.settings.state.collectAsState()
    val now = rememberNow()
    val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    WidgetCard(Modifier.fillMaxWidth()) {
        CardHeader("World clock")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.worldClocks.take(if (compact) 3 else 4).forEach { id ->
                val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@forEach
                val t = Instant.ofEpochMilli(now).atZone(zone)
                val diffH = (t.offset.totalSeconds - local.offset.totalSeconds) / 3600.0
                val dayDiff = t.toLocalDate().compareTo(local.toLocalDate())
                val top = when {
                    dayDiff > 0 -> "Tomorrow"
                    dayDiff < 0 -> "Yesterday"
                    diffH == 0.0 -> "Local"
                    else -> (if (diffH > 0) "+" else "") + (if (diffH % 1.0 == 0.0) diffH.toInt().toString() else "%.1f".format(Locale.ENGLISH, diffH)) + "H"
                }
                Column {
                    Text(top, color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(t.format(timeFormatter(ctx)), color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                        if (!DateFormat.is24HourFormat(ctx)) {
                            Text(t.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH)), color = c.text, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                        }
                    }
                    Text(cityName(id), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

fun cityName(zoneId: String) = zoneId.substringAfterLast('/').replace('_', ' ')

// ---------- battery ----------

@Composable
fun BatteryWidget() {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = if (l >= 0) l * 100 / sc.coerceAtLeast(1) else -1
                val st = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    WidgetCard(Modifier.fillMaxWidth()) {
        CardHeader("Battery")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = when {
                charging -> Color(0xFF34C759)
                level in 0..20 -> Color(0xFFFF3B30)
                level in 21..35 -> Color(0xFFFF9F0A)
                else -> Color(0xFF34C759)
            }
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(64.dp)) {
                    val st = 6.dp.toPx()
                    drawArc(c.card2, 0f, 360f, false, style = Stroke(st))
                    drawArc(color, -90f, 360f * (level.coerceAtLeast(0) / 100f), false, style = Stroke(st, cap = StrokeCap.Round))
                }
                Text(if (charging) "⚡" else "", fontSize = 18.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (level >= 0) "$level%" else "—", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                Text(if (charging) "Charging" else "Tablet", color = c.text2, fontSize = 13.sp)
            }
        }
    }
}

// ---------- news ----------

@Composable
fun NewsWidget(maxItems: Int) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val s by ctl.app.settings.state.collectAsState()
    val all by ctl.app.news.items.collectAsState()
    var topic by rememberSaveable { mutableStateOf("TOP") }
    var countryMenu by remember { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(topic, s.newsEdition) { ctl.app.news.load(topic) }
    WidgetCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Top stories", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                Text("🌐 ${NewsRepo.edition(s.newsEdition).name} ▾", color = c.accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { countryMenu = true }.padding(horizontal = 8.dp, vertical = 4.dp))
                androidx.compose.material3.DropdownMenu(countryMenu, { countryMenu = false }) {
                    NewsRepo.editions.forEach { ed ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(ed.name, fontWeight = if (ed.code == s.newsEdition) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                countryMenu = false
                                ctl.app.settings.set(com.tablauncher.data.Settings.NEWS, ed.code)
                                ctl.app.news.clear()
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NewsRepo.topics.forEach { (id, name) -> Chip(name, id == topic) { topic = id } }
        }
        Spacer(Modifier.height(14.dp))
        val items = all[topic].orEmpty()
        if (items.isEmpty()) {
            Text("Loading headlines…", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            return@WidgetCard
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = (maxWidth / 290.dp).toInt().coerceIn(1, 4)
            val count = if (cols == 1) maxItems else if (showMore) cols * 6 else cols * 2
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (cols == 1) {
                    items.take(count).forEach { NewsRow(it) }
                } else {
                    items.take(count).chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { NewsCard(it, Modifier.weight(1f).fillMaxHeight()) }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (items.size > count || showMore) {
                        Text(if (showMore) "Show less" else "More stories", color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(14.dp))
                                .clickable { showMore = !showMore }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsPicture(n: com.tablauncher.data.NewsItem, modifier: Modifier) {
    val c = LocalColors.current
    val hue = (n.source.hashCode() and 0xFFFF) % 360
    Box(modifier.background(Color.hsv(hue.toFloat(), 0.45f, if (c.dark) 0.5f else 0.82f)), contentAlignment = Alignment.Center) {
        Text(n.source.take(1).uppercase(Locale.ENGLISH), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        if (n.image != null) {
            AsyncImage(model = n.image, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}

@Composable
private fun NewsCard(n: com.tablauncher.data.NewsItem, modifier: Modifier) {
    val ctl = LocalController.current
    val c = LocalColors.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.card2).clickable { ctl.openUrl(n.link) }) {
        NewsPicture(n, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(12.dp)) {
            Text(listOf(n.source, relativeTime(n.time)).filter { it.isNotBlank() }.joinToString(" · "),
                color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(n.title, color = c.text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 3,
                overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun NewsRow(n: com.tablauncher.data.NewsItem) {
    val ctl = LocalController.current
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ctl.openUrl(n.link) }, verticalAlignment = Alignment.CenterVertically) {
        NewsPicture(n, Modifier.size(width = 84.dp, height = 60.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(listOf(n.source, relativeTime(n.time)).filter { it.isNotBlank() }.joinToString(" · "),
                color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(n.title, color = c.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        }
    }
}

// ---------- live scores ----------

@Composable
fun ScoresWidget(maxItems: Int) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val fill = LocalWidgetFill.current
    val s by ctl.app.settings.state.collectAsState()
    val matches by ctl.app.sports.matches.collectAsState()
    val defaultSport = s.sportsLeagues.firstNotNullOfOrNull { SportsRepo.league(it)?.sport } ?: "Football"
    var sport by rememberSaveable { mutableStateOf(defaultSport) }
    val ofSport = SportsRepo.leagues.filter { it.sport == sport }
    val leagues = ofSport.filter { it.id in s.sportsLeagues }.ifEmpty { ofSport.take(3) }
    var selected by rememberSaveable(sport) { mutableStateOf(leagues.firstOrNull()?.id) }
    val current = leagues.firstOrNull { it.id == selected } ?: leagues.firstOrNull()
    LaunchedEffect(current?.id) {
        val id = current?.id ?: return@LaunchedEffect
        while (true) {
            ctl.app.sports.ensure(id)
            delay(60_000)
        }
    }
    WidgetCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Live scores", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val liveCount = current?.let { matches[it.id] }?.count { it.state == MatchState.LIVE } ?: 0
            if (liveCount > 0) Text("● $liveCount live", color = Color(0xFFFF3B30), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        if (leagues.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                leagues.forEach { l -> Chip(l.name.removeSuffix(" (all matches)"), l.id == current?.id) { selected = l.id } }
            }
            Spacer(Modifier.height(4.dp))
        }
        val list = current?.let { matches[it.id] }
        val body: @Composable () -> Unit = {
            when {
                list == null -> Text("Loading scores…", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                list.isEmpty() -> Text("No matches right now", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                else -> list.take(if (fill) 20 else maxItems).forEachIndexed { i, m ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    MatchRow(m)
                }
            }
        }
        if (fill) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { body() } else body()
        // One button per sport.
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SportsRepo.sports.forEach { (name, emoji) ->
                val on = name == sport
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (on) c.accent.copy(alpha = 0.18f) else c.card2)
                        .then(if (on) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                        .clickable { sport = name },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 19.sp) }
            }
        }
    }
}

@Composable
private fun MatchRow(m: Match) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = m.link != null) { m.link?.let(ctl::openUrl) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(92.dp)) {
            when (m.state) {
                MatchState.LIVE -> {
                    Text("LIVE", color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(m.detail, color = c.text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                MatchState.PRE -> {
                    val zdt = Instant.ofEpochMilli(m.start).atZone(ZoneId.systemDefault())
                    val isToday = zdt.toLocalDate() == java.time.LocalDate.now()
                    Text(if (isToday) "Today" else zdt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                        color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(zdt.format(if (DateFormat.is24HourFormat(ctx)) DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)
                        else DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                MatchState.POST -> Text(m.detail.ifBlank { "Final" }, color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamLine(m.away, m.state)
            TeamLine(m.home, m.state)
        }
    }
}

@Composable
private fun TeamLine(t: com.tablauncher.data.Team, state: MatchState) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (t.logo != null) {
            AsyncImage(model = t.logo, contentDescription = null, modifier = Modifier.size(22.dp))
        } else {
            Box(Modifier.size(22.dp).clip(CircleShape).background(c.card2), contentAlignment = Alignment.Center) {
                Text(t.short.take(1), color = c.text2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(t.name, color = c.text, fontSize = 15.sp, fontWeight = if (t.winner) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state != MatchState.PRE && t.score != null) {
            Text(t.score, color = c.text, fontSize = 15.sp, fontWeight = if (t.winner || state == MatchState.LIVE) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

// ---------- Android widgets ----------

@Composable
fun AppWidgetCard(item: WidgetItem) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val height = (if (item.heightDp > 0) item.heightDp else 200).dp
    WidgetCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            val w = maxWidth
            val h = maxHeight
            val view = remember(item.appWidgetId) { mutableStateOf<AppWidgetHostView?>(null) }
            if (ctl.app.widgets.info(item.appWidgetId) == null) {
                Text("This widget is no longer available", color = c.text2, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    factory = { ctx ->
                        (ctl.app.widgets.createView(ctx, item.appWidgetId) ?: return@AndroidView TextView(ctx)).also {
                            (it as? com.tablauncher.data.LongPressWidgetView)?.onLongPress = { ctl.editWidgets = true }
                            it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            view.value = it
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { v ->
                        if (v is AppWidgetHostView) {
                            @Suppress("DEPRECATION")
                            v.updateAppWidgetSize(null, w.value.toInt(), h.value.toInt(), w.value.toInt(), h.value.toInt())
                        }
                    },
                )
            }
        }
    }
}
