package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.SketchPropertySheet
import com.example.boschlaser.ui.SketchPropertySheetStyle
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchPropertySheetInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private val resetKey = mutableStateOf(0)
    private val animateDismiss = mutableStateOf(false)
    private val present = mutableStateOf(false)
    private var dismissals = 0
    private var clicks = 0
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    private fun showSheet() {
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                SketchPropertySheet(visible.value, resetKey.value, animateDismiss.value, "Properties fixture",
                    SketchPropertySheetStyle(Color.White, Color.Black, 17.sp, 16.dp, 12.dp, 14.dp, 14.dp, 48.dp),
                    with(LocalDensity.current) { 8.dp.toPx() },
                    onDismiss = { dismissals++; animateDismiss.value = true; visible.value = false },
                    onPresenceChanged = { present.value = it }, modifier = Modifier.width(260.dp)) {
                    Button(onClick = { clicks++ }, modifier = Modifier.fillMaxWidth().height(72.dp).testTag("child")) { Text("Apply fixture") }
                    Box(Modifier.height(120.dp))
                }
            }
        }
    }
    private fun sheet() = compose.onNodeWithTag("sketch-property-sheet")
    private fun density() = sheet().fetchSemanticsNode().boundsInRoot.width / 260f

    @Test fun clicksAndHorizontalDragsDoNotBecomePanelDismissal() {
        showSheet()
        compose.onNodeWithTag("child").performClick()
        val initial = sheet().fetchSemanticsNode().boundsInRoot
        val density = density()
        sheet().performTouchInput { down(center); moveBy(Offset(80 * density, 20 * density)); up() }
        assertEquals(initial, sheet().fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(0, dismissals); assertTrue(present.value) }
    }

    @Test fun shortDownwardDragReturnsWithoutActivatingItsChild() {
        showSheet()
        val initial = sheet().fetchSemanticsNode().boundsInRoot
        val density = density()
        compose.onNodeWithTag("child").performTouchInput {
            down(center)
            moveBy(Offset(0f, 20 * density))
            moveBy(Offset(0f, 20 * density))
            up()
        }
        assertEquals(initial, sheet().fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(0, dismissals) }
    }

    @Test fun cancellingEvenALongDownwardDragAlwaysReturnsThePanel() {
        showSheet()
        val initial = sheet().fetchSemanticsNode().boundsInRoot
        val density = density()
        sheet().performTouchInput { down(center); moveBy(Offset(0f, 100 * density)); cancel() }
        assertEquals(initial, sheet().fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(0, dismissals); assertTrue(present.value) }
    }

    @Test fun dismissalKeepsPresenceUntilTheAnimationFinishes() {
        showSheet()
        val density = density()
        compose.mainClock.autoAdvance = false
        try {
            sheet().performTouchInput { down(center); moveBy(Offset(0f, 100 * density)); up() }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { assertEquals(1, dismissals); assertTrue(present.value) }
            sheet().assertExists()
            compose.mainClock.advanceTimeBy(200)
            sheet().assertDoesNotExist()
            compose.runOnIdle { assertFalse(present.value) }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun refreshedSelectionInterruptsDismissalAndRestoresPosition() {
        showSheet()
        val initial = sheet().fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { animateDismiss.value = true; visible.value = false }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { resetKey.value++; animateDismiss.value = false; visible.value = true }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(300)
            assertEquals(initial, sheet().fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertTrue(present.value); assertEquals(0, dismissals) }
        } finally { compose.mainClock.autoAdvance = true }
    }
}
