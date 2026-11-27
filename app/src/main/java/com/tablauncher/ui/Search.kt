package com.tablauncher.ui

import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablauncher.data.AppInfo
import com.tablauncher.data.WebSuggest
import com.tablauncher.ui.widgets.WidgetCard
import kotlinx.coroutines.delay
import java.util.Locale

private data class SettingsShortcut(val name: String, val words: List<String>, val action: String)

private val settingsShortcuts = listOf(
    SettingsShortcut("Wi-Fi", listOf("wifi", "wi-fi", "internet", "network"), Settings.ACTION_WIFI_SETTINGS),
    SettingsShortcut("Bluetooth", listOf("bluetooth", "pair"), Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingsShortcut("Display & brightness", listOf("display", "brightness", "screen", "dark mode"), Settings.ACTION_DISPLAY_SETTINGS),
    SettingsShortcut("Sound", listOf("sound", "volume", "ringtone"), Settings.ACTION_SOUND_SETTINGS),
    SettingsShortcut("Battery", listOf("battery", "power"), Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingsShortcut("Apps", listOf("apps", "applications"), Settings.ACTION_APPLICATION_SETTINGS),
    SettingsShortcut("Storage", listOf("storage", "space"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingsShortcut("Location", listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingsShortcut("Date & time", listOf("date", "time"), Settings.ACTION_DATE_SETTINGS),
    SettingsShortcut("Language", listOf("language", "english", "keyboard"), Settings.ACTION_LOCALE_SETTINGS),
    SettingsShortcut("Accessibility", listOf("accessibility"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsShortcut("Security", listOf("security", "password", "lock"), Settings.ACTION_SECURITY_SETTINGS),
    SettingsShortcut("Default Home app", listOf("home app", "launcher", "default"), Settings.ACTION_HOME_SETTINGS),
    SettingsShortcut("Airplane mode", listOf("airplane", "flight"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingsShortcut("Developer options", listOf("developer"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SettingsShortcut("All settings", listOf("settings"), Settings.ACTION_SETTINGS),
)

@Composable
fun SearchScreen() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val all by ctl.app.apps.apps.collectAsState()
    val s by ctl.app.settings.state.collectAsState()
    val usage by ctl.app.usage.usage.collectAsState()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val apps = remember(all, s.hidden) { all.filter { it.key !in s.hidden } }
    val matches = remember(apps, query) { searchApps(apps, query) }
    val suggested = remember(apps, usage) {
        apps.filter { ctl.app.usage.score(it.key) > 0 }.sortedByDescending { ctl.app.usage.score(it.key) }.take(8)
    }
    val calc = remember(query) { Calculator.evaluate(query) }
    val url = remember(query) { looksLikeUrl(query.trim()) }
    val settingsHits = remember(query) {
        val q = query.trim().lowercase(Locale.ENGLISH)
        if (q.length < 3) emptyList() else settingsShortcuts.filter { sc -> sc.words.any { it.startsWith(q) || q.startsWith(it) } || sc.name.lowercase().startsWith(q) }.take(3)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    LaunchedEffect(query) {
        if (query.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(180)
        suggestions = WebSuggest.fetch(query)
    }

    fun submit() {
        val q = query.trim()
        when {
            q.isEmpty() -> {}
            matches.isNotEmpty() -> ctl.launch(matches.first(), null)
            url != null -> { ctl.openUrl(url); ctl.searchOpen = false }
            else -> ctl.webSearch(q)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.page)
            .clickable(interactionSource = null, indication = null) { ctl.searchOpen = false }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.ime),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    value = query,
                    onChange = { query = it },
                    placeholder = "Search apps, web and Play Store",
                    modifier = Modifier.weight(1f).height(52.dp),
                    focusRequester = focus,
                    onSubmit = ::submit,
                    trailing = {
                        Icon(Icons.Rounded.Mic, "Voice search", tint = c.accent, modifier = Modifier
                            .padding(start = 8.dp).size(26.dp).clip(RoundedCornerShape(13.dp)).clickable {
                                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                runCatching { voice.launch(i) }
                            })
                    },
                )
                Text("Cancel", color = c.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp).clickable { ctl.searchOpen = false })
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.clickable(interactionSource = null, indication = null) {},
            ) {
                if (query.isBlank()) {
                    if (suggested.isNotEmpty()) item { Section("Suggestions") { AppIconsRow(suggested) } }
                    return@LazyColumn
                }
                if (calc != null) item {
                    Section("Calculator") {
                        ResultRow(Icons.Rounded.Calculate, buildAnnotatedString {
                            append("${query.trim()} = ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(calc) }
                        }) {}
                    }
                }
                if (url != null) item {
                    Section("Website") { ResultRow(Icons.Rounded.Public, buildAnnotatedString { append("Open $url") }) { ctl.openUrl(url); ctl.searchOpen = false } }
                }
                if (matches.isNotEmpty()) item { Section("Apps") { AppIconsRow(matches.take(16)) } }
                if (settingsHits.isNotEmpty()) item {
                    Section("Settings") {
                        settingsHits.forEach { sc ->
                            ResultRow(Icons.Rounded.Settings, buildAnnotatedString { append(sc.name) }) {
                                ctl.startIntent(Intent(sc.action)); ctl.searchOpen = false
                            }
                        }
                    }
                }
                item {
                    Section("Search Google") {
                        val q = query.trim()
                        (listOf(q) + suggestions).distinct().forEachIndexed { i, sug ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                            Row(
                                Modifier.fillMaxWidth().clickable { ctl.webSearch(sug) }.padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Search, null, tint = c.text2, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(highlight(sug, q), color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (i > 0) Icon(Icons.Rounded.NorthWest, "Use", tint = c.text2, modifier = Modifier.size(22.dp)
                                    .clip(RoundedCornerShape(11.dp)).clickable { query = "$sug " })
                            }
                        }
                    }
                }
                item {
                    WidgetCard(Modifier.fillMaxWidth().clickable { ctl.playStoreSearch(query.trim()) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF01875F)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Shop, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(buildAnnotatedString {
                                append("Search ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("“${query.trim()}”") }
                                append(" on the Play Store")
                            }, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.text2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    WidgetCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {
        Text(title.uppercase(Locale.ENGLISH), color = LocalColors.current.text2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ResultRow(icon: ImageVector, text: androidx.compose.ui.text.AnnotatedString, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = c.text, fontSize = 16.sp)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppIconsRow(apps: List<AppInfo>) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEach { app ->
            val holder = remember(app.key) { CoordsHolder() }
            IconCell(app.label, 56.dp, c.text, Color.Transparent, Modifier.width(86.dp), showLabel = true) {
                AppIconImage(app, 56.dp, Modifier.onGloballyPositioned { holder.coords = it }.launcherGestures(
                    app.key, ctl,
                    onTap = { ctl.launch(app, holder.bounds()) },
                    onLongPress = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        holder.bounds()?.let { ctl.openMenu(MenuTarget.LibraryApp(app), it) }
                    },
                ))
            }
        }
    }
}

private fun highlight(text: String, query: String) = buildAnnotatedString {
    val i = text.lowercase().indexOf(query.lowercase())
    if (query.isBlank() || i < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i, i + query.length)) }
    append(text.substring(i + query.length))
}

private fun looksLikeUrl(q: String): String? {
    if (q.contains(' ') || !q.contains('.')) return null
    val tld = q.substringAfterLast('.').substringBefore('/')
    if (tld.length < 2 || !tld.all { it.isLetter() }) return null
    return if (q.startsWith("http://") || q.startsWith("https://")) q else "https://$q"
}

/** Tiny calculator for the search box: + - * / % ^ and brackets. */
object Calculator {
    fun evaluate(input: String): String? {
        val s = input.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
        if (s.length < 3 || s.none { it in "+-*/^%" } || s.any { it !in "0123456789.+-*/^%()" }) return null
        if (!s.any { it.isDigit() }) return null
        return try {
            val p = Parser(s)
            val v = p.expr()
            if (p.pos != s.length || v.isNaN() || v.isInfinite()) null
            else if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
            else "%.8f".format(Locale.ENGLISH, v).trimEnd('0').trimEnd('.')
        } catch (e: Exception) {
            null
        }
    }

    private class Parser(val s: String) {
        var pos = 0
        fun peek() = if (pos < s.length) s[pos] else '\u0000'
        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') { val op = s[pos++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = factor()
            while (peek() == '*' || peek() == '/' || peek() == '%') {
                val op = s[pos++]
                val r = factor()
                v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
            }
            return v
        }
        fun factor(): Double {
            val b = unary()
            return if (peek() == '^') { pos++; Math.pow(b, factor()) } else b
        }
        fun unary(): Double = when (peek()) {
            '-' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> primary()
        }
        fun primary(): Double {
            if (peek() == '(') { pos++; val v = expr(); require(peek() == ')'); pos++; return v }
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}
