package com.tablauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Usage(val count: Int, val last: Long)

/** Remembers which apps you open from the launcher (for Suggestions and dock recents). */
class UsageStore(context: Context) {
    private val sp = context.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val _usage = MutableStateFlow(load())
    val usage: StateFlow<Map<String, Usage>> = _usage

    private fun load(): Map<String, Usage> = sp.all.mapNotNull { (k, v) ->
        val parts = (v as? String)?.split(",") ?: return@mapNotNull null
        val c = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val t = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        k to Usage(c, t)
    }.toMap()

    fun record(key: String) {
        val now = System.currentTimeMillis()
        val u = _usage.value[key]
        val next = Usage((u?.count ?: 0) + 1, now)
        _usage.value = _usage.value + (key to next)
        sp.edit().putString(key, "${next.count},${next.last}").apply()
    }

    fun score(key: String, now: Long = System.currentTimeMillis()): Double {
        val u = _usage.value[key] ?: return 0.0
        val hours = (now - u.last) / 3_600_000.0
        return u.count / (1.0 + hours / 72.0)
    }

    fun recents(limit: Int): List<String> =
        _usage.value.entries.sortedByDescending { it.value.last }.take(limit).map { it.key }
}
