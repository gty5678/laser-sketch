package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationActionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }
    @Test fun callbacksVisibilityAndLowerDeletePositionFollowHostState() {
        val finish = mutableStateOf(true)
        val delete = mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            AnnotationActions(finish.value, delete.value, "Finish fixture", "Delete fixture",
                { actions += "finish" }, { actions += "delete" }, { Text("F") }, { Text("D") })
        } }
        compose.onNodeWithContentDescription("Delete fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Finish fixture").performClick()
        compose.runOnIdle { delete.value = true }
        val finishBounds = compose.onNodeWithContentDescription("Finish fixture").fetchSemanticsNode().boundsInRoot
        val deleteBounds = compose.onNodeWithContentDescription("Delete fixture").fetchSemanticsNode().boundsInRoot
        assertEquals(finishBounds.left, deleteBounds.left, 1f)
        assertEquals(finishBounds.width, finishBounds.height, 1f)
        assertEquals(finishBounds.height * 64f / 52f, deleteBounds.top - finishBounds.top, 1f)
        compose.onNodeWithContentDescription("Delete fixture").performClick()
        compose.runOnIdle { assertEquals(listOf("finish", "delete"), actions); finish.value = false; delete.value = false }
        compose.onNodeWithContentDescription("Finish fixture").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete fixture").assertDoesNotExist()
    }
}
