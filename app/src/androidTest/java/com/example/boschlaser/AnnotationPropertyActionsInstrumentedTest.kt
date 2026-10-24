package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.RecentMeasurements
import com.example.boschlaser.ui.AnnotationAreaPointAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationPropertyActionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun recentValuesUseHostOrderAndHideWithSelection() {
        val show = mutableStateOf(true)
        val labels = mutableStateOf(listOf("303 mm", "202 mm", "101 mm", "ignored mm"))
        val choices = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RecentMeasurements(labels.value, show.value) { choices += it } } }
        compose.onNodeWithText("ignored mm").assertDoesNotExist()
        compose.onNodeWithText("202 mm").performClick()
        compose.onNodeWithText("101 mm").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 2), choices); show.value = false }
        compose.onNodeWithText("303 mm").assertDoesNotExist()
        compose.runOnIdle { show.value = true; labels.value = emptyList() }
        compose.onNodeWithText("202 mm").assertDoesNotExist()
    }

    @Test fun pointDeletionOnlyAppearsForHostSelectedPoint() {
        val show = mutableStateOf(false)
        var clicks = 0
        compose.setContent { MaterialTheme {
            AnnotationAreaPointAction(show.value, "Delete point fixture", { clicks++ }, { Text("D") })
        } }
        compose.onNodeWithContentDescription("Delete point fixture").assertDoesNotExist()
        compose.runOnIdle { show.value = true }
        compose.onNodeWithContentDescription("Delete point fixture").performClick()
        compose.runOnIdle { assertEquals(1, clicks); show.value = false }
        compose.onNodeWithContentDescription("Delete point fixture").assertDoesNotExist()
    }
}
