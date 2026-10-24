package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.platform.showBleDevicePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBleDevicePickerInstrumentedTest {
    @Test fun candidatesAreVisibleAndSelectionReportsTheCorrectIndex() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var selected = -1
        var dialog: androidx.appcompat.app.AlertDialog? = null
        try {
            scenario.onActivity {
                dialog = showBleDevicePicker(ContextThemeWrapper(it, R.style.Theme_LaserSketch),
                    listOf("Candidate fixture A", "Candidate fixture B")) { index -> selected = index }
            }
            onView(withText("Candidate fixture B")).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.onActivity {
                val list = requireNotNull(dialog).listView
                assertTrue(list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1)))
                assertEquals(1, selected)
                assertFalse(requireNotNull(dialog).isShowing)
            }
        } finally { finishAndCloseScenario(scenario) }
    }

    @Test fun cancellationDoesNotSelectADevice() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var selected = -1
        var dialog: androidx.appcompat.app.AlertDialog? = null
        try {
            scenario.onActivity {
                dialog = showBleDevicePicker(ContextThemeWrapper(it, R.style.Theme_LaserSketch),
                    listOf("Candidate fixture")) { index -> selected = index }
            }
            onView(withText(R.string.action_cancel)).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.onActivity {
                requireNotNull(dialog).getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertFalse(requireNotNull(dialog).isShowing)
                assertEquals(-1, selected)
            }
        } finally { finishAndCloseScenario(scenario) }
    }
}
