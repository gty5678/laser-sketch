package com.example.boschlaser

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private class SwipeDismissPanel(context: Context) : LinearLayout(context) {
    var onPanelVisibilityChanged: (() -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var draggingDown = false

    override fun setVisibility(visibility: Int) {
        if (visibility == View.VISIBLE) {
            animate().cancel()
            translationY = 0f
            alpha = 1f
        }
        super.setVisibility(visibility)
        post { onPanelVisibilityChanged?.invoke() }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                draggingDown = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dy > touchSlop && dy > abs(dx)) {
                    draggingDown = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return draggingDown || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                draggingDown = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!draggingDown && dy > touchSlop && dy > abs(dx)) draggingDown = true
                if (draggingDown) {
                    translationY = max(0f, dy)
                    alpha = (1f - translationY / max(1f, height.toFloat())).coerceIn(.35f, 1f)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingDown) {
                    val dismissDistance = max(48f * resources.displayMetrics.density, height * .18f)
                    if (event.actionMasked == MotionEvent.ACTION_UP && translationY >= dismissDistance) {
                        animate().translationY(height.toFloat()).alpha(0f).setDuration(160L).withEndAction {
                            visibility = View.GONE
                        }.start()
                    } else {
                        animate().translationY(0f).alpha(1f).setDuration(160L).start()
                    }
                    draggingDown = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

class SketchActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PROJECT_ID = "sketch_project_id"
        const val EXTRA_MEASUREMENTS_MM = "sketch_measurements_mm"
        private const val PREFS_NAME = "sketch_settings"
        private const val PREF_GRID_DISPLAY_STEP_MM = "grid_display_step_mm"
        private const val PREF_MAGNIFIER_ZOOM = "magnifier_zoom"
    }

    private lateinit var sketchView: SketchView
    private lateinit var propertyPanel: LinearLayout
    private lateinit var modeBar: LinearLayout
    private lateinit var placementActions: LinearLayout
    private lateinit var deleteSelectionButton: ImageButton
    private lateinit var straightenWallButton: ImageButton
    private lateinit var snapGridSwitch: SwitchCompat
    private lateinit var joinWallsButton: ImageButton
    private lateinit var extendWallButton: ImageButton
    private val modeButtons = linkedMapOf<SketchMode, ImageButton>()
    private var projectId: String? = null
    private var latestMeasurements = emptyList<Float>()
    private var moveStart = false
    private var currentMode = SketchMode.SELECT
    private var joinWallsActive = false
    private var firstJoinWallId: String? = null
    private var firstJoinWallPickPoint: SketchPoint? = null
    private var extendWallActive = false
    private var firstExtendWallId: String? = null
    private var systemBottomInset = 0
    private var systemTopInset = 0

    private val measurementListener: (List<Float>) -> Unit = { values ->
        runOnUiThread {
            latestMeasurements = values
            val selection = sketchView.selectedWall()?.let(SketchSelection::Wall)
            if (selection != null && !joinWallsActive && !extendWallActive) showSelection(selection)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        projectId = savedInstanceState?.getString(EXTRA_PROJECT_ID) ?: intent.getStringExtra(EXTRA_PROJECT_ID)
        latestMeasurements = intent.getFloatArrayExtra(EXTRA_MEASUREMENTS_MM)?.toList().orEmpty().asReversed()
        buildInterface()
        val id = projectId
        if (id == null) {
            toast("草稿文件不存在")
            finish()
        } else {
            val project = SketchProjectStore.load(this, id)
            if (project == null) { toast("无法打开草稿"); finish() }
            else sketchView.restoreState(project.state, adoptWallThickness = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        projectId?.let { outState.putString(EXTRA_PROJECT_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        MeasurementStore.addListener(measurementListener)
    }

    override fun onStop() {
        MeasurementStore.removeListener(measurementListener)
        saveProject()
        super.onStop()
    }

    private fun buildInterface() {
        val root = FrameLayout(this)
        sketchView = SketchView(this).apply {
            onSelectionChanged = { if (!joinWallsActive && !extendWallActive) showSelection(it) }
            onWallPicked = { wall, point ->
                when {
                    joinWallsActive -> handleJoinWallPicked(wall, point)
                    extendWallActive -> handleExtendWallPicked(wall)
                }
            }
        }
        root.addView(sketchView, FrameLayout.LayoutParams(-1, -1))

        modeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setBackgroundColor(Color.argb(245, 255, 255, 255))
            elevation = dp(8).toFloat()
        }
        modeBar.addView(modeIconButton(R.drawable.ic_back, "返回") { finish() })
        modeBar.addView(modeIconButton(R.drawable.ic_save, "保存草稿") {
            if (saveProject()) toast("草稿已保存") else toast("保存失败，请重试")
        })
        listOf(
            Triple(SketchMode.SELECT, R.drawable.ic_select, "选择与移动"),
            Triple(SketchMode.WALL, R.drawable.ic_wall, "放置墙体"),
            Triple(SketchMode.RECT_COLUMN, R.drawable.ic_column, "放置柱子"),
            Triple(SketchMode.DOOR, R.drawable.ic_door, "放置门"),
            Triple(SketchMode.WINDOW, R.drawable.ic_window, "放置窗"),
        ).forEach { (mode, icon, description) ->
            val button = modeIconButton(icon, description) { setMode(mode) }
            modeButtons[mode] = button
            modeBar.addView(button)
        }
        modeBar.addView(modeIconButton(R.drawable.ic_settings, "绘图设置") { showDrawingSettings() })
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(modeBar)
        }
        val editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(5))
            setBackgroundColor(Color.rgb(246, 249, 248))
        }
        joinWallsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_join_walls)
            imageTintList = ColorStateList.valueOf(Color.rgb(0, 102, 76))
            contentDescription = "连接两墙"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBackground(Color.WHITE, 10)
            setOnClickListener {
                if (joinWallsActive) stopWallJoinMode() else startWallJoinMode()
            }
        }
        editBar.addView(joinWallsButton, LinearLayout.LayoutParams(dp(48), dp(44)))
        extendWallButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_extend_wall)
            imageTintList = ColorStateList.valueOf(Color.rgb(0, 102, 76))
            contentDescription = "延长墙到另一面墙"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBackground(Color.WHITE, 10)
            setOnClickListener {
                if (extendWallActive) stopWallExtendMode() else startWallExtendMode()
            }
        }
        editBar.addView(extendWallButton, LinearLayout.LayoutParams(dp(48), dp(44)).apply {
            marginStart = dp(6)
        })
        val topToolbars = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(250, 255, 255, 255))
            elevation = dp(8).toFloat()
            addView(scroll, LinearLayout.LayoutParams(-1, -2))
            addView(editBar, LinearLayout.LayoutParams(-1, dp(52)))
        }
        root.addView(topToolbars, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        propertyPanel = SwipeDismissPanel(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            setBackgroundColor(Color.argb(248, 255, 255, 255))
            elevation = dp(14).toFloat()
            visibility = View.GONE
        }
        root.addView(propertyPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(14))
        })
        snapGridSwitch = SwitchCompat(this).apply {
            text = "网格吸附"
            textSize = 14f
            setTextColor(Color.rgb(35, 42, 42))
            isChecked = true
            setPadding(dp(14), dp(6), dp(12), dp(6))
            elevation = dp(10).toFloat()
            background = roundedBackground(Color.argb(248, 255, 255, 255), 22)
            setOnCheckedChangeListener { _, enabled -> sketchView.setGridSnapEnabled(enabled) }
        }
        root.addView(snapGridSwitch, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(14)
        })
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).let { preferences ->
            preferences.getFloat(PREF_GRID_DISPLAY_STEP_MM, SketchView.DEFAULT_GRID_DISPLAY_STEP_MM)
                .let { sketchView.setGridDisplayStepMm(it) }
            preferences.getFloat(PREF_MAGNIFIER_ZOOM, SketchView.DEFAULT_MAGNIFIER_ZOOM)
                .let { sketchView.setMagnifierZoom(it) }
        }
        (propertyPanel as SwipeDismissPanel).onPanelVisibilityChanged = { updateSnapSwitchPosition() }
        propertyPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSnapSwitchPosition() }
        placementActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(floatingIconButton(R.drawable.ic_undo, "撤销上一步", Color.rgb(88, 96, 101)) {
                if (!sketchView.undoPlacement()) toast("没有可撤销的操作")
            }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(10) })
            deleteSelectionButton = floatingIconButton(
                R.drawable.ic_delete,
                "删除选中对象",
                Color.rgb(190, 55, 55),
            ) {
                if (!sketchView.deleteSelected()) toast("请先选择对象")
            }.apply { visibility = View.GONE }
            addView(deleteSelectionButton, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(10) })
            addView(floatingIconButton(R.drawable.ic_confirm, "完成放置", Color.rgb(0, 122, 92)) {
                setMode(SketchMode.SELECT)
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        root.addView(placementActions, FrameLayout.LayoutParams(-2, dp(56), Gravity.TOP or Gravity.END).apply {
            marginEnd = dp(10); topMargin = dp(64)
        })
        straightenWallButton = floatingIconButton(
            R.drawable.ic_straighten,
            "将墙体拉成水平或垂直",
            Color.rgb(0, 122, 92),
        ) {
            if (!sketchView.straightenSelectedWall(moveStart)) toast("请先选择墙体")
        }.apply { visibility = View.GONE }
        root.addView(straightenWallButton, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(14)
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(topToolbars) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTopInset = bars.top
            view.setPadding(0, bars.top, 0, 0)
            updatePlacementActionsPosition()
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(propertyPanel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBottomInset = bars.bottom
            updatePropertyPanelPosition()
            updateSnapSwitchPosition()
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(placementActions) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTopInset = bars.top
            updatePlacementActionsPosition()
            insets
        }
        setMode(SketchMode.SELECT)
    }

    private fun setMode(mode: SketchMode) {
        if (joinWallsActive) stopWallJoinMode()
        if (extendWallActive) stopWallExtendMode()
        currentMode = mode
        sketchView.setMode(mode)
        modeButtons.forEach { (item, button) ->
            button.setColorFilter(if (item == mode) Color.WHITE else Color.rgb(0, 102, 76))
            button.background = if (item == mode) roundedBackground(Color.rgb(0, 122, 92), 10) else roundedBackground(Color.TRANSPARENT, 10)
        }
        placementActions.visibility = View.VISIBLE
        val undoButton = placementActions.getChildAt(0)
        val confirmButton = placementActions.getChildAt(2)
        confirmButton.visibility = if (mode == SketchMode.SELECT) View.GONE else View.VISIBLE
        (undoButton.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.marginEnd = if (mode == SketchMode.SELECT && deleteSelectionButton.visibility != View.VISIBLE) 0 else dp(10)
            undoButton.layoutParams = params
        }
        when (mode) {
            SketchMode.DOOR -> showOpeningPlacementProperties(SketchOpeningType.DOOR)
            SketchMode.WINDOW -> showOpeningPlacementProperties(SketchOpeningType.WINDOW)
            SketchMode.SELECT -> Unit
            else -> propertyPanel.visibility = View.GONE
        }
        updatePropertyPanelPosition()
    }

    private fun startWallJoinMode() {
        setMode(SketchMode.SELECT)
        joinWallsActive = true
        firstJoinWallId = null
        firstJoinWallPickPoint = null
        sketchView.setWallPickMode(true)
        showSelection(null)
        updateWallJoinToolbar()
    }

    private fun stopWallJoinMode(showSelectedWall: Boolean = false) {
        joinWallsActive = false
        firstJoinWallId = null
        firstJoinWallPickPoint = null
        sketchView.setWallPickMode(false)
        updateWallJoinToolbar()
        if (showSelectedWall) {
            showSelection(sketchView.selectedWall()?.let(SketchSelection::Wall))
        }
    }

    private fun handleJoinWallPicked(wall: SketchWall, pickPoint: SketchPoint) {
        if (!joinWallsActive) return
        val firstId = firstJoinWallId
        if (firstId == null) {
            firstJoinWallId = wall.id
            firstJoinWallPickPoint = pickPoint
            sketchView.excludeWallFromNextPick(wall.id)
            updateWallJoinToolbar()
            return
        }
        if (firstId == wall.id) {
            toast("第二面墙不能与第一面相同")
            return
        }
        val firstPickPoint = firstJoinWallPickPoint ?: return
        when (sketchView.joinWallsAtIntersection(firstId, firstPickPoint, wall.id, pickPoint)) {
            WallJoinResult.SUCCESS -> {
                stopWallJoinMode(showSelectedWall = true)
                toast("两面墙已连接")
            }
            WallJoinResult.PARALLEL -> {
                firstJoinWallId = null
                firstJoinWallPickPoint = null
                sketchView.setWallPickMode(true)
                updateWallJoinToolbar()
                toast("平行墙没有交点")
            }
            WallJoinResult.OPENING_CONFLICT -> resetWallJoinSelection("裁剪会切掉门窗，请先移动门窗或保留另一侧")
            WallJoinResult.SAME_WALL -> toast("请选择两面不同的墙")
            WallJoinResult.WALL_NOT_FOUND -> {
                firstJoinWallId = null
                firstJoinWallPickPoint = null
                sketchView.setWallPickMode(true)
                updateWallJoinToolbar()
            }
        }
    }

    private fun updateWallJoinToolbar() {
        val foreground = if (joinWallsActive) Color.WHITE else Color.rgb(0, 102, 76)
        joinWallsButton.imageTintList = ColorStateList.valueOf(foreground)
        joinWallsButton.contentDescription = if (joinWallsActive) "取消连接两墙" else "连接两墙"
        joinWallsButton.background = roundedBackground(
            if (joinWallsActive) Color.rgb(0, 122, 92) else Color.WHITE,
            10,
        )
    }

    private fun resetWallJoinSelection(message: String) {
        firstJoinWallId = null
        firstJoinWallPickPoint = null
        sketchView.setWallPickMode(true)
        updateWallJoinToolbar()
        toast(message)
    }

    private fun startWallExtendMode() {
        setMode(SketchMode.SELECT)
        extendWallActive = true
        firstExtendWallId = null
        sketchView.setWallPickMode(true)
        showSelection(null)
        updateWallExtendToolbar()
        toast("请选择要延长的第一面墙")
    }

    private fun stopWallExtendMode(showSelectedWall: Boolean = false) {
        extendWallActive = false
        firstExtendWallId = null
        sketchView.setWallPickMode(false)
        updateWallExtendToolbar()
        if (showSelectedWall) {
            showSelection(sketchView.selectedWall()?.let(SketchSelection::Wall))
        }
    }

    private fun handleExtendWallPicked(wall: SketchWall) {
        if (!extendWallActive) return
        val firstId = firstExtendWallId
        if (firstId == null) {
            firstExtendWallId = wall.id
            sketchView.excludeWallFromNextPick(wall.id)
            toast("请选择作为边界的第二面墙")
            return
        }
        when (sketchView.extendWallToWall(firstId, wall.id)) {
            WallExtendResult.SUCCESS -> {
                stopWallExtendMode(showSelectedWall = true)
                toast("第一面墙已延长到目标墙")
            }
            WallExtendResult.PARALLEL -> resetWallExtendSelection("两墙平行，无法延长")
            WallExtendResult.ALREADY_REACHES -> resetWallExtendSelection("第一面墙已到达目标墙，无需延长")
            WallExtendResult.TARGET_MISSED -> resetWallExtendSelection("交点不在第二面墙上")
            WallExtendResult.OPENING_CONFLICT -> resetWallExtendSelection("延长关联墙后门窗空间不足，操作已取消")
            WallExtendResult.SAME_WALL -> toast("请选择两面不同的墙")
            WallExtendResult.WALL_NOT_FOUND -> resetWallExtendSelection("墙体已变化，请重新选择")
        }
    }

    private fun resetWallExtendSelection(message: String) {
        firstExtendWallId = null
        sketchView.setWallPickMode(true)
        toast(message)
    }

    private fun updateWallExtendToolbar() {
        val foreground = if (extendWallActive) Color.WHITE else Color.rgb(0, 102, 76)
        extendWallButton.imageTintList = ColorStateList.valueOf(foreground)
        extendWallButton.contentDescription = if (extendWallActive) "取消延长墙" else "延长墙到另一面墙"
        extendWallButton.background = roundedBackground(
            if (extendWallActive) Color.rgb(0, 122, 92) else Color.WHITE,
            10,
        )
    }

    private fun showSelection(selection: SketchSelection?) {
        propertyPanel.removeAllViews()
        deleteSelectionButton.visibility =
            if (selection != null && currentMode == SketchMode.SELECT) View.VISIBLE else View.GONE
        straightenWallButton.visibility =
            if (selection is SketchSelection.Wall && currentMode == SketchMode.SELECT) View.VISIBLE else View.GONE
        val undoButton = placementActions.getChildAt(0)
        (undoButton.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.marginEnd = if (currentMode == SketchMode.SELECT && selection == null) 0 else dp(10)
            undoButton.layoutParams = params
        }
        if (selection == null) {
            when (currentMode) {
                SketchMode.DOOR -> showOpeningPlacementProperties(SketchOpeningType.DOOR)
                SketchMode.WINDOW -> showOpeningPlacementProperties(SketchOpeningType.WINDOW)
                else -> propertyPanel.visibility = View.GONE
            }
            return
        }
        propertyPanel.visibility = View.VISIBLE
        updatePropertyPanelPosition()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = when (selection) {
                is SketchSelection.Wall -> "墙体属性"
                is SketchSelection.Column -> if (selection.column.type == SketchColumnType.CIRCLE) "圆柱属性" else "方柱属性"
                is SketchSelection.Opening -> if (selection.opening.type == SketchOpeningType.DOOR) "门属性" else "窗属性"
            }
            textSize = 17f; setTextColor(Color.rgb(25, 25, 25))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        propertyPanel.addView(header)
        when (selection) {
            is SketchSelection.Wall -> showWallProperties(selection.wall)
            is SketchSelection.Column -> showColumnProperties(selection.column)
            is SketchSelection.Opening -> showOpeningProperties(selection.opening)
        }
    }

    private fun showWallProperties(wall: SketchWall) {
        val current = wall.measuredLength ?: kotlin.math.hypot(wall.end.x - wall.start.x, wall.end.y - wall.start.y)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = numberInput(current.roundToInt().toString())
        row.addView(TextView(this).apply { text = "长度(mm)  "; textSize = 15f })
        row.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(Button(this).apply {
            text = if (moveStart) "移动起点" else "移动终点"; isAllCaps = false
            setOnClickListener { moveStart = !moveStart; showSelection(sketchView.selectedWall()?.let(SketchSelection::Wall)) }
        })
        row.addView(Button(this).apply {
            text = "应用"; isAllCaps = false
            setOnClickListener {
                val value = input.text.toString().toFloatOrNull()
                if (value == null || !sketchView.applySelectedWallLength(value, moveStart)) {
                    toast(if (sketchView.lastWallEditHadOpeningConflict()) "墙长不足以容纳现有门窗" else "请输入有效长度")
                }
            }
        })
        propertyPanel.addView(row)
        val thicknessRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val thicknessInput = numberInput(wall.thickness.roundToInt().toString())
        thicknessRow.addView(TextView(this).apply { text = "墙厚(mm)  "; textSize = 15f })
        thicknessRow.addView(thicknessInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        thicknessRow.addView(Button(this).apply {
            text = "应用墙厚"; isAllCaps = false
            setOnClickListener {
                val value = thicknessInput.text.toString().toFloatOrNull()
                if (value == null || !sketchView.updateSelectedWallThickness(value)) toast("墙厚请输入50～1000毫米")
            }
        })
        propertyPanel.addView(thicknessRow)
        if (latestMeasurements.isNotEmpty()) {
            val measurements = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            latestMeasurements.take(3).forEach { value ->
                measurements.addView(Button(this).apply {
                    text = "${value.roundToInt()} mm"; isAllCaps = false
                    setOnClickListener { sketchView.applySelectedWallLength(value, moveStart) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            propertyPanel.addView(measurements)
        }
    }

    private fun showColumnProperties(column: SketchColumn) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val width = numberInput(column.width.roundToInt().toString())
        val depth = numberInput(column.depth.roundToInt().toString())
        row.addView(TextView(this).apply { text = "宽/直径" })
        row.addView(width, LinearLayout.LayoutParams(0, dp(48), 1f))
        if (column.type == SketchColumnType.RECTANGLE) {
            row.addView(TextView(this).apply { text = "  深" })
            row.addView(depth, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        row.addView(Button(this).apply {
            text = "应用"; isAllCaps = false
            setOnClickListener {
                val w = width.text.toString().toFloatOrNull()
                val d = if (column.type == SketchColumnType.CIRCLE) w else depth.text.toString().toFloatOrNull()
                if (w == null || d == null || !sketchView.updateSelectedColumnSize(w, d)) toast("请输入有效尺寸")
            }
        })
        propertyPanel.addView(row)
    }

    private fun showOpeningProperties(opening: SketchOpening) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val width = numberInput(opening.width.roundToInt().toString())
        row.addView(TextView(this).apply { text = "宽度(mm)  " })
        row.addView(width, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(Button(this).apply {
            text = "应用"; isAllCaps = false
            setOnClickListener {
                val value = width.text.toString().toFloatOrNull()
                if (value == null || !sketchView.updateSelectedOpening(value)) toast("请输入有效宽度")
            }
        })
        if (opening.type == SketchOpeningType.DOOR) row.addView(Button(this).apply {
            text = "翻转"; isAllCaps = false; setOnClickListener { sketchView.updateSelectedOpening(opening.width, true) }
        })
        propertyPanel.addView(row)
    }

    private fun showOpeningPlacementProperties(type: SketchOpeningType) {
        propertyPanel.removeAllViews()
        propertyPanel.visibility = View.VISIBLE
        updatePropertyPanelPosition()
        propertyPanel.addView(TextView(this).apply {
            text = if (type == SketchOpeningType.DOOR) "放置门 · 点击墙体确定位置" else "放置窗 · 点击墙体确定位置"
            textSize = 17f
            setTextColor(Color.rgb(25, 25, 25))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val width = numberInput(sketchView.placementOpeningWidth(type).roundToInt().toString())
        row.addView(TextView(this).apply { text = "宽度(mm)  "; textSize = 15f })
        row.addView(width, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(Button(this).apply {
            text = "应用"; isAllCaps = false
            setOnClickListener {
                val value = width.text.toString().toFloatOrNull()
                if (value == null || !sketchView.updatePlacementOpeningWidth(type, value)) toast("请输入100～5000毫米")
                else toast("后续放置将使用 ${value.roundToInt()} mm")
            }
        })
        propertyPanel.addView(row)
    }

    private fun showDrawingSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        content.addView(TextView(this).apply {
            text = "背景网格间距（毫米）"
            textSize = 14f
            setTextColor(Color.rgb(80, 86, 86))
        })
        val gridInput = numberInput(sketchView.gridDisplayStepMm().toString()).apply {
            hint = "例如 100"
            selectAll()
        }
        content.addView(gridInput, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8)
        })
        content.addView(TextView(this).apply {
            text = "放大镜倍率（${SketchView.MIN_MAGNIFIER_ZOOM.toInt()}～${SketchView.MAX_MAGNIFIER_ZOOM.toInt()} 倍）"
            textSize = 14f
            setTextColor(Color.rgb(80, 86, 86))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val magnifierInput = numberInput(sketchView.magnifierZoom().toString()).apply {
            hint = "例如 2.6"
        }
        content.addView(magnifierInput, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8)
        })
        AlertDialog.Builder(this)
            .setTitle("绘图设置")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("应用") { _, _ ->
                val gridValue = gridInput.text.toString().toFloatOrNull()
                val magnifierValue = magnifierInput.text.toString().toFloatOrNull()
                if (gridValue == null || gridValue !in SketchView.MIN_GRID_DISPLAY_STEP_MM..SketchView.MAX_GRID_DISPLAY_STEP_MM) {
                    toast("请输入 ${SketchView.MIN_GRID_DISPLAY_STEP_MM.toInt()}～${SketchView.MAX_GRID_DISPLAY_STEP_MM.toInt()} 毫米")
                } else if (magnifierValue == null || magnifierValue !in SketchView.MIN_MAGNIFIER_ZOOM..SketchView.MAX_MAGNIFIER_ZOOM) {
                    toast("放大镜倍率请输入 ${SketchView.MIN_MAGNIFIER_ZOOM.toInt()}～${SketchView.MAX_MAGNIFIER_ZOOM.toInt()}")
                } else {
                    sketchView.setGridDisplayStepMm(gridValue)
                    sketchView.setMagnifierZoom(magnifierValue)
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putFloat(PREF_GRID_DISPLAY_STEP_MM, gridValue)
                        .putFloat(PREF_MAGNIFIER_ZOOM, magnifierValue)
                        .apply()
                }
            }
            .show()
    }

    private fun updatePropertyPanelPosition() {
        if (!::propertyPanel.isInitialized) return
        (propertyPanel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = systemBottomInset + dp(14)
            propertyPanel.layoutParams = params
        }
    }

    private fun updateSnapSwitchPosition() {
        if (!::snapGridSwitch.isInitialized || !::propertyPanel.isInitialized) return
        val panelOffset = if (propertyPanel.visibility == View.VISIBLE) propertyPanel.height + dp(12) else 0
        (snapGridSwitch.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = systemBottomInset + dp(14) + panelOffset
            snapGridSwitch.layoutParams = params
        }
    }

    private fun updatePlacementActionsPosition() {
        if (!::placementActions.isInitialized) return
        sketchView.setMagnifierTopOffset(systemTopInset + dp(110).toFloat())
        (placementActions.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.topMargin = systemTopInset + dp(116)
            placementActions.layoutParams = params
        }
    }

    private fun saveProject(): Boolean {
        val id = projectId ?: return false
        return runCatching {
            val preview = sketchView.exportPreviewBitmap()
            try {
                SketchProjectStore.save(this, id, sketchView.snapshotState(), preview)
            } finally {
                preview.recycle()
            }
        }.getOrDefault(false)
    }

    private fun modeIconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        setColorFilter(Color.rgb(0, 102, 76))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = roundedBackground(Color.TRANSPARENT, 10)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(48)).apply { marginEnd = dp(2) }
    }

    private fun floatingIconButton(icon: Int, description: String, color: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        setColorFilter(Color.WHITE)
        setPadding(dp(15), dp(15), dp(15), dp(15))
        elevation = dp(10).toFloat()
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        setOnClickListener { action() }
    }

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun numberInput(value: String) = EditText(this).apply {
        setText(value); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSelectAllOnFocus(true); setSingleLine(true)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
