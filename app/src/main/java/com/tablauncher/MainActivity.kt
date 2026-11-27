package com.tablauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.tablauncher.data.Settings
import com.tablauncher.data.WidgetArea
import com.tablauncher.data.WidgetItem
import com.tablauncher.data.WidgetType
import com.tablauncher.ui.LauncherController
import com.tablauncher.ui.LauncherRoot
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var controller: LauncherController
    private val app get() = application as TabLauncherApp

    private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo, val area: WidgetArea)
    private var pendingWidget: PendingWidget? = null
    private var wallpaperForDark = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        lifecycleScope.launch {
            app.weather.refresh(force = true)
            app.calendar.refresh()
        }
    }

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureOrFinishWidget() else cancelWidget()
    }

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.resumeTick.value++
    }

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val id = app.wallpapers.import(uri)
            if (id == null) {
                Toast.makeText(this@MainActivity, "Couldn't add that picture", Toast.LENGTH_SHORT).show()
            } else {
                app.settings.set(if (wallpaperForDark) Settings.WP_DARK else Settings.WP_LIGHT, id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        controller = LauncherController(app).apply {
            rootView = window.decorView
            requestPermission = { permissionLauncher.launch(it) }
            addAppWidget = ::startAddWidget
            pickWallpaper = { dark ->
                wallpaperForDark = dark
                wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            requestHomeRole = { intent -> runCatching { roleLauncher.launch(intent) } }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                controller.onBack()
            }
        })

        setContent { LauncherRoot(controller) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val alreadyOnTop = hasWindowFocus() || (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
            controller.onHomePressed(alreadyOnTop)
        }
    }

    override fun onStart() {
        super.onStart()
        app.widgets.start()
    }

    override fun onStop() {
        super.onStop()
        app.widgets.stop()
    }

    override fun onResume() {
        super.onResume()
        controller.resumeTick.value++
        lifecycleScope.launch { app.calendar.refresh() }
    }

    // ---------- adding Android widgets ----------

    private fun startAddWidget(info: AppWidgetProviderInfo, area: WidgetArea) {
        val id = app.widgets.allocateId()
        pendingWidget = PendingWidget(id, info, area)
        if (app.widgets.bindIfAllowed(id, info)) {
            configureOrFinishWidget()
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            try {
                bindLauncher.launch(i)
            } catch (e: Exception) {
                cancelWidget()
                Toast.makeText(this, "Can't add this widget", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureOrFinishWidget() {
        val p = pendingWidget ?: return
        val optional = Build.VERSION.SDK_INT >= 28 &&
            (p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (p.info.configure != null && !optional) {
            try {
                app.widgets.host.startAppWidgetConfigureActivityForResult(this, p.id, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // Fall through and add it without configuring.
            }
        }
        finishWidget()
    }

    private fun finishWidget() {
        val p = pendingWidget ?: return
        pendingWidget = null
        val density = resources.displayMetrics.density
        var h = (p.info.minHeight / density).toInt() + 16
        if (Build.VERSION.SDK_INT >= 31 && p.info.targetCellHeight > 0) h = maxOf(h, p.info.targetCellHeight * 100)
        app.layout.addWidget(
            p.area,
            WidgetItem("w" + UUID.randomUUID().toString().take(8), WidgetType.APP_WIDGET, p.id, h.coerceIn(110, 440)),
        )
        controller.pageRequests.tryEmit(
            if (p.area == WidgetArea.TODAY) com.tablauncher.ui.PageRequest.Today else com.tablauncher.ui.PageRequest.FirstHome
        )
    }

    private fun cancelWidget() {
        pendingWidget?.let { app.widgets.delete(it.id) }
        pendingWidget = null
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            if (resultCode == RESULT_OK) finishWidget() else cancelWidget()
        }
    }

    companion object {
        private const val REQ_CONFIGURE = 4021
    }
}
