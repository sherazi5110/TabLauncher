package com.tablauncher.data

import android.content.pm.ApplicationInfo

/** Sorts apps into App Library categories using Android's category plus name hints. */
object Categories {

    private val hints: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.UTILITIES to listOf(
            "com.android.vending", "settings", "launcher", "filemanager", "files", "documentsui", "calculator",
            "clock", "deskclock", "contacts", "dialer", "keyboard", "inputmethod", "vpn", "cleaner", "security",
            "browser", "chrome", "firefox", "opera", "brave", "recorder", "compass", "flashlight", "scanner",
            "remote", "bluetooth", "backup", "authenticator", "packageinstaller",
        ),
        AppCategory.GAMES to listOf("game", "games", "supercell", "rovio", "com.king.", "mojang", "pubg", "tencent.ig",
            "activision", "miHoYo", "hoyoverse", "roblox", "ea.", "gameloft", "playgames", "freefire", "dts.freefire"),
        AppCategory.SOCIAL to listOf(
            "whatsapp", "facebook", "instagram", "twitter", "com.x.", "zhiliaoapp", "musically", "snapchat",
            "telegram", "discord", "reddit", "linkedin", "messenger", "thoughtcrime", "signal", "apps.messaging",
            "viber", "skype", "barcelona", "pinterest", "tumblr", "chat", "messag", "social", "dating", "imo",
        ),
        AppCategory.ENTERTAINMENT to listOf(
            "youtube", "netflix", "spotify", "avod", "primevideo", "disney", "hulu", "twitch", "music", "video",
            "player", "movie", "moviebox", "tv", "podcast", "radio", "soundcloud", "deezer", "tidal", "vlc",
            "mxtech", "stream", "anime", "crunchyroll", "hbo", "plex", "kodi", "jio", "hotstar",
        ),
        AppCategory.SHOPPING to listOf(
            "amazon.mshop", "ebay", "aliexpress", "shop", "temu", "shein", "daraz", "flipkart", "ubereats",
            "doordash", "foodpanda", "swiggy", "zomato", "deliver", "grocery", "careem.food", "walmart", "ikea",
        ),
        AppCategory.HEALTH to listOf("fit", "health", "workout", "yoga", "calm", "headspace", "strava",
            "step", "sleep", "meditat", "period", "diet", "nike"),
        AppCategory.TRAVEL to listOf("maps", "waze", "ubercab", "uber", "lyft", "careem", "indrive", "booking",
            "airbnb", "tripadvisor", "flight", "travel", "trip", "ride", "transit", "airline", "hotel", "train"),
        AppCategory.PRODUCTIVITY to listOf(
            "docs", "sheets", "slides", "office", "word", "excel", "powerpoint", "drive", "notion", "todo",
            "calendar", "mail", "com.google.android.gm", "outlook", "bank", "wallet", "pay", "finance", "money", "zoom", "teams",
            "meet", "slack", "keep", "notes", "evernote", "dropbox", "onedrive", "pdf", "classroom", "task",
            "trello", "invest", "crypto", "binance", "easypaisa", "jazzcash",
        ),
        AppCategory.CREATIVITY to listOf("photo", "camera", "gallery", "edit", "lightroom", "snapseed", "canva",
            "picsart", "draw", "paint", "sketch", "capcut", "inshot", "studio", "procreate", "design"),
        AppCategory.INFORMATION to listOf("news", "wiki", "book", "read", "kindle", "weather", "translate",
            "dictionary", "learn", "duolingo", "quran", "bible", "magazine", "medium", "quora", "googlequicksearchbox"),
    )

    fun categorize(info: ApplicationInfo, label: String): AppCategory {
        val pkg = info.packageName.lowercase()
        val name = label.lowercase()
        @Suppress("DEPRECATION")
        if (info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return AppCategory.GAMES
        }
        // Known names/packages first (they are more reliable than what apps declare).
        for ((cat, words) in hints) {
            if (words.any { w -> pkg.contains(w.lowercase()) || name.contains(w.lowercase()) }) return cat
        }
        return when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.CREATIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.INFORMATION
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> AppCategory.UTILITIES
        }
    }
}
