package com.example.boschlaser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import org.junit.After
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() {
        finishAndCloseScenario(compose.activityRule.scenario)
    }
    private val labels = MeasurementLabels("Current fixture", "History fixture", "Measure", "Clear", "Export", "Empty fixture", "Delete")
    @Test fun emptyDisconnectedScreenDisablesDeviceAndHistoryActions() {
        compose.setContent { MaterialTheme { MeasurementScreen(emptyList(), "Empty distance", false, labels, { "More $it" }, {}, {}, {}, {}) } }
        compose.onNodeWithText(labels.measure).assertIsNotEnabled()
        compose.onNodeWithText(labels.clear).assertIsNotEnabled()
        compose.onNodeWithText(labels.export).assertIsNotEnabled()
        compose.onNodeWithText(labels.empty).assertIsDisplayed()
    }
    @Test fun historyActionsCarryRecordIdentityAndConnectedDeviceCanMeasure() {
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                MeasurementScreen(listOf(MeasurementRowState("new-id", "New fixture"), MeasurementRowState("old-id", "Old fixture")),
                    "100.5 mm", true, labels, { "More $it" }, { actions += "measure" }, { actions += "clear" },
                    { actions += "export" }, { actions += it })
            }
        }
        compose.onNodeWithText(labels.measure).performClick()
        compose.onNodeWithText(labels.clear).performClick()
        compose.onNodeWithText(labels.export).performClick()
        compose.onAllNodesWithText(labels.delete)[0].performClick()
        compose.runOnIdle { assertEquals(listOf("measure", "clear", "export", "new-id"), actions) }
    }
    @Test fun pagingShowsOlderRecordsWithoutChangingTheDocument() {
        val rows = (0..100).map { MeasurementRowState("id-$it", "Fixture $it") }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MeasurementScreen(rows, "Distance", false, labels, { "More $it" }, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Fixture 100").assertDoesNotExist()
        compose.onNodeWithText("More 1").performScrollTo().performClick()
        compose.onNodeWithText("Fixture 100").assertExists()
        compose.onNodeWithText("More 1").assertDoesNotExist()
        assertEquals(101, rows.size)
    }
    @Test fun androidHostKeepsDoubleMillimetersAndDeletesByIdForTimestampCollisions() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val service = com.example.boschlaser.shared.measurement.MeasurementService(
            com.example.boschlaser.shared.measurement.InMemoryMeasurementRepository())
        kotlinx.coroutines.runBlocking {
            service.record(com.example.boschlaser.shared.measurement.MeasurementRecord(
                com.example.boschlaser.shared.measurement.Measurement("older", 101.1, 1)))
            service.record(com.example.boschlaser.shared.measurement.MeasurementRecord(
                com.example.boschlaser.shared.measurement.Measurement("newer", 16777217.5, 1)))
        }
        var deletedId: String? = null
        compose.setContent {
            MaterialTheme { AndroidMeasurements(service, false, {}, {}, {}, { deletedId = it }) }
        }
        compose.onNodeWithText(context.getString(R.string.measurement_distance, "16777217.5")).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.measurement_delete))[0].performClick()
        compose.runOnIdle { assertEquals("newer", deletedId) }
        kotlinx.coroutines.runBlocking { service.delete(requireNotNull(deletedId)) }
        compose.onNodeWithText(context.getString(R.string.measurement_distance, "101.1")).assertIsDisplayed()
        assertEquals("older", service.records.value.single().measurement.id)
    }

}
