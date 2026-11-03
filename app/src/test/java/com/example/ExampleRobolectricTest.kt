package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    fun testSelectStickerAndOpenStudio() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("open_selected_in_studio_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("open_selected_in_studio_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("video_studio_screen").assertExists()
        composeTestRule.onNodeWithTag("start_mp4_encoding_button").assertExists()
    }
}
