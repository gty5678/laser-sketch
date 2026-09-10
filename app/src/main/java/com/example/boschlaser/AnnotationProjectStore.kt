package com.example.boschlaser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AnnotationState(
    val dimensions: List<DimensionMark> = emptyList(),
    val texts: List<TextMark> = emptyList(),
    val angles: List<AngleMark> = emptyList(),
    val areas: List<AreaMark> = emptyList(),
)

data class AnnotationProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
)

data class AnnotationProject(
    val summary: AnnotationProjectSummary,
    val bitmap: Bitmap,
    val state: AnnotationState,
)

object AnnotationProjectStore {
    private const val ROOT = "annotation-projects"
    private const val IMAGE_FILE = "photo.jpg"
    private const val PREVIEW_FILE = "preview.png"
    private const val DATA_FILE = "project.json"

    fun create(context: Context, bitmap: Bitmap): String {
        val id = UUID.randomUUID().toString()
        val directory = projectDirectory(context, id).apply { check(mkdirs()) }
        FileOutputStream(File(directory, IMAGE_FILE)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream))
        }
        val now = System.currentTimeMillis()
        val name = "照片标注 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))}"
        writeProjectData(directory, id, name, now, AnnotationState())
        return id
    }

    fun save(context: Context, id: String, state: AnnotationState, preview: Bitmap? = null) {
        val directory = projectDirectory(context, id)
        if (!directory.isDirectory || !File(directory, IMAGE_FILE).isFile) return
        val existing = readData(File(directory, DATA_FILE))
        val name = existing?.optString("name")?.takeIf { it.isNotBlank() }
            ?: "照片标注 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        writeProjectData(directory, id, name, System.currentTimeMillis(), state)
        preview?.let { bitmap ->
            FileOutputStream(File(directory, PREVIEW_FILE)).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    fun load(context: Context, id: String): AnnotationProject? {
        val directory = projectDirectory(context, id)
        val data = readData(File(directory, DATA_FILE)) ?: return null
        val bitmap = BitmapFactory.decodeFile(File(directory, IMAGE_FILE).absolutePath) ?: return null
        return AnnotationProject(
            summary = AnnotationProjectSummary(
                id = id,
                name = data.optString("name", "照片标注"),
                updatedAt = data.optLong("updatedAt", 0L),
            ),
            bitmap = bitmap,
            state = decodeState(data),
        )
    }

    fun loadThumbnail(context: Context, id: String, targetSize: Int): Bitmap? {
        if (!isValidId(id)) return null
        val directory = projectDirectory(context, id)
        val preview = File(directory, PREVIEW_FILE)
        val image = if (preview.isFile) preview else File(directory, IMAGE_FILE)
        if (!image.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetSize || bounds.outHeight / sampleSize > targetSize) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            image.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) },
        )
    }

    fun rename(context: Context, id: String, newName: String): Boolean = runCatching {
        val name = newName.trim()
        require(name.isNotEmpty())
        val directory = projectDirectory(context, id)
        val data = readData(File(directory, DATA_FILE)) ?: error("标注文件不存在")
        writeProjectData(directory, id, name, System.currentTimeMillis(), decodeState(data))
    }.isSuccess

    fun list(context: Context): List<AnnotationProjectSummary> =
        rootDirectory(context).listFiles().orEmpty().mapNotNull { directory ->
            if (!directory.isDirectory || !isValidId(directory.name)) return@mapNotNull null
            val data = readData(File(directory, DATA_FILE)) ?: return@mapNotNull null
            if (!File(directory, IMAGE_FILE).isFile) return@mapNotNull null
            AnnotationProjectSummary(
                id = directory.name,
                name = data.optString("name", "照片标注"),
                updatedAt = data.optLong("updatedAt", 0L),
            )
        }.sortedByDescending { it.updatedAt }

    fun delete(context: Context, id: String): Boolean {
        if (!isValidId(id)) return false
        val root = rootDirectory(context).canonicalFile
        val target = projectDirectory(context, id).canonicalFile
        if (target.parentFile != root) return false
        return !target.exists() || target.deleteRecursively()
    }

    private fun writeProjectData(
        directory: File,
        id: String,
        name: String,
        updatedAt: Long,
        state: AnnotationState,
    ) {
        val data = encodeState(state).apply {
            put("version", 1)
            put("id", id)
            put("name", name)
            put("updatedAt", updatedAt)
        }
        val target = File(directory, DATA_FILE)
        val temporary = File(directory, "$DATA_FILE.tmp")
        FileOutputStream(temporary).bufferedWriter(Charsets.UTF_8).use { it.write(data.toString()) }
        if (target.exists()) target.delete()
        check(temporary.renameTo(target))
    }

    private fun encodeState(state: AnnotationState) = JSONObject().apply {
        put("dimensions", JSONArray().apply {
            state.dimensions.forEach { mark -> put(JSONObject().apply {
                put("startX", mark.startX); put("startY", mark.startY)
                put("endX", mark.endX); put("endY", mark.endY)
                put("label", mark.label); put("color", mark.color); put("thickness", mark.thickness)
            }) }
        })
        put("texts", JSONArray().apply {
            state.texts.forEach { mark -> put(JSONObject().apply {
                put("x", mark.x); put("y", mark.y); put("text", mark.text)
                put("color", mark.color); put("textSize", mark.textSize)
                put("whiteBackground", mark.whiteBackground)
            }) }
        })
        put("angles", JSONArray().apply {
            state.angles.forEach { mark -> put(JSONObject().apply {
                put("vertexX", mark.vertexX); put("vertexY", mark.vertexY)
                put("firstX", mark.firstX); put("firstY", mark.firstY)
                put("secondX", mark.secondX); put("secondY", mark.secondY)
                put("label", mark.label); put("color", mark.color)
            }) }
        })
        put("areas", JSONArray().apply {
            state.areas.forEach { mark -> put(JSONObject().apply {
                put("label", mark.label); put("color", mark.color)
                put("points", JSONArray().apply {
                    mark.points.forEach { point -> put(JSONObject().apply { put("x", point.x); put("y", point.y) }) }
                })
            }) }
        })
    }

    private fun decodeState(data: JSONObject): AnnotationState = AnnotationState(
        dimensions = data.optJSONArray("dimensions").objects().map { item -> DimensionMark(
            item.float("startX"), item.float("startY"), item.float("endX"), item.float("endY"),
            item.optString("label"), item.optInt("color", Color.rgb(214, 32, 42)), item.optInt("thickness", 1),
        ) },
        texts = data.optJSONArray("texts").objects().map { item -> TextMark(
            item.float("x"), item.float("y"), item.optString("text"), item.optInt("color", Color.rgb(214, 32, 42)),
            item.optInt("textSize", 1), item.optBoolean("whiteBackground", true),
        ) },
        angles = data.optJSONArray("angles").objects().map { item -> AngleMark(
            item.float("vertexX"), item.float("vertexY"), item.float("firstX"), item.float("firstY"),
            item.float("secondX"), item.float("secondY"), item.optString("label"), item.optInt("color", Color.rgb(214, 32, 42)),
        ) },
        areas = data.optJSONArray("areas").objects().map { item -> AreaMark(
            points = item.optJSONArray("points").objects().map { point -> AreaPoint(point.float("x"), point.float("y")) },
            label = item.optString("label"),
            color = item.optInt("color", Color.rgb(214, 32, 42)),
        ) }.filter { it.points.size >= 3 },
    )

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONObject.float(name: String): Float = optDouble(name, 0.0).toFloat()
    private fun readData(file: File): JSONObject? = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    private fun rootDirectory(context: Context) = File(context.filesDir, ROOT).apply { mkdirs() }
    private fun projectDirectory(context: Context, id: String) = File(rootDirectory(context), id)
    private fun isValidId(id: String): Boolean = runCatching { UUID.fromString(id) }.isSuccess
}
