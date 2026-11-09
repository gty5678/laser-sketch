package com.example.boschlaser

import android.Manifest
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val permissions = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            for (permission in listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) {
                instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, permission)
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(permissions).around(compose)

    @Test fun homeLanguageChooserUpdatesResourcesAndSurvivesRecreation() {
        val original = AppCompatDelegate.getApplicationLocales()
        try {
            compose.activityRule.scenario.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
            compose.waitForIdle()
            for ((nativeName, expected) in listOf("简体中文" to "设置", "繁體中文" to "設定",
                "日本語" to "設定", "한국어" to "설정", "English" to "Settings")) {
                compose.onNodeWithText(compose.activity.getString(R.string.app_settings)).performClick()
                onView(withText(compose.activity.getString(R.string.app_language))).perform(click())
                onView(withText(nativeName)).perform(click())
                compose.waitForIdle()
                compose.onNodeWithText(expected).assertIsDisplayed()
                val context = compose.activity.applicationContext
                val directory = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                val csv = java.io.File.createTempFile("language-test-", ".csv", directory)
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context,
                        "${context.packageName}.fileprovider", csv)
                    kotlinx.coroutines.runBlocking {
                        com.example.boschlaser.platform.AndroidMeasurementCsv(context).export(uri, emptyList())
                    }
                    org.junit.Assert.assertEquals(
                        compose.activity.resources.getStringArray(R.array.measurement_csv_headers).joinToString(","),
                        csv.readLines().first())
                } finally { csv.delete() }
                compose.activityRule.scenario.recreate()
                compose.waitForIdle()
                compose.onNodeWithText(expected).assertIsDisplayed()
            }
            compose.onNodeWithText("Settings").performClick()
            onView(withText("Language")).perform(click())
            onView(withText("Follow system")).perform(click())
            compose.waitForIdle()
            org.junit.Assert.assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        } finally {
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
            compose.waitForIdle()
            finishAndCloseScenario(compose.activityRule.scenario)
        }
    }
}
