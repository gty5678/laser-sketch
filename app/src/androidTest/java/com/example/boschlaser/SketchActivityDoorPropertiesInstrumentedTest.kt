package com.example.boschlaser

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchActivityDoorPropertiesInstrumentedTest {
    @Test
    fun doorFlipActionsAreInTheRightSideInstancePropertyColumn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wall = SketchWall(id = "wall", start = SketchPoint(0f, 0f), end = SketchPoint(3000f, 0f))
        val door = SketchOpening(
            id = "door",
            type = SketchOpeningType.DOOR,
            wallId = wall.id,
            position = .5f,
        )
        val projectId = SketchProjectStore.create(context)
        check(SketchProjectStore.save(context, projectId, SketchState(walls = listOf(wall), openings = listOf(door))))

        val scenario = ActivityScenario.launch<SketchActivity>(
            Intent(context, SketchActivity::class.java).putExtra(SketchActivity.EXTRA_PROJECT_ID, projectId),
        )
        try {
            scenario.onActivity { activity ->
                SketchActivity::class.java.getDeclaredMethod("showSelection", SketchSelection::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, SketchSelection.Opening(door))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val panel = SketchActivity::class.java.getDeclaredField("propertyPanel").apply {
                    isAccessible = true
                }.get(activity) as LinearLayout
                val leftRight = panel.descendants(Button::class.java).single {
                    it.text == context.getString(R.string.door_flip_left_right)
                }
                val upDown = panel.descendants(Button::class.java).single {
                    it.text == context.getString(R.string.door_flip_up_down)
                }
                val widthInput = panel.descendants(EditText::class.java).single()
                val leftRightLocation = leftRight.screenLocation()
                val upDownLocation = upDown.screenLocation()
                val inputLocation = widthInput.screenLocation()

                assertTrue(leftRightLocation[0] >= inputLocation[0] + widthInput.width)
                assertEquals(leftRightLocation[0], upDownLocation[0])
                assertTrue(leftRightLocation[1] < upDownLocation[1])
            }
        } finally {
            scenario.close()
            SketchProjectStore.delete(context, projectId)
        }
    }

    private fun <T : View> View.descendants(type: Class<T>): List<T> {
        val result = mutableListOf<T>()
        if (type.isInstance(this)) result.add(type.cast(this)!!)
        if (this is ViewGroup) {
            for (index in 0 until childCount) result += getChildAt(index).descendants(type)
        }
        return result
    }

    private fun View.screenLocation() = IntArray(2).also(::getLocationOnScreen)
}
