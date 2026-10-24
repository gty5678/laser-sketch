package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.rules.ExternalResource

/** Keeps native drawing/input tests visible so OEM background freezing cannot suspend the runner. */
internal class ForegroundTestHostRule : ExternalResource() {
    private var scenario: ActivityScenario<ComponentActivity>? = null
    override fun before() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }
    override fun after() {
        val host = scenario ?: return
        finishAndCloseScenario(host)
        scenario = null
    }
}
