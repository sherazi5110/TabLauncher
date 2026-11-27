package com.tablauncher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Wallpaper ids:
 *  - "builtin:<name>"  one of [WallpaperRenderer.builtins]
 *  - "file:<name>"     a photo you added, stored in full resolution
 *  - "system"          keep the current system wallpaper (including live wallpapers)
 */
class WallpaperStore(private val context: Context) {
    private val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
    private val _imported = MutableStateFlow(list())
    val imported: StateFlow<List<String>> = _imported
    private val prefs = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    private fun list(): List<String> =
        (dir.listFiles() ?: emptyArray()).sortedBy { it.lastModified() }.map { "file:${it.name}" }

    /** Copies the picked photo byte-for-byte, so it keeps its full resolution. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val name = UUID.randomUUID().toString().take(10) + ".img"
            val out = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                ?: return@withContext null
            // Make sure it really is an image.
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(out.path, o)
            if (o.outWidth <= 0) { out.delete(); return@withContext null }
            _imported.value = list()
            "file:$name"
        } catch (e: Exception) {
            null
        }
    }

    fun delete(id: String) {
        if (!id.startsWith("file:")) return
        File(dir, id.removePrefix("file:")).delete()
        _imported.value = list()
    }

    /** Full-screen bitmap for [id], or null for "system". */
    suspend fun load(id: String, dark: Boolean, w: Int, h: Int): Bitmap? = withContext(Dispatchers.Default) {
        try { loadUnsafe(id, dark, w, h) } catch (e: Throwable) { null }
    }

    private fun loadUnsafe(id: String, dark: Boolean, w: Int, h: Int): Bitmap? = run {
        when {
            id == "system" -> null
            id.startsWith("file:") -> decodeFile(File(dir, id.removePrefix("file:")), maxSide = (maxOf(w, h) * 3 / 2).coerceIn(1080, 4096))
                ?: WallpaperRenderer.render("waves", dark, w, h)
            else -> WallpaperRenderer.render(id.removePrefix("builtin:"), dark, w, h)
        }
    }

    suspend fun thumbnail(id: String, dark: Boolean, w: Int, h: Int): Bitmap? = withContext(Dispatchers.Default) {
        runCatching { thumbUnsafe(id, dark, w, h) }.getOrNull()
    }

    private fun thumbUnsafe(id: String, dark: Boolean, w: Int, h: Int): Bitmap? = run {
        when {
            id == "system" -> null
            id.startsWith("file:") -> decodeFile(File(dir, id.removePrefix("file:")), maxSide = maxOf(w, h) * 2)
            else -> WallpaperRenderer.render(id.removePrefix("builtin:"), dark, w, h)
        }
    }

    private fun decodeFile(f: File, maxSide: Int): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Sets the system Home and Lock screen wallpaper to [id] (if it changed since last time). */
    suspend fun applyToSystem(id: String, dark: Boolean, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (id == "system") return@withContext false
        val signature = "$id|$dark"
        if (!force && prefs.getString("applied", null) == signature) return@withContext true
        try {
            val wm = WallpaperManager.getInstance(context)
            val flags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            if (id.startsWith("file:")) {
                FileInputStream(File(dir, id.removePrefix("file:"))).use { wm.setStream(it, null, true, flags) }
            } else {
                val (w, h) = screenSize()
                val bmp = WallpaperRenderer.render(id.removePrefix("builtin:"), dark, w, h)
                wm.setBitmap(bmp, null, true, flags)
                bmp.recycle()
            }
            prefs.edit().putString("applied", signature).apply()
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }
}
