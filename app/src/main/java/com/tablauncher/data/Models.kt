package com.tablauncher.data

import android.content.ComponentName
import android.os.UserHandle

data class AppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val category: AppCategory,
    val installTime: Long,
    val isSystem: Boolean,
    val isWork: Boolean,
)

enum class AppCategory(val title: String) {
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    PRODUCTIVITY("Productivity & Finance"),
    CREATIVITY("Creativity"),
    INFORMATION("Information & Reading"),
    SHOPPING("Shopping & Food"),
    HEALTH("Health & Fitness"),
    TRAVEL("Travel"),
    UTILITIES("Utilities"),
}

sealed class HomeItem {
    abstract val id: String

    data class App(val key: String) : HomeItem() {
        override val id: String get() = "a:$key"
    }

    /** [w] x [h] is how many grid cells the folder covers; 1x1 is a normal folder icon. */
    data class Folder(val fid: String, val name: String, val apps: List<String>, val w: Int = 1, val h: Int = 1) : HomeItem() {
        override val id: String get() = "f:$fid"
    }
}

enum class WidgetType(val title: String, val description: String) {
    WEATHER("Weather", "Current weather and forecast"),
    UP_NEXT("Up Next", "Your next calendar events"),
    WORLD_CLOCK("World Clock", "Time in other cities"),
    BATTERY("Battery", "Tablet battery level"),
    NEWS("Top Stories", "Headlines from Google News"),
    SCORES("Live Scores", "Football, cricket, NBA and more"),
    APP_WIDGET("Android widget", ""),
}

enum class WidgetArea { HOME, TODAY }

data class WidgetItem(
    val id: String,
    val type: WidgetType,
    val appWidgetId: Int = -1,
    val heightDp: Int = 0,
)

data class Layout(
    val home: List<HomeItem> = emptyList(),
    val dock: List<String> = emptyList(),
    val homeWidgets: List<WidgetItem> = emptyList(),
    val todayWidgets: List<WidgetItem> = emptyList(),
    val known: Set<String> = emptySet(),
    val initialized: Boolean = false,
) {
    fun widgets(area: WidgetArea) = if (area == WidgetArea.HOME) homeWidgets else todayWidgets

    fun withWidgets(area: WidgetArea, list: List<WidgetItem>) =
        if (area == WidgetArea.HOME) copy(homeWidgets = list) else copy(todayWidgets = list)

    /** Every app key that is placed on Home (directly or inside a folder). */
    fun homeKeys(): Set<String> = buildSet {
        home.forEach {
            when (it) {
                is HomeItem.App -> add(it.key)
                is HomeItem.Folder -> addAll(it.apps)
            }
        }
    }
}
