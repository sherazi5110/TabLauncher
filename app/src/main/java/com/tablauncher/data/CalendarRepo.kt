package com.tablauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class CalEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int, val location: String)

class CalendarRepo(private val context: Context) {
    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())
    val events: StateFlow<List<CalEvent>> = _events
    val hasPermission = MutableStateFlow(false)

    suspend fun refresh() {
        val granted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        hasPermission.value = granted
        if (!granted) return
        _events.value = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, now - 6 * 3_600_000L)
                ContentUris.appendId(it, now + 3 * 86_400_000L)
            }.build()
            val proj = arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION,
            )
            val out = mutableListOf<CalEvent>()
            try {
                context.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val end = c.getLong(3)
                        if (end < now) continue
                        out += CalEvent(c.getLong(0), c.getString(1) ?: "(No title)", c.getLong(2), end, c.getInt(4) == 1, c.getInt(5), c.getString(6) ?: "")
                    }
                }
            } catch (e: Exception) {
                // Ignore; show empty.
            }
            out.take(6)
        }
    }
}
