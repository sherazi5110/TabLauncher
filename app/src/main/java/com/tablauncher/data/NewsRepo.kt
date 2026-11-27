package com.tablauncher.data

import android.util.Xml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(val title: String, val source: String, val link: String, val time: Long, val image: String? = null)

data class NewsEdition(val code: String, val name: String, val hl: String, val gl: String, val ceid: String)

/** English headlines from Google News RSS. */
class NewsRepo(private val settings: Settings) {
    private val cache = mutableMapOf<String, Pair<Long, List<NewsItem>>>()
    private val _items = MutableStateFlow<Map<String, List<NewsItem>>>(emptyMap())
    val items: StateFlow<Map<String, List<NewsItem>>> = _items

    suspend fun load(topic: String, force: Boolean = false) {
        val ed = edition(settings.current.newsEdition)
        val key = "${ed.code}/$topic"
        val cached = cache[key]
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 20 * 60_000) {
            _items.value = _items.value + (topic to cached.second)
            return
        }
        // Bing News includes a picture for most stories; Google News is the text-only fallback.
        val bingQuery = when (topic) {
            "TOP" -> "top stories"
            "WORLD" -> "world news"
            else -> topic.lowercase()
        }
        val bing = Net.get("https://www.bing.com/news/search?q=${Net.enc(bingQuery)}&format=rss&cc=${ed.gl}&setlang=en")
            ?.let { parse(it) }.orEmpty()
        val list = if (bing.count { it.image != null } >= 3) bing else {
            val params = "hl=${ed.hl}&gl=${ed.gl}&ceid=${ed.ceid}"
            val url = if (topic == "TOP") "https://news.google.com/rss?$params"
            else "https://news.google.com/rss/headlines/section/topic/$topic?$params"
            Net.get(url)?.let { parse(it) }.orEmpty().ifEmpty { bing }
        }
        if (list.isNotEmpty()) {
            cache[key] = System.currentTimeMillis() to list
            _items.value = _items.value + (topic to list)
        }
    }

    fun clear() {
        cache.clear()
        _items.value = emptyMap()
    }

    private fun parse(xml: String): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
        try {
            val p = Xml.newPullParser()
            p.setInput(StringReader(xml))
            var inItem = false
            var title = ""; var link = ""; var source = ""; var date = 0L; var image: String? = null; var desc = ""
            var tag = ""
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> {
                        tag = p.name
                        if (tag == "item") { inItem = true; title = ""; link = ""; source = ""; date = 0L; image = null; desc = "" }
                        if (inItem && image == null && (tag == "media:content" || tag == "media:thumbnail" || tag == "enclosure")) {
                            image = p.getAttributeValue(null, "url")
                        }
                    }
                    XmlPullParser.TEXT -> if (inItem) {
                        val t = p.text.trim()
                        if (t.isNotEmpty()) when (tag) {
                            "title" -> title += t
                            "link" -> link += t
                            "source", "News:Source" -> source += t
                            "pubDate" -> date = runCatching { fmt.parse(t)?.time ?: 0L }.getOrDefault(0L)
                            "News:Image" -> image = t
                            "description" -> desc += t
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (p.name == "item" && inItem) {
                            inItem = false
                            val clean = if (source.isNotEmpty() && title.endsWith(" - $source")) title.removeSuffix(" - $source") else title
                            if (image == null) image = Regex("<img[^>]+src=\"([^\"]+)\"").find(desc)?.groupValues?.get(1)
                            if (clean.isNotEmpty() && link.isNotEmpty()) out += NewsItem(clean, source, realLink(link), date, image?.let(::bigImage))
                        }
                        tag = ""
                    }
                }
            }
        } catch (e: Exception) {
            // Return whatever was parsed.
        }
        return out.take(30)
    }

    /** Bing wraps article links in a redirect; open the article directly. */
    private fun realLink(link: String): String = try {
        android.net.Uri.parse(link).getQueryParameter("url") ?: link
    } catch (e: Exception) {
        link
    }

    /** Ask Bing's thumbnail service for a bigger picture. */
    private fun bigImage(url: String): String {
        val u = url.replace("http://", "https://")
        return if (u.contains("bing.com/th")) "$u&w=640&h=360&c=7&rs=1" else u
    }

    companion object {
        val topics = listOf(
            "TOP" to "For you", "WORLD" to "World", "BUSINESS" to "Business", "TECHNOLOGY" to "Technology",
            "SPORTS" to "Sports", "ENTERTAINMENT" to "Entertainment", "SCIENCE" to "Science", "HEALTH" to "Health",
        )

        val editions = listOf(
            NewsEdition("US", "United States", "en-US", "US", "US:en"),
            NewsEdition("GB", "United Kingdom", "en-GB", "GB", "GB:en"),
            NewsEdition("PK", "Pakistan", "en-PK", "PK", "PK:en"),
            NewsEdition("IN", "India", "en-IN", "IN", "IN:en"),
            NewsEdition("AE", "United Arab Emirates", "en-AE", "AE", "AE:en"),
            NewsEdition("SA", "Saudi Arabia", "en-SA", "SA", "SA:en"),
            NewsEdition("CA", "Canada", "en-CA", "CA", "CA:en"),
            NewsEdition("AU", "Australia", "en-AU", "AU", "AU:en"),
            NewsEdition("SG", "Singapore", "en-SG", "SG", "SG:en"),
            NewsEdition("ZA", "South Africa", "en-ZA", "ZA", "ZA:en"),
        )

        fun edition(code: String) = editions.firstOrNull { it.code == code } ?: editions[0]
    }
}
