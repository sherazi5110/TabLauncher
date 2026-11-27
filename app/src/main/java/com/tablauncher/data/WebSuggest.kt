package com.tablauncher.data

import org.json.JSONArray

/** Google search suggestions as you type. */
object WebSuggest {
    suspend fun fetch(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val body = Net.get("https://suggestqueries.google.com/complete/search?client=firefox&hl=en&q=${Net.enc(query)}", 4000)
            ?: return emptyList()
        return try {
            val arr = JSONArray(body).getJSONArray(1)
            (0 until arr.length()).map { arr.getString(it) }.filter { !it.equals(query, ignoreCase = true) }.take(6)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
