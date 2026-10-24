package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Segment2
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

enum class MigrationNotice { ORPHAN_OPENING, INCOMPLETE_AREA }
data class MigratedProject(val project: Project, val notices: List<MigrationNotice> = emptyList())

/** Pure, deterministic migration of Android version-1 metadata. Source bytes are never changed. */
object ProjectMigration {
    private const val LEGACY_COLOR = -2744278
    fun androidSketch(text: String, targetId: String? = null): MigratedProject = migrate(text) { data ->
        val notices = mutableListOf<MigrationNotice>()
        val walls = unique(data.items("walls").mapIndexed { index, wall ->
            val center = Segment2(wall.point("start"), wall.point("end"))
            val thickness = wall.number("thickness", 240.0)
            val alignment = when (wall.string("controlLine", "CENTER")) {
                "CENTER" -> WallAlignment.CENTER
                "INNER" -> WallAlignment.LEFT
                "OUTER" -> WallAlignment.RIGHT
                else -> invalid()
            }
            val offset = when (alignment) {
                WallAlignment.CENTER -> 0.0
                WallAlignment.LEFT -> -thickness / 2
                WallAlignment.RIGHT -> thickness / 2
            }
            val reference = Geometry.offset(center, offset)
            Wall(wall.string("id", "wall-$index"), reference.start, reference.end, thickness, alignment,
                wall.optionalNumber("measuredLength"))
        }) { it.id }
        val columns = unique(data.items("columns").mapIndexed { index, column ->
            val id = column.string("id", "column-$index")
            val center = column.point("center")
            val width = column.number("width", 400.0)
            when (column.string("type", "RECTANGLE")) {
                "RECTANGLE" -> RectColumn(id, center, width, column.number("depth", 400.0), column.number("rotationDegrees", 0.0))
                "CIRCLE" -> CircularColumn(id, center, width)
                else -> invalid()
            }
        }) { it.id }
        val openings = unique(data.items("openings").mapIndexedNotNull { index, opening ->
            val hostId = opening.string("wallId")
            val host = walls[hostId]
            if (host == null) { notices.add(MigrationNotice.ORPHAN_OPENING); return@mapIndexedNotNull null }
            Opening(opening.string("id", "opening-$index"), hostId,
                opening.number("position", 0.5).coerceIn(0.0, 1.0) * Geometry.length(Segment2(host.start, host.end)),
                opening.number("width", 900.0), OpeningType.valueOf(opening.string("type", "DOOR")),
                opening.boolean("flipped", false), opening.boolean("hingeFlipped", false))
        }) { it.id }
        val project = Project(data.manifest(targetId), SketchDocument(walls = walls, columns = columns, openings = openings))
        ProjectSerializer.encode(project) // Validate the full target before returning any migrated state.
        MigratedProject(project, notices)
    }

    fun androidAnnotation(text: String, photo: ProjectAsset, targetId: String? = null): MigratedProject = migrate(text) { data ->
        val notices = mutableListOf<MigrationNotice>()
        val annotations = buildMap<String, com.example.boschlaser.shared.annotation.Annotation> {
            data.items("dimensions").forEachIndexed { index, mark ->
                val id = "dimension-$index"
                put(id, DimensionAnnotation(id, mark.photoPoint("startX", "startY"), mark.photoPoint("endX", "endY"),
                    mark.string("label", ""), mark.integer("color", LEGACY_COLOR), mark.integer("thickness", 1)))
            }
            data.items("texts").forEachIndexed { index, mark ->
                val id = "text-$index"
                put(id, TextAnnotation(id, mark.photoPoint("x", "y"), mark.string("text", ""),
                    mark.integer("color", LEGACY_COLOR), mark.integer("textSize", 1), mark.boolean("whiteBackground", true)))
            }
            data.items("angles").forEachIndexed { index, mark ->
                val id = "angle-$index"
                put(id, AngleAnnotation(id, mark.photoPoint("vertexX", "vertexY"), mark.photoPoint("firstX", "firstY"),
                    mark.photoPoint("secondX", "secondY"), mark.string("label", ""), mark.integer("color", LEGACY_COLOR)))
            }
            data.items("areas").forEachIndexed { index, mark ->
                val points = mark.items("points").map { it.photoPoint("x", "y") }
                if (points.size < 3) notices.add(MigrationNotice.INCOMPLETE_AREA)
                else {
                    val id = "area-$index"
                    put(id, AreaAnnotation(id, points, mark.string("label", ""), mark.integer("color", LEGACY_COLOR)))
                }
            }
        }
        val project = Project(data.manifest(targetId), annotations = mapOf(photo.id to AnnotationDocument(annotations, photo.id)),
            assets = mapOf(photo.id to photo))
        ProjectSerializer.encode(project)
        MigratedProject(project, notices)
    }
    private fun migrate(text: String, action: (JsonObject) -> MigratedProject): MigratedProject = try {
        val data = ProjectSerializer.parseMetadata(text) as? JsonObject ?: invalid()
        if (data.integer("version", 1) != 1) throw ProjectFormatException(ProjectFormatFailure.UNSUPPORTED_VERSION)
        require(data.string("id").isNotBlank())
        action(data)
    } catch (failure: ProjectFormatException) { throw failure }
    catch (_: SerializationException) { throw ProjectFormatException(ProjectFormatFailure.INVALID_JSON) }
    catch (_: IllegalArgumentException) { invalid() }

    private fun JsonObject.manifest(targetId: String?) = ProjectManifest(targetId ?: string("id"), string("name"),
        get("updatedAt")?.let { (it as? JsonPrimitive)?.takeIf { value -> !value.isString }?.longOrNull ?: invalid() } ?: 0L)
    private fun JsonObject.string(key: String, fallback: String? = null): String = get(key)?.let {
        (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: invalid()
    } ?: fallback ?: invalid()
    private fun JsonObject.number(key: String, fallback: Double? = null): Double = get(key)?.let {
        (it as? JsonPrimitive)?.takeIf { value -> !value.isString }?.doubleOrNull?.takeIf(Double::isFinite) ?: invalid()
    } ?: fallback ?: invalid()
    private fun JsonObject.optionalNumber(key: String) = if (get(key) == null || get(key) == JsonNull) null else number(key)
    private fun JsonObject.integer(key: String, fallback: Int): Int = get(key)?.let {
        (it as? JsonPrimitive)?.takeIf { value -> !value.isString }?.intOrNull ?: invalid()
    } ?: fallback
    private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean = get(key)?.let {
        (it as? JsonPrimitive)?.takeIf { value -> !value.isString }?.booleanOrNull ?: invalid()
    } ?: fallback
    private fun JsonObject.items(key: String): List<JsonObject> = get(key)?.let {
        (it as? JsonArray)?.map { value -> value as? JsonObject ?: invalid() } ?: invalid()
    } ?: emptyList()
    private fun JsonObject.point(key: String): Vec2 {
        val point = get(key) as? JsonObject ?: invalid()
        return Vec2(point.number("x"), point.number("y"))
    }
    private fun JsonObject.photoPoint(x: String, y: String) = PhotoPoint(number(x, 0.0), number(y, 0.0))
    private fun <T> unique(items: List<T>, id: (T) -> String): Map<String, T> = linkedMapOf<String, T>().apply {
        items.forEach { if (put(id(it), it) != null) invalid() }
    }
    private fun invalid(): Nothing = throw ProjectFormatException(ProjectFormatFailure.INVALID_DATA)
}
