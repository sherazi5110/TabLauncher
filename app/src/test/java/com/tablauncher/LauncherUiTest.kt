package com.tablauncher

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.tablauncher.data.IconShape
import com.tablauncher.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LauncherUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as TabLauncherApp
    private val ctl get() = app.controller!!

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/ui-shots").apply { mkdirs() }
        runCatching {
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }.onFailure { println("screenshot $name failed: $it") }
    }

    @Test
    fun customizeControlsChangeSettings() {
        rule.waitForIdle()
        shot("01-home")

        rule.runOnIdle { ctl.customizeOpen = true }
        rule.waitForIdle()
        shot("02-customize")

        rule.onNodeWithText("Light").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, app.settings.current.themeMode)

        rule.onNodeWithText("Aurora").performClick()
        rule.waitForIdle()
        assertEquals("builtin:aurora", app.settings.current.lightWallpaper)
        shot("03-wallpaper-picked")

        rule.onNodeWithText("Use on Lock screen too").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue("lock screen switch", app.settings.current.lockScreenSync)

        rule.onNodeWithText("Icons").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Circle").performClick()
        rule.waitForIdle()
        assertEquals(IconShape.CIRCLE, app.settings.current.iconShape)
        shot("04-icons")

        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        assertTrue(!ctl.customizeOpen)
        shot("05-after-done")
    }

    @Test
    fun hideAndUnhideToggles() {
        rule.waitForIdle()
        rule.runOnIdle { ctl.customizeOpen = true }
        rule.waitForIdle()
        rule.onNodeWithText("More").performClick()
        rule.waitForIdle()
        shot("06-more")
    }

    @Test
    fun swipeDownOpensSearch() {
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipeDown(startY = centerY - 200f, endY = centerY + 300f) }
        rule.waitForIdle()
        shot("07-after-swipe")
        assertTrue("search should open", ctl.searchOpen)
    }
}
