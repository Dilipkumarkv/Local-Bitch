package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.GenerationStats
import com.example.ui.components.MetricsBanner
import com.example.ui.theme.LocalGgufTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun metrics_banner_screenshot() {
        val testStats = GenerationStats(
            promptTokens = 42,
            generatedTokens = 128,
            promptEvalMs = 85L,
            generationMs = 4200L,
            tokensPerSecond = 30.5,
            contextTokensUsed = 170
        )
        composeTestRule.setContent {
            LocalGgufTheme {
                MetricsBanner(stats = testStats)
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/metrics_banner.png")
    }
}
