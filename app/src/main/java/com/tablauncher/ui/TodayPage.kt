package com.tablauncher.ui

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablauncher.data.WidgetArea
import com.tablauncher.data.WidgetType
import com.tablauncher.ui.widgets.WidgetView
import com.tablauncher.ui.widgets.rememberNow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayPage(m: HomeMetrics) {
    val ctl = LocalController.current
    val c = LocalColors.current
    val layout by ctl.app.layout.layout.collectAsState()
    val now = rememberNow()
    val zdt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val greeting = when (zdt.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyVerticalStaggeredGrid(
        columns = if (m.landscape) StaggeredGridCells.Fixed(3) else StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(c.page),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = top + 24.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "header") {
            Column {
                Text(zdt.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                    color = c.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                Text(greeting, color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                GoogleBar()
                Spacer(Modifier.height(4.dp))
            }
        }
        items(layout.todayWidgets, key = { it.id }, span = { w ->
            if (w.type == WidgetType.NEWS || (w.type == WidgetType.APP_WIDGET && w.heightDp > 360)) StaggeredGridItemSpan.FullLine
            else StaggeredGridItemSpan.SingleLane
        }) { w ->
            WidgetView(w, WidgetArea.TODAY, Modifier.fillMaxWidth())
        }
        item(span = StaggeredGridItemSpan.FullLine, key = "footer") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AddWidgetButton(WidgetArea.TODAY)
                Spacer(Modifier.width(10.dp))
                if (ctl.editWidgets) DoneEditingButton() else {
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(c.card).clickable { ctl.editWidgets = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Edit, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleBar() {
    val ctl = LocalController.current
    val c = LocalColors.current
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(if (c.dark) 0.dp else 5.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(shape)
            .background(c.card)
            .clickable { ctl.searchOpen = true }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.text2)
        Spacer(Modifier.width(12.dp))
        Text("Search the web, your apps and the Play Store", color = c.text2, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Mic, "Voice search", tint = c.accent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable {
            ctl.startIntent(Intent(RecognizerIntent.ACTION_WEB_SEARCH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US"), "Voice search isn't available")
        }.padding(8.dp))
        Icon(Icons.Rounded.CenterFocusWeak, "Google Lens", tint = c.accent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable {
            val lens = Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage("com.google.android.googlequicksearchbox")
            ctl.startIntent(lens, "Google Lens isn't installed")
        }.padding(8.dp))
    }
}
