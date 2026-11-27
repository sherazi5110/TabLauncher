package com.tablauncher.data

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class IconSpec(val shape: IconShape, val style: ResolvedIconStyle, val tint: Int)

/** Loads the installed apps, keeps them up to date and renders their icons. */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val _byKey = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val byKey: StateFlow<Map<String, AppInfo>> = _byKey

    /** Bumped when icons must be redrawn (an app was updated). */
    val iconVersion = MutableStateFlow(0)

    private val iconCache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val iconSize = (context.resources.displayMetrics.density * 84).toInt().coerceIn(128, 256)
    private var reloadJob: Job? = null

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload(immediate = true)
    }

    fun reload(immediate: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            if (!immediate) delay(300)
            val list = withContext(Dispatchers.IO) { load() }
            _apps.value = list
            _byKey.value = list.associateBy { it.key }
        }
    }

    private fun load(): List<AppInfo> {
        val result = mutableListOf<AppInfo>()
        val me = Process.myUserHandle()
        for (user in userManager.userProfiles) {
            val list = try { launcherApps.getActivityList(null, user) } catch (e: Exception) { emptyList() }
            for (lai in list) {
                if (lai.componentName.packageName == context.packageName) continue
                val key = keyOf(lai, user)
                activityInfos[key] = lai
                val label = lai.label?.toString()?.trim().orEmpty().ifEmpty { lai.componentName.packageName }
                val ai = lai.applicationInfo
                result += AppInfo(
                    key = key,
                    label = label,
                    packageName = lai.componentName.packageName,
                    component = lai.componentName,
                    user = user,
                    category = Categories.categorize(ai, label),
                    installTime = lai.firstInstallTime,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isWork = user != me,
                )
            }
        }
        return result.sortedBy { it.label.lowercase() }
    }

    private fun keyOf(lai: LauncherActivityInfo, user: UserHandle): String {
        val base = lai.componentName.flattenToShortString()
        return if (user == Process.myUserHandle()) base else "$base@${userManager.getSerialNumberForUser(user)}"
    }

    // ---------- icons ----------

    private fun cacheKey(key: String, spec: IconSpec) = "$key|${spec.shape}|${spec.style}|${spec.tint}"

    fun peekIcon(key: String, spec: IconSpec): ImageBitmap? = iconCache.get(cacheKey(key, spec))

    suspend fun loadIcon(app: AppInfo, spec: IconSpec): ImageBitmap? {
        val ck = cacheKey(app.key, spec)
        iconCache.get(ck)?.let { return it }
        val lai = activityInfos[app.key] ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val dpi = context.resources.displayMetrics.densityDpi
                val raw = rawIcon(lai, dpi)
                val d = (raw as? android.graphics.drawable.AdaptiveIconDrawable) ?: lai.getIcon(dpi) ?: raw ?: return@withContext null
                var bmp = IconFactory.render(d, iconSize, spec.shape, spec.style, spec.tint)
                if (app.isWork) bmp = badge(bmp, app.user)
                bmp.asImageBitmap().also { iconCache.put(ck, it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** The icon straight from the app's resources (some systems replace it with a pre-shaped bitmap). */
    private fun rawIcon(lai: LauncherActivityInfo, dpi: Int): android.graphics.drawable.Drawable? = try {
        val ai = lai.applicationInfo
        val res = context.packageManager.getResourcesForApplication(ai)
        val id = lai.activityInfo.iconResource.takeIf { it != 0 } ?: ai.icon
        if (id == 0) null else res.getDrawableForDensity(id, dpi, null)
    } catch (e: Exception) {
        null
    }

    private fun badge(bmp: Bitmap, user: UserHandle): Bitmap {
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bmp), user)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        badged.setBounds(0, 0, out.width, out.height)
        badged.draw(Canvas(out))
        return out
    }

    private fun invalidateIcons(pkg: String) {
        iconCache.snapshot().keys.filter { it.contains(pkg) }.forEach { iconCache.remove(it) }
        iconVersion.value++
    }

    // ---------- actions ----------

    fun launch(app: AppInfo, bounds: Rect?, rootView: android.view.View?) {
        try {
            val opts: Bundle? = if (bounds != null && rootView != null) {
                ActivityOptions.makeScaleUpAnimation(rootView, bounds.left, bounds.top, bounds.width(), bounds.height()).toBundle()
            } else null
            launcherApps.startMainActivity(app.component, app.user, bounds, opts)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun appInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open app info", Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstall(app: AppInfo) {
        try {
            val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .putExtra(Intent.EXTRA_USER, app.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't uninstall ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasShortcutAccess(): Boolean = try { launcherApps.hasShortcutHostPermission() } catch (e: Exception) { false }

    fun shortcuts(app: AppInfo): List<ShortcutInfo> {
        if (!hasShortcutAccess()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (launcherApps.getShortcuts(q, app.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun startShortcut(s: ShortcutInfo) {
        try {
            launcherApps.startShortcut(s, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open shortcut", Toast.LENGTH_SHORT).show()
        }
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) { invalidateIcons(packageName); reload() }
        override fun onPackageAdded(packageName: String, user: UserHandle) { reload() }
        override fun onPackageChanged(packageName: String, user: UserHandle) { invalidateIcons(packageName); reload() }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { reload() }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { reload() }
    }
}
