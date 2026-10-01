package com.example.boschlaser

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchActivityDoorPropertiesInstrumentedTest {
    @Test
    fun doorFlipActionsAreVerticalCircularButtonsOnTheCanvasRightSide() {
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
                val sketchView = SketchActivity::class.java.getDeclaredField("sketchView").apply {
                    isAccessible = true
                }.get(activity) as SketchView
                SketchView::class.java.getDeclaredField("selectedOpeningId").apply {
                    isAccessible = true
                    set(sketchView, door.id)
                }
                SketchActivity::class.java.getDeclaredMethod("showSelection", SketchSelection::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, SketchSelection.Opening(door))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val panel = SketchActivity::class.java.getDeclaredField("propertyPanel").apply {
                    isAccessible = true
                }.get(activity) as LinearLayout
                val modeBar = SketchActivity::class.java.getDeclaredField("modeBar").apply {
                    isAccessible = true
                }.get(activity) as LinearLayout
                assertFalse(modeBar.descendants(ImageButton::class.java).any {
                    it.contentDescription == "保存草稿"
                })
                assertFalse(panel.descendants(Button::class.java).any {
                    it.text == context.getString(R.string.door_flip_left_right) ||
                        it.text == context.getString(R.string.door_flip_up_down)
                })

                val actions = SketchActivity::class.java.getDeclaredField("doorFlipActions").apply {
                    isAccessible = true
                }.get(activity) as LinearLayout
                assertEquals(View.VISIBLE, actions.visibility)
                assertEquals(LinearLayout.VERTICAL, actions.orientation)
                assertEquals(2, actions.childCount)
                val leftRight = actions.getChildAt(0) as ImageButton
                val upDown = actions.getChildAt(1) as ImageButton
                assertEquals(context.getString(R.string.door_flip_left_right), leftRight.contentDescription)
                assertEquals(context.getString(R.string.door_flip_up_down), upDown.contentDescription)
                assertEquals(GradientDrawable.OVAL, (leftRight.background as GradientDrawable).shape)
                assertEquals(GradientDrawable.OVAL, (upDown.background as GradientDrawable).shape)
                val leftRightLocation = leftRight.screenLocation()
                val upDownLocation = upDown.screenLocation()

                assertTrue(leftRightLocation[0] > activity.resources.displayMetrics.widthPixels / 2)
                assertEquals(leftRightLocation[0], upDownLocation[0])
                assertTrue(leftRightLocation[1] < upDownLocation[1])

                leftRight.performClick()
                upDown.performClick()
                val opening = (SketchActivity::class.java.getDeclaredField("sketchView").apply {
                    isAccessible = true
                }.get(activity) as SketchView).selectedOpening()
                assertTrue(opening?.hingeFlipped == true)
                assertTrue(opening?.flipped == true)
                val savedOpening = SketchProjectStore.load(context, projectId)?.state?.openings?.single()
                assertTrue(savedOpening?.hingeFlipped == true)
                assertTrue(savedOpening?.flipped == true)
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
