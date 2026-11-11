package com.example.boschlaser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SketchPoint(val x: Float, val y: Float)

enum class SketchWallControlLine { CENTER, INNER, OUTER }

data class SketchWall(
    val id: String = UUID.randomUUID().toString(),
    val start: SketchPoint,
    val end: SketchPoint,
    val thickness: Float = 240f,
    val measuredLength: Float? = null,
    val controlLine: SketchWallControlLine = SketchWallControlLine.CENTER,
    val fill: com.example.boschlaser.shared.model.WallFill = com.example.boschlaser.shared.model.WallFill.BLANK,
)

enum class SketchColumnType { RECTANGLE, CIRCLE }

data class SketchColumn(
    val id: String = UUID.randomUUID().toString(),
    val type: SketchColumnType,
    val center: SketchPoint,
    val width: Float = 400f,
    val depth: Float = 400f,
    val rotationDegrees: Float = 0f,
    val fill: com.example.boschlaser.shared.model.ColumnFill = com.example.boschlaser.shared.model.ColumnFill.BLACK,
)

enum class SketchOpeningType { DOOR, WINDOW }

data class SketchOpening(
    val id: String = UUID.randomUUID().toString(),
    val type: SketchOpeningType,
    val wallId: String,
    val position: Float,
    val width: Float = if (type == SketchOpeningType.DOOR) 900f else 1200f,
    val flipped: Boolean = false,
    val hingeFlipped: Boolean = false,
    val doorStyle: com.example.boschlaser.shared.model.DoorStyle = com.example.boschlaser.shared.model.DoorStyle.SINGLE,
)

data class SketchState(
    val boards: List<com.example.boschlaser.shared.model.Board> = emptyList(),
    val walls: List<SketchWall> = emptyList(),
    val columns: List<SketchColumn> = emptyList(),
    val openings: List<SketchOpening> = emptyList(),
)

data class SketchProjectSummary(val id: String, val name: String, val updatedAt: Long)
data class SketchProject(val summary: SketchProjectSummary, val state: SketchState)

object SketchProjectStore {
    private const val ROOT = "sketch-projects"
    private const val DATA_FILE = "project.json"
    private const val PREVIEW_FILE = "preview.png"

    fun create(context: Context): String {
        val id = UUID.randomUUID().toString()
        val directory = projectDirectory(context, id).apply { check(mkdirs()) }
        val now = System.currentTimeMillis()
        val name = context.getString(R.string.project_sketch_default_name, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now)))
        write(directory, id, name, now, SketchState())
        return id
    }

    fun save(context: Context, id: String, state: SketchState, preview: Bitmap? = null): Boolean = runCatching {
        require(isValidId(id))
        val directory = projectDirectory(context, id)
        require(directory.isDirectory)
        val old = readJson(File(directory, DATA_FILE)) ?: error("草稿不存在")
        val name = old.optString("name").takeIf(String::isNotBlank) ?: context.getString(R.string.home_sketches)
        write(directory, id, name, System.currentTimeMillis(), state)
        preview?.let { bitmap ->
            FileOutputStream(File(directory, PREVIEW_FILE)).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }.isSuccess

    fun load(context: Context, id: String): SketchProject? {
        if (!isValidId(id)) return null
        val data = readJson(File(projectDirectory(context, id), DATA_FILE)) ?: return null
        return SketchProject(
            SketchProjectSummary(id, data.optString("name", context.getString(R.string.home_sketches)), data.optLong("updatedAt")),
            decode(data),
        )
    }

    fun list(context: Context): List<SketchProjectSummary> =
        rootDirectory(context).listFiles().orEmpty().mapNotNull { directory ->
            if (!directory.isDirectory || !isValidId(directory.name)) return@mapNotNull null
            val data = readJson(File(directory, DATA_FILE)) ?: return@mapNotNull null
            SketchProjectSummary(
                directory.name,
                data.optString("name", context.getString(R.string.home_sketches)),
                data.optLong("updatedAt"),
            )
        }.sortedByDescending { it.updatedAt }

    fun rename(context: Context, id: String, newName: String): Boolean = runCatching {
        val name = newName.trim()
        require(name.isNotEmpty())
        val directory = projectDirectory(context, id)
        val data = readJson(File(directory, DATA_FILE)) ?: error("草稿不存在")
        write(directory, id, name, System.currentTimeMillis(), decode(data))
    }.isSuccess

    fun delete(context: Context, id: String): Boolean {
        if (!isValidId(id)) return false
        val root = rootDirectory(context).canonicalFile
        val target = projectDirectory(context, id).canonicalFile
        if (target.parentFile != root) return false
        return !target.exists() || target.deleteRecursively()
    }

    fun loadThumbnail(context: Context, id: String, targetSize: Int): Bitmap? {
        if (!isValidId(id)) return null
        val file = File(projectDirectory(context, id), PREVIEW_FILE)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetSize || bounds.outHeight / sample > targetSize) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun write(directory: File, id: String, name: String, updatedAt: Long, state: SketchState) {
        val json = encode(state).apply {
            put("version", 1); put("id", id); put("name", name); put("updatedAt", updatedAt)
        }
        val target = File(directory, DATA_FILE)
        val temporary = File(directory, "$DATA_FILE.tmp")
        FileOutputStream(temporary).bufferedWriter(Charsets.UTF_8).use { it.write(json.toString()) }
        if (target.exists()) check(target.delete())
        check(temporary.renameTo(target))
    }

    private fun encode(state: SketchState) = JSONObject().apply {
        put("boards", JSONArray().apply { state.boards.forEach { board -> put(JSONObject().apply {
            put("id", board.id); put("fill", board.fill.name); put("shape", board.shape.name)
            put("vertices", JSONArray().apply { board.vertices.forEach { point -> put(JSONArray().put(point.x).put(point.y)) } })
        }) } })
        put("walls", JSONArray().apply { state.walls.forEach { wall -> put(JSONObject().apply {
            put("id", wall.id); point("start", wall.start); point("end", wall.end)
            put("thickness", wall.thickness); wall.measuredLength?.let { put("measuredLength", it) }
            put("controlLine", wall.controlLine.name); put("fill", wall.fill.name)
        }) } })
        put("columns", JSONArray().apply { state.columns.forEach { column -> put(JSONObject().apply {
            put("id", column.id); put("type", column.type.name); point("center", column.center)
            put("width", column.width); put("depth", column.depth); put("rotationDegrees", column.rotationDegrees); put("fill", column.fill.name)
        }) } })
        put("openings", JSONArray().apply { state.openings.forEach { opening -> put(JSONObject().apply {
            put("id", opening.id); put("type", opening.type.name); put("wallId", opening.wallId)
            put("position", opening.position); put("width", opening.width); put("flipped", opening.flipped)
            put("hingeFlipped", opening.hingeFlipped)
            put("doorStyle", opening.doorStyle.name)
        }) } })
    }

    private fun decode(data: JSONObject) = SketchState(
        boards = data.optJSONArray("boards").objects().map { item ->
            val points = item.getJSONArray("vertices")
            com.example.boschlaser.shared.model.Board(item.getString("id"), (0 until points.length()).map { i ->
                val p = points.getJSONArray(i); com.example.boschlaser.shared.geometry.Vec2(p.getDouble(0), p.getDouble(1))
            }, com.example.boschlaser.shared.model.BoardFill.valueOf(item.getString("fill"))).let { board ->
                if (item.has("shape")) board.copy(shape = com.example.boschlaser.shared.model.BoardShape.valueOf(item.getString("shape"))) else board
            }
        },
        walls = data.optJSONArray("walls").objects().map { item -> SketchWall(
            item.optString("id", UUID.randomUUID().toString()), item.point("start"), item.point("end"),
            item.optDouble("thickness", 240.0).toFloat(),
            if (item.has("measuredLength")) item.optDouble("measuredLength").toFloat() else null,
            runCatching { SketchWallControlLine.valueOf(item.optString("controlLine")) }
                .getOrDefault(SketchWallControlLine.CENTER),
            runCatching { com.example.boschlaser.shared.model.WallFill.valueOf(item.optString("fill", "BLANK").let { if (it == "GRAY") "BLANK" else it }) }.getOrDefault(com.example.boschlaser.shared.model.WallFill.BLANK),
        ) },
        columns = data.optJSONArray("columns").objects().map { item -> SketchColumn(
            item.optString("id", UUID.randomUUID().toString()),
            runCatching { SketchColumnType.valueOf(item.optString("type")) }.getOrDefault(SketchColumnType.RECTANGLE),
            item.point("center"), item.optDouble("width", 400.0).toFloat(), item.optDouble("depth", 400.0).toFloat(),
            item.optDouble("rotationDegrees", 0.0).toFloat(),
            runCatching { com.example.boschlaser.shared.model.ColumnFill.valueOf(item.optString("fill", "BLACK")) }.getOrDefault(com.example.boschlaser.shared.model.ColumnFill.BLACK),
        ) },
        openings = data.optJSONArray("openings").objects().map { item -> SketchOpening(
            item.optString("id", UUID.randomUUID().toString()),
            runCatching { SketchOpeningType.valueOf(item.optString("type")) }.getOrDefault(SketchOpeningType.DOOR),
            item.optString("wallId"), item.optDouble("position", .5).toFloat().coerceIn(0f, 1f),
            item.optDouble("width", 900.0).toFloat(), item.optBoolean("flipped"), item.optBoolean("hingeFlipped"),
            com.example.boschlaser.shared.model.DoorStyle.valueOf(item.optString("doorStyle", "SINGLE")),
        ) },
    )

    private fun JSONObject.point(name: String, point: SketchPoint) = put(name, JSONObject().apply {
        put("x", point.x); put("y", point.y)
    })
    private fun JSONObject.point(name: String): SketchPoint = optJSONObject(name)?.let {
        SketchPoint(it.optDouble("x").toFloat(), it.optDouble("y").toFloat())
    } ?: SketchPoint(0f, 0f)
    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
    private fun readJson(file: File): JSONObject? = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    private fun rootDirectory(context: Context) = File(context.filesDir, ROOT).apply { mkdirs() }
    private fun projectDirectory(context: Context, id: String) = File(rootDirectory(context), id)
    private fun isValidId(id: String) = runCatching { UUID.fromString(id) }.isSuccess
}
