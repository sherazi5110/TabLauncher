package com.tablauncher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.UserManager

/** Hosts normal Android widgets (Google, Gmail, Calendar…) inside the launcher. */
class WidgetHostManager(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> {
        val um = context.getSystemService(UserManager::class.java)
        return um.userProfiles.flatMap { user ->
            runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList())
        }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        try { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) } catch (e: Exception) { false }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return try { host.createView(viewContext, id, info) } catch (e: Exception) { null }
    }

    fun delete(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    companion object {
        const val HOST_ID = 1207
    }
}
