package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.ArabicStrings
import com.example.core.EnglishStrings
import com.example.domain.ReasoningLevel
import com.example.ui.components.ReasoningTierSegmentedControl
import com.example.ui.theme.CoreMindTheme
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.theme.cyberPaletteForTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string resources from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("Mooazb AI", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `bilingual localization and layout directions are consistent`() {
        assertEquals(LayoutDirection.Rtl, AppLanguage.AR.layoutDirection)
        assertEquals(LayoutDirection.Ltr, AppLanguage.EN.layoutDirection)
        assertTrue(ArabicStrings.appTitle.isNotBlank())
        assertTrue(EnglishStrings.appTitle.isNotBlank())
        assertTrue(ArabicStrings.ideHeaderTitle.isNotBlank())
        assertTrue(EnglishStrings.ideHeaderTitle.isNotBlank())
    }

    @Test
    fun `reasoning segmented control switches across all 4 tiers`() {
        var selected by mutableStateOf(ReasoningLevel.HIGH)

        composeTestRule.setContent {
            CoreMindTheme {
                ReasoningTierSegmentedControl(
                    selectedLevel = selected,
                    onSelect = { selected = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("reasoning_segment_high").assertIsSelected()

        composeTestRule.onNodeWithTag("reasoning_segment_low").performClick()
        composeTestRule.onNodeWithTag("reasoning_segment_low").assertIsSelected()
        assertEquals(ReasoningLevel.LOW, selected)

        composeTestRule.onNodeWithTag("reasoning_segment_medium").performClick()
        composeTestRule.onNodeWithTag("reasoning_segment_medium").assertIsSelected()
        assertEquals(ReasoningLevel.MEDIUM, selected)

        composeTestRule.onNodeWithTag("reasoning_segment_xhigh").performClick()
        composeTestRule.onNodeWithTag("reasoning_segment_xhigh").assertIsSelected()
        assertEquals(ReasoningLevel.XHIGH, selected)
    }

    @Test
    fun `animated cybernetic lab theme transitions smoothly across all 4 reasoning tiers`() {
        val palettes = ReasoningLevel.entries.map { cyberPaletteForTier(it) }
        assertEquals(4, palettes.map { it.accent }.toSet().size)
        assertEquals(4, palettes.map { it.background }.toSet().size)

        var activeTier by mutableStateOf(ReasoningLevel.LOW)
        var observedPrimary = Color.Unspecified
        var observedSurface = Color.Unspecified

        composeTestRule.setContent {
            CoreMindTheme(reasoningLevel = activeTier) {
                observedPrimary = MaterialTheme.colorScheme.primary
                observedSurface = LocalCyberPalette.current.surface
                ReasoningTierSegmentedControl(
                    selectedLevel = activeTier,
                    onSelect = { activeTier = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(cyberPaletteForTier(ReasoningLevel.LOW).accent, observedPrimary)
        assertEquals(cyberPaletteForTier(ReasoningLevel.LOW).surface, observedSurface)

        composeTestRule.onNodeWithTag("reasoning_segment_xhigh").performClick()
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        assertEquals(ReasoningLevel.XHIGH, activeTier)
        assertEquals(cyberPaletteForTier(ReasoningLevel.XHIGH).accent, observedPrimary)
        assertEquals(cyberPaletteForTier(ReasoningLevel.XHIGH).surface, observedSurface)
        assertNotEquals(
            cyberPaletteForTier(ReasoningLevel.LOW).accent,
            observedPrimary
        )
    }
}
