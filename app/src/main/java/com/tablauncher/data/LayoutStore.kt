package com.tablauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Home screen, dock and widget arrangement, saved as JSON. */
class LayoutStore(context: Context) {
    private val sp = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<Layout> = _layout
    val current: Layout get() = _layout.value

    fun update(transform: (Layout) -> Layout) {
        val next = transform(_layout.value)
        if (next != _layout.value) {
            _layout.value = next
            save(next)
        }
    }

    // ---------- home & dock ----------

    fun moveHome(from: Int, to: Int) = update { l ->
        if (from !in l.home.indices) return@update l
        val list = l.home.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        l.copy(home = list)
    }

    fun removeHomeItem(id: String) = update { l -> l.copy(home = l.home.filterNot { it.id == id }) }

    /** Removes a whole Home page; its apps stay in the App Library. */
    fun removeHomeItems(ids: Set<String>) = update { l -> l.copy(home = l.home.filterNot { it.id in ids }) }

    fun addToHome(key: String, index: Int? = null) = update { l ->
        if (key in l.homeKeys()) return@update l
        val list = l.home.toMutableList()
        list.add((index ?: list.size).coerceIn(0, list.size), HomeItem.App(key))
        l.copy(home = list)
    }

    fun addToDock(key: String, index: Int? = null) = update { l ->
        val dock = l.dock.filterNot { it == key }.toMutableList()
        dock.add((index ?: dock.size).coerceIn(0, dock.size), key)
        l.copy(dock = dock, home = removeKey(l.home, key))
    }

    fun removeFromDock(key: String) = update { l -> l.copy(dock = l.dock.filterNot { it == key }) }

    fun moveDock(from: Int, to: Int) = update { l ->
        if (from !in l.dock.indices) return@update l
        val d = l.dock.toMutableList()
        val k = d.removeAt(from)
        d.add(to.coerceIn(0, d.size), k)
        l.copy(dock = d)
    }

    /** Dock app dragged onto the Home grid. */
    fun dockToHome(key: String, index: Int) = update { l ->
        val home = removeKey(l.home, key).toMutableList()
        home.add(index.coerceIn(0, home.size), HomeItem.App(key))
        l.copy(dock = l.dock.filterNot { it == key }, home = home)
    }

    /** Drop app [key] onto the home item at [targetId]: makes or extends a folder. */
    fun mergeInto(targetId: String, key: String, folderName: String) = update { l ->
        val target = l.home.firstOrNull { it.id == targetId } ?: return@update l
        if (target is HomeItem.App && target.key == key) return@update l
        val newItem = when (target) {
            is HomeItem.App -> HomeItem.Folder(UUID.randomUUID().toString().take(8), folderName, listOf(target.key, key))
            is HomeItem.Folder -> if (key in target.apps) target else target.copy(apps = target.apps + key)
        }
        val cleaned = removeKey(l.home.map { if (it.id == targetId) newItem else it }, key, keepId = newItem.id)
        l.copy(home = cleaned, dock = l.dock.filterNot { it == key })
    }

    fun renameFolder(fid: String, name: String) = update { l ->
        l.copy(home = l.home.map { if (it is HomeItem.Folder && it.fid == fid) it.copy(name = name) else it })
    }

    fun removeFromFolder(fid: String, key: String, placeOnHome: Boolean = true) = update { l ->
        val idx = l.home.indexOfFirst { it is HomeItem.Folder && it.fid == fid }
        if (idx < 0) return@update l
        val f = l.home[idx] as HomeItem.Folder
        val remaining = f.apps.filterNot { it == key }
        val list = l.home.toMutableList()
        when (remaining.size) {
            0 -> list.removeAt(idx)
            1 -> list[idx] = HomeItem.App(remaining[0])
            else -> list[idx] = f.copy(apps = remaining)
        }
        if (placeOnHome) list.add((idx + 1).coerceAtMost(list.size), HomeItem.App(key))
        l.copy(home = list)
    }

    fun resizeFolder(fid: String, w: Int, h: Int) = update { l ->
        l.copy(home = l.home.map { if (it is HomeItem.Folder && it.fid == fid) it.copy(w = w, h = h) else it })
    }

    fun ungroupFolder(fid: String) = update { l ->
        val idx = l.home.indexOfFirst { it is HomeItem.Folder && it.fid == fid }
        if (idx < 0) return@update l
        val f = l.home[idx] as HomeItem.Folder
        val list = l.home.toMutableList()
        list.removeAt(idx)
        list.addAll(idx, f.apps.map { HomeItem.App(it) })
        l.copy(home = list)
    }

    private fun removeKey(home: List<HomeItem>, key: String, keepId: String? = null): List<HomeItem> =
        home.mapNotNull { item ->
            if (item.id == keepId) return@mapNotNull item
            when (item) {
                is HomeItem.App -> item.takeIf { it.key != key }
                is HomeItem.Folder -> {
                    val apps = item.apps.filterNot { it == key }
                    when (apps.size) {
                        0 -> null
                        1 -> HomeItem.App(apps[0])
                        else -> item.copy(apps = apps)
                    }
                }
            }
        }

    // ---------- widgets ----------

    fun addWidget(area: WidgetArea, item: WidgetItem) = update { l -> l.withWidgets(area, l.widgets(area) + item) }

    fun removeWidget(area: WidgetArea, id: String) = update { l -> l.withWidgets(area, l.widgets(area).filterNot { it.id == id }) }

    fun moveWidget(area: WidgetArea, id: String, delta: Int) = update { l ->
        val list = l.widgets(area).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@update l
        list.add(j, list.removeAt(i))
        l.withWidgets(area, list)
    }

    fun resizeWidget(area: WidgetArea, id: String, heightDp: Int) = update { l ->
        l.withWidgets(area, l.widgets(area).map { if (it.id == id) it.copy(heightDp = heightDp.coerceIn(80, 640)) else it })
    }

    // ---------- keeping in sync with installed apps ----------

    fun reconcile(apps: List<AppInfo>, settings: SettingsData) {
        if (apps.isEmpty()) return
        val keys = apps.map { it.key }.toSet()
        update { l ->
            var home = l.home.mapNotNull { item ->
                when (item) {
                    is HomeItem.App -> item.takeIf { it.key in keys }
                    is HomeItem.Folder -> {
                        val a = item.apps.filter { it in keys }
                        when (a.size) {
                            0 -> null
                            1 -> HomeItem.App(a[0])
                            else -> item.copy(apps = a)
                        }
                    }
                }
            }
            var dock = l.dock.filter { it in keys }
            var homeWidgets = l.homeWidgets
            var todayWidgets = l.todayWidgets
            if (!l.initialized) {
                dock = defaultDock(apps)
                home = apps.filter { it.key !in dock && it.key !in settings.hidden }.map { HomeItem.App(it.key) }
                homeWidgets = listOf(WidgetType.UP_NEXT, WidgetType.WEATHER, WidgetType.WORLD_CLOCK).map(::builtin)
                todayWidgets = listOf(WidgetType.WEATHER, WidgetType.SCORES, WidgetType.UP_NEXT, WidgetType.NEWS, WidgetType.BATTERY)
                    .map(::builtin)
            } else if (settings.addNewApps) {
                val placed = Layout(home = home).homeKeys() + dock
                val fresh = apps.filter { it.key !in l.known && it.key !in placed && it.key !in settings.hidden }
                home = home + fresh.map { HomeItem.App(it.key) }
            }
            l.copy(home = home, dock = dock, homeWidgets = homeWidgets, todayWidgets = todayWidgets, known = keys, initialized = true)
        }
    }

    fun resetHome(apps: List<AppInfo>, settings: SettingsData) {
        update { it.copy(initialized = false, home = emptyList(), dock = emptyList()) }
        val keepWidgets = current
        reconcile(apps, settings)
        update { it.copy(homeWidgets = keepWidgets.homeWidgets.ifEmpty { it.homeWidgets }, todayWidgets = keepWidgets.todayWidgets.ifEmpty { it.todayWidgets }) }
    }

    private fun defaultDock(apps: List<AppInfo>): List<String> {
        val preferred = listOf(
            "com.android.chrome", "com.google.android.gm", "com.google.android.youtube", "com.android.vending",
            "com.google.android.apps.photos", "com.google.android.apps.messaging", "com.whatsapp",
            "com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files",
            "com.google.android.calendar", "com.google.android.keep", "com.android.settings",
        )
        val byPkg = apps.filter { !it.isWork }.groupBy { it.packageName }
        return preferred.mapNotNull { byPkg[it]?.firstOrNull()?.key }.distinct().take(8)
    }

    companion object {
        fun builtin(type: WidgetType) = WidgetItem(id = "w" + UUID.randomUUID().toString().take(8), type = type)
    }

    // ---------- JSON ----------

    private fun load(): Layout {
        val json = sp.getString("layout", null) ?: return Layout()
        return try {
            val o = JSONObject(json)
            Layout(
                home = o.optJSONArray("home").objects().mapNotNull { h ->
                    when (h.optString("t")) {
                        "app" -> HomeItem.App(h.getString("k"))
                        "folder" -> HomeItem.Folder(h.getString("id"), h.optString("name", "Folder"), h.getJSONArray("apps").strings(),
                            h.optInt("w", 1), h.optInt("h", 1))
                        else -> null
                    }
                },
                dock = o.optJSONArray("dock").strings(),
                homeWidgets = o.optJSONArray("hw").objects().mapNotNull(::widgetFrom),
                todayWidgets = o.optJSONArray("tw").objects().mapNotNull(::widgetFrom),
                known = o.optJSONArray("known").strings().toSet(),
                initialized = o.optBoolean("init", false),
            )
        } catch (e: Exception) {
            Layout()
        }
    }

    private fun widgetFrom(o: JSONObject): WidgetItem? {
        val type = WidgetType.entries.firstOrNull { it.name == o.optString("type") } ?: return null
        return WidgetItem(o.getString("id"), type, o.optInt("wid", -1), o.optInt("h", 0))
    }

    private fun save(l: Layout) {
        val o = JSONObject()
        o.put("home", JSONArray().apply {
            l.home.forEach { h ->
                put(
                    when (h) {
                        is HomeItem.App -> JSONObject().put("t", "app").put("k", h.key)
                        is HomeItem.Folder -> JSONObject().put("t", "folder").put("id", h.fid).put("name", h.name)
                            .put("apps", JSONArray(h.apps)).put("w", h.w).put("h", h.h)
                    }
                )
            }
        })
        o.put("dock", JSONArray(l.dock))
        fun widgets(list: List<WidgetItem>) = JSONArray().apply {
            list.forEach { put(JSONObject().put("id", it.id).put("type", it.type.name).put("wid", it.appWidgetId).put("h", it.heightDp)) }
        }
        o.put("hw", widgets(l.homeWidgets))
        o.put("tw", widgets(l.todayWidgets))
        o.put("known", JSONArray(l.known.toList()))
        o.put("init", l.initialized)
        sp.edit().putString("layout", o.toString()).apply()
    }
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
