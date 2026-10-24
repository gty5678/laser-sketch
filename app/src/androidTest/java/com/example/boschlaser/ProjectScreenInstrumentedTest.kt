package com.example.boschlaser

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import org.junit.After
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.ProjectCardState
import com.example.boschlaser.ui.ProjectLabels
import com.example.boschlaser.ui.ProjectScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun finishScreen() {
        finishAndCloseScenario(compose.activityRule.scenario)
    }
    private val labels = ProjectLabels("Empty fixture", "Actions", "Rename", "Delete", "Export")
    @Test fun cardAndMenuActionsUseStableProjectIdentity() {
        val actions = mutableListOf<Pair<String, String>>()
        compose.setContent {
            MaterialTheme {
                ProjectScreen(listOf(ProjectCardState("first", "First fixture", "Time"), ProjectCardState("second", "Second fixture", "Time")),
                    labels,
                    onOpen = { actions += "open" to it },
                    onRename = { actions += "rename" to it },
                    onDelete = { actions += "delete" to it },
                    onExport = { actions += "export" to it },
                    thumbnail = {})
            }
        }
        compose.onNodeWithText("Second fixture").performClick()
        for (label in listOf("Rename", "Delete", "Export")) {
            compose.onAllNodesWithText("Actions")[1].performClick()
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText(label).assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals(listOf("open" to "second", "rename" to "second", "delete" to "second", "export" to "second"), actions) }
    }
    @Test fun emptyCatalogDisplaysLocalizedMessageWithoutProjectActions() {
        compose.setContent {
            MaterialTheme { ProjectScreen(emptyList(), labels, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText(labels.empty).assertIsDisplayed()
        compose.onNodeWithText(labels.actions).assertDoesNotExist()
    }
    @Test fun androidHostObservesRenameAndDeletionFromApplicationCatalog() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val services = ProjectServices.get(context)
        val fixtureName = "Live catalog ${java.util.UUID.randomUUID()}"
        val project = kotlinx.coroutines.runBlocking { services.createSketch(fixtureName).await() }
        try {
            compose.setContent {
                MaterialTheme {
                    com.example.boschlaser.ui.AndroidProjectList(services, true, labels, { "Time" }, {}, {}, {}, {})
                }
            }
            compose.onNodeWithText(fixtureName).assertIsDisplayed()
            kotlinx.coroutines.runBlocking { services.rename(project.manifest.projectId, "Renamed $fixtureName").await() }
            compose.onNodeWithText("Renamed $fixtureName").assertIsDisplayed()
            compose.onNodeWithText(fixtureName).assertDoesNotExist()
            kotlinx.coroutines.runBlocking { services.delete(project.manifest.projectId).await() }
            compose.onNodeWithText("Renamed $fixtureName").assertDoesNotExist()
        } finally { kotlinx.coroutines.runBlocking { services.delete(project.manifest.projectId).await() } }
    }

}
