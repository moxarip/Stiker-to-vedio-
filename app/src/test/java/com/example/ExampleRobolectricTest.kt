package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.BuiltinStickerFactory
import com.example.video.AnimatedStickerDecoder
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAnimatedWebpDecodingAndConverterTab() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builtins = BuiltinStickerFactory.ensureBuiltinStickers(context)
        assertTrue(builtins.isNotEmpty())

        // Verify that our AnimatedStickerDecoder extracts all 8 original ANMF frames from the animated WebP
        val decoded = AnimatedStickerDecoder.decodeFromUri(context, builtins.first().uri)
        assertTrue(decoded != null && decoded.isAnimated && decoded.frames.size == 8)

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("nav_tab_video_studio").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_video_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("video_studio_screen").assertExists()
        composeTestRule.onNodeWithTag("start_mp4_encoding_button").assertExists()
    }
}
