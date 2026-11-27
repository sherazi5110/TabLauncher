package com.tablauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }

enum class IconStyle(val title: String) { LIGHT("Normal"), AUTO("Auto"), DARK("Dark"), TINTED("Tinted") }

enum class IconShape(val title: String) {
    SQUIRCLE("Squircle"), ROUNDED("Rounded"), CIRCLE("Circle"), SQUARE("Square"), TEARDROP("Teardrop")
}

enum class AppFont(val title: String) {
    SYSTEM("System"), INTER("Inter"), POPPINS("Poppins"), NUNITO("Nunito"), OUTFIT("Outfit"),
    MANROPE("Manrope"), QUICKSAND("Quicksand"), LORA("Lora (serif)")
}

enum class ClockWeight(val title: String) { THIN("Thin"), LIGHT("Light"), REGULAR("Regular"), BOLD("Bold") }

enum class SwipeAction(val title: String) { SEARCH("Open search"), NOTIFICATIONS("Open notifications"), NONE("Nothing") }

data class SettingsData(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val iconStyle: IconStyle = IconStyle.LIGHT,
    val tintColor: Int = 0xFF5B8CFF.toInt(),
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val labelScale: Float = 1f,
    val font: AppFont = AppFont.INTER,
    val clockWeight: ClockWeight = ClockWeight.LIGHT,
    val landCols: Int = 6,
    val landRows: Int = 5,
    val portCols: Int = 5,
    val portRows: Int = 6,
    val homeWidgets: Boolean = true,
    val dockRecents: Boolean = true,
    val addNewApps: Boolean = true,
    val libraryOnLeft: Boolean = false,
    val swipeDown: SwipeAction = SwipeAction.SEARCH,
    val hidden: Set<String> = emptySet(),
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val weatherCity: String? = null,
    val celsius: Boolean = true,
    val newsEdition: String = "US",
    val worldClocks: List<String> = listOf("Europe/London", "Asia/Dubai", "Asia/Tokyo", "America/New_York"),
    val sportsLeagues: List<String> = listOf("soccer/eng.1", "soccer/uefa.champions", "cricket", "basketball/nba"),
    val lightWallpaper: String = "builtin:waves",
    val darkWallpaper: String = "builtin:waves",
    val lockScreenSync: Boolean = true,
)

/** Persistent launcher settings, observable as a [StateFlow]. */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsData> = _state.asStateFlow()
    val current: SettingsData get() = _state.value

    // Held in a field: SharedPreferences only keeps weak references to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _state.value = read() }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
    }

    fun set(key: String, value: Any?) = edit {
        when (value) {
            null -> remove(key)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Double -> putString(key, value.toString())
            is Enum<*> -> putString(key, value.name)
            is Set<*> -> putStringSet(key, value.map { it.toString() }.toSet())
            is List<*> -> putString(key, value.joinToString("|"))
            else -> putString(key, value.toString())
        }
    }

    private fun read(): SettingsData {
        val d = SettingsData()
        fun list(key: String, def: List<String>): List<String> =
            sp.getString(key, null)?.let { s -> if (s.isEmpty()) emptyList() else s.split("|") } ?: def
        return SettingsData(
            themeMode = enumOr(sp.getString(THEME, null), d.themeMode),
            iconStyle = enumOr(sp.getString(ICON_STYLE, null), d.iconStyle),
            tintColor = sp.getInt(TINT, d.tintColor),
            iconShape = enumOr(sp.getString(ICON_SHAPE, null), d.iconShape),
            iconScale = sp.getFloat(ICON_SCALE, d.iconScale),
            showLabels = sp.getBoolean(LABELS, d.showLabels),
            labelScale = sp.getFloat(LABEL_SCALE, d.labelScale),
            font = enumOr(sp.getString(FONT, null), d.font),
            clockWeight = enumOr(sp.getString(CLOCK_WEIGHT, null), d.clockWeight),
            landCols = sp.getInt(LAND_COLS, d.landCols),
            landRows = sp.getInt(LAND_ROWS, d.landRows),
            portCols = sp.getInt(PORT_COLS, d.portCols),
            portRows = sp.getInt(PORT_ROWS, d.portRows),
            homeWidgets = sp.getBoolean(HOME_WIDGETS, d.homeWidgets),
            dockRecents = sp.getBoolean(DOCK_RECENTS, d.dockRecents),
            addNewApps = sp.getBoolean(ADD_NEW, d.addNewApps),
            libraryOnLeft = sp.getBoolean(LIBRARY_LEFT, d.libraryOnLeft),
            swipeDown = enumOr(sp.getString(SWIPE_DOWN, null), d.swipeDown),
            hidden = sp.getStringSet(HIDDEN, null)?.toSet() ?: d.hidden,
            weatherLat = sp.getString(W_LAT, null)?.toDoubleOrNull(),
            weatherLon = sp.getString(W_LON, null)?.toDoubleOrNull(),
            weatherCity = sp.getString(W_CITY, null),
            celsius = sp.getBoolean(CELSIUS, d.celsius),
            newsEdition = sp.getString(NEWS, null) ?: d.newsEdition,
            worldClocks = list(CLOCKS, d.worldClocks),
            sportsLeagues = list(SPORTS, d.sportsLeagues),
            lightWallpaper = sp.getString(WP_LIGHT, null) ?: d.lightWallpaper,
            darkWallpaper = sp.getString(WP_DARK, null) ?: d.darkWallpaper,
            lockScreenSync = sp.getBoolean(LOCK_SYNC, d.lockScreenSync),
        )
    }

    companion object {
        const val THEME = "theme"
        const val ICON_STYLE = "icon_style"
        const val TINT = "tint"
        const val ICON_SHAPE = "icon_shape"
        const val ICON_SCALE = "icon_scale"
        const val LABELS = "labels"
        const val LABEL_SCALE = "label_scale"
        const val FONT = "font"
        const val CLOCK_WEIGHT = "clock_weight"
        const val LAND_COLS = "land_cols"
        const val LAND_ROWS = "land_rows"
        const val PORT_COLS = "port_cols"
        const val PORT_ROWS = "port_rows"
        const val HOME_WIDGETS = "home_widgets"
        const val DOCK_RECENTS = "dock_recents"
        const val ADD_NEW = "add_new"
        const val LIBRARY_LEFT = "library_left"
        const val SWIPE_DOWN = "swipe_down"
        const val HIDDEN = "hidden"
        const val W_LAT = "w_lat"
        const val W_LON = "w_lon"
        const val W_CITY = "w_city"
        const val CELSIUS = "celsius"
        const val NEWS = "news"
        const val CLOCKS = "clocks"
        const val SPORTS = "sports"
        const val WP_LIGHT = "wp_light"
        const val WP_DARK = "wp_dark"
        const val LOCK_SYNC = "lock_sync"
    }
}

inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default
