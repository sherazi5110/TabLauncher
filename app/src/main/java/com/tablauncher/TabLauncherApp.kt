package com.tablauncher

import android.app.Application
import android.content.Context
import com.tablauncher.data.AppRepository
import com.tablauncher.data.CalendarRepo
import com.tablauncher.data.LayoutStore
import com.tablauncher.data.NewsRepo
import com.tablauncher.data.Settings
import com.tablauncher.data.SportsRepo
import com.tablauncher.data.UsageStore
import com.tablauncher.data.WeatherRepo
import com.tablauncher.data.WidgetHostManager
import com.tablauncher.wallpaper.WallpaperStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class TabLauncherApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: Settings
    lateinit var apps: AppRepository
    lateinit var layout: LayoutStore
    lateinit var usage: UsageStore
    lateinit var widgets: WidgetHostManager
    lateinit var weather: WeatherRepo
    lateinit var news: NewsRepo
    lateinit var sports: SportsRepo
    lateinit var calendar: CalendarRepo
    lateinit var wallpapers: WallpaperStore

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        apps = AppRepository(this, scope)
        layout = LayoutStore(this)
        usage = UsageStore(this)
        widgets = WidgetHostManager(this)
        weather = WeatherRepo(this, settings)
        news = NewsRepo(settings)
        sports = SportsRepo(settings)
        calendar = CalendarRepo(this)
        wallpapers = WallpaperStore(this)

        apps.start()
        scope.launch {
            // Keep Home/Dock in sync with installed apps (new apps get added, removed apps disappear).
            combine(apps.apps, settings.state) { list, s -> list to s }
                .distinctUntilChanged { a, b -> a.first == b.first && a.second.addNewApps == b.second.addNewApps }
                .collect { (list, s) -> layout.reconcile(list, s) }
        }
    }
}

val Context.launcherApp: TabLauncherApp get() = applicationContext as TabLauncherApp
