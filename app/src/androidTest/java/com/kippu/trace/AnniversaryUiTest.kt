package com.kippu.trace

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kippu.trace.model.AnniversaryType
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import com.kippu.trace.ui.components.AnniversarySettings
import com.kippu.trace.ui.components.AnniversarySettingsState
import com.kippu.trace.ui.screens.EditorScreen
import com.kippu.trace.utils.AnniversaryUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AnniversaryUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun millis(date: String) = AnniversaryUtils.millis(LocalDate.parse(date))
    @Test fun cycleSettingsLiveInEditorInsteadOfDateDialog() {
        var saved: DateEvent? = null
        compose.setContent { MaterialTheme { EditorScreen(onDismiss = {}, onSave = { saved = it }) } }
        compose.onNodeWithTag("anniversary_enabled_switch").assertIsOff()
        compose.onNodeWithText(context.getString(R.string.start_date_label)).performClick()
        compose.onNodeWithTag("anniversary_enabled_switch").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.cancel)).performScrollTo().performClick()
        compose.onNodeWithTag("anniversary_enabled_switch").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.anniversary_days_tab)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle { assertEquals(AnniversaryType.CUSTOM_DAYS, saved!!.anniversaryType) }
    }


    @Test fun repeatControlsValidateInputAndSaveSelection() {
        val state = AnniversarySettingsState()
        compose.setContent { MaterialTheme { AnniversarySettings(state, DisplayMode.COUNT_DOWN) } }
        compose.onNodeWithTag("repeat_enabled_switch").assertIsOff().performClick()
        compose.onNodeWithText(context.getString(R.string.day_unit)).performClick()
        compose.onNodeWithText(context.getString(R.string.anniversary_custom_days_label)).performTextReplacement("0")
        compose.runOnIdle { assertFalse(state.valid(DisplayMode.COUNT_DOWN)) }
        compose.onNodeWithText(context.getString(R.string.anniversary_custom_days_label)).performTextReplacement("30")
        compose.runOnIdle {
            val event = state.applyTo(DateEvent(title = "Repeat", targetDate = millis("2026-01-01"), isFuture = true, mode = DisplayMode.COUNT_DOWN))
            assertTrue(state.valid(event.mode))
            assertEquals(RepeatMode.CUSTOM_DAYS, event.repeatMode)
            assertEquals(30, event.repeatCustomDays)
        }
    }

}
