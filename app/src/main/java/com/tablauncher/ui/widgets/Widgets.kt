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

@Composable
fun WidgetCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        modifier
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
    Box(modifier.markItemPress(ctl)) {
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
    val events by ctl.app.calendar.events.collectAsState()
    val granted by ctl.app.calendar.hasPermission.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            ctl.app.calendar.refresh()
            delay(5 * 60_000)
        }
    }
    WidgetCard(Modifier.fillMaxWidth()) {
        CardHeader("Up next", dot = Color(0xFFFF3B30))
        Spacer(Modifier.height(10.dp))
        when {
            !granted -> Text("Tap to show your calendar events", color = c.text2, fontSize = 14.sp,
                modifier = Modifier.clickable { ctl.requestPermission(Manifest.permission.READ_CALENDAR) })
            events.isEmpty() -> Text("No upcoming events", color = c.text2, fontSize = 14.sp)
            else -> events.take(if (compact) 2 else 3).forEachIndexed { i, e ->
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
    LaunchedEffect(topic, s.newsEdition) { ctl.app.news.load(topic) }
    WidgetCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Top stories", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Google News · ${NewsRepo.edition(s.newsEdition).name}", color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NewsRepo.topics.forEach { (id, name) -> Chip(name, id == topic) { topic = id } }
        }
        Spacer(Modifier.height(6.dp))
        val items = all[topic].orEmpty()
        if (items.isEmpty()) {
            Text("Loading headlines…", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
        items.take(maxItems).forEachIndexed { i, n ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(
                Modifier.fillMaxWidth().clickable { ctl.openUrl(n.link) }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hue = (n.source.hashCode() and 0xFFFF) % 360
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.hsv(hue.toFloat(), 0.45f, if (c.dark) 0.55f else 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(n.source.take(1).uppercase(Locale.ENGLISH), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOf(n.source, relativeTime(n.time)).filter { it.isNotBlank() }.joinToString(" · "),
                        color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(n.title, color = c.text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                }
            }
        }
    }
}

// ---------- live scores ----------

@Composable
fun ScoresWidget(maxItems: Int) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val s by ctl.app.settings.state.collectAsState()
    val matches by ctl.app.sports.matches.collectAsState()
    val leagues = s.sportsLeagues.mapNotNull { SportsRepo.league(it) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val current = leagues.firstOrNull { it.id == selected } ?: leagues.firstOrNull()
    LaunchedEffect(s.sportsLeagues) {
        while (true) {
            ctl.app.sports.refresh()
            delay(60_000)
        }
    }
    WidgetCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Live scores", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val liveCount = matches.values.sumOf { l -> l.count { it.state == MatchState.LIVE } }
            if (liveCount > 0) {
                Text("● $liveCount live", color = Color(0xFFFF3B30), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (leagues.isEmpty()) {
            Text("Choose your leagues in Customize → Widgets.", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            return@WidgetCard
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leagues.forEach { l -> Chip(l.name.removeSuffix(" (all matches)"), l.id == current?.id) { selected = l.id } }
        }
        Spacer(Modifier.height(6.dp))
        val list = current?.let { matches[it.id] }
        when {
            list == null -> Text("Loading scores…", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            list.isEmpty() -> Text("No matches right now", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            else -> list.take(maxItems).forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                MatchRow(m)
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
