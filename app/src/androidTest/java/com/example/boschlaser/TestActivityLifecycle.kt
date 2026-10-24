package com.example.boschlaser

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals

/** Avoids helper-task races and verifies destruction before a test releases its scenario. */
internal fun <A : Activity> finishAndCloseScenario(scenario: ActivityScenario<A>) {
    if (scenario.state != Lifecycle.State.DESTROYED) scenario.onActivity { it.finish() }
    val deadline = SystemClock.uptimeMillis() + 5000
    while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(20)
    }
    assertEquals("The test activity must be destroyed", Lifecycle.State.DESTROYED, scenario.state)
    scenario.close()
}

/** Exercise a real pause without moving foreground ownership to the test helper's UID. */
internal fun <A : Activity> withPausedActivity(scenario: ActivityScenario<A>, block: () -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    var overlay: LifecyclePauseActivity? = null
    try {
        scenario.onActivity { it.startActivity(Intent(it, LifecyclePauseActivity::class.java)) }
        val deadline = SystemClock.uptimeMillis() + 5000
        while (overlay == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                overlay = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<LifecyclePauseActivity>().singleOrNull()
            }
            if (overlay == null) SystemClock.sleep(20)
        }
        org.junit.Assert.assertNotNull("The pause overlay must be resumed", overlay)
        assertEquals("The original page must be paused and visible", Lifecycle.State.STARTED, scenario.state)
        block()
    } finally {
        instrumentation.runOnMainSync { overlay?.finish() }
        instrumentation.waitForIdleSync()
    }
}
