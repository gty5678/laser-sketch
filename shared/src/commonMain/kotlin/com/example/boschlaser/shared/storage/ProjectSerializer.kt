package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.annotation.Annotation as PhotoAnnotation
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.model.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

enum class ProjectFormatFailure { INVALID_JSON, INVALID_DATA, MISSING_ENTRY, UNKNOWN_ENTRY, UNSUPPORTED_FORMAT, UNSUPPORTED_VERSION, UNSUPPORTED_UNIT, SIZE_LIMIT }
class ProjectFormatException(val reason: ProjectFormatFailure) : Exception(reason.name)

/** Portable metadata codec; ZIP streams and photo bytes belong to the platform adapter. */
object ProjectSerializer {
    val entries = setOf("manifest.json", "sketch.json", "measurements.json", "annotations.json")
    private const val MAX_TEXT_LENGTH = 8 * 1024 * 1024
    private const val MAX_DEPTH = 64
    fun encode(project: Project): Map<String, String> {
        validateGeometry(project.sketch)
        val result = linkedMapOf(
            "manifest.json" to manifest(project).toString(),
            "sketch.json" to sketch(project).toString(),
            "measurements.json" to JsonArray(project.measurements.map(::measurement)).toString(),
            "annotations.json" to JsonArray(project.annotations.values.map(::annotationDocument)).toString(),
        )
        if (result.values.any { it.length > MAX_TEXT_LENGTH }) fail(ProjectFormatFailure.SIZE_LIMIT)
        return result
    }
    fun decode(files: Map<String, String>): Project {
        if (!files.keys.containsAll(entries)) fail(ProjectFormatFailure.MISSING_ENTRY)
        if (files.keys != entries) fail(ProjectFormatFailure.UNKNOWN_ENTRY)
        try {
            val manifest = parseMetadata(files.getValue("manifest.json")).obj()
            manifest.keys("format", "formatVersion", "projectId", "name", "updatedAt", "unit", "assets")
            if (manifest.string("format") != "LaserSketch") fail(ProjectFormatFailure.UNSUPPORTED_FORMAT)
            if (manifest.int("formatVersion") != 1) fail(ProjectFormatFailure.UNSUPPORTED_VERSION)
            if (manifest.string("unit") != "mm") fail(ProjectFormatFailure.UNSUPPORTED_UNIT)
            val metadata = ProjectManifest(manifest.string("projectId"), manifest.string("name"), manifest.long("updatedAt"))
            val assets = unique(manifest.array("assets").map { asset(it.obj()) }) { it.id }
            val sketch = parseMetadata(files.getValue("sketch.json")).obj()
            sketch.keys("formatVersion", "walls", "openings", "columns", "bindings", "dimensions")
            if (sketch.int("formatVersion") != 1) fail(ProjectFormatFailure.UNSUPPORTED_VERSION)
            val document = SketchDocument(walls = unique(sketch.array("walls").map { wall(it.obj()) }) { it.id },
                openings = unique(sketch.array("openings").map { opening(it.obj()) }) { it.id },
                columns = unique(sketch.array("columns").map { column(it.obj()) }) { it.id },
                dimensions = unique(sketch["dimensions"]?.array()?.map { dimension(it.obj()) }.orEmpty()) { it.id })
            validateGeometry(document)
            val measurements = parseMetadata(files.getValue("measurements.json")).array().map { measurement(it.obj()) }
            val annotations = unique(parseMetadata(files.getValue("annotations.json")).array().map { annotationDocument(it.obj()) }) {
                it.photoAssetId ?: fail(ProjectFormatFailure.INVALID_DATA)
            }
            val bindings = sketch.array("bindings").map { binding(it.obj()) }
            return Project(metadata, document, measurements, annotations, assets, bindings)
        } catch (failure: ProjectFormatException) { throw failure }
        catch (_: SerializationException) { fail(ProjectFormatFailure.INVALID_JSON) }
        catch (_: IllegalArgumentException) { fail(ProjectFormatFailure.INVALID_DATA) }
    }
    private fun manifest(project: Project) = buildJsonObject {
        val data = project.manifest
        put("format", data.format); put("formatVersion", data.formatVersion); put("projectId", data.projectId)
        put("name", data.name); put("updatedAt", data.updatedAt); put("unit", data.unit)
        put("assets", JsonArray(project.assets.values.map { asset -> buildJsonObject {
            put("id", asset.id); put("path", asset.path); put("mimeType", asset.mimeType)
            asset.pixelWidth?.let { put("pixelWidth", it) }; asset.pixelHeight?.let { put("pixelHeight", it) }
        } }))
    }
    private fun sketch(project: Project) = buildJsonObject {
        put("formatVersion", project.sketch.formatVersion)
        if (project.sketch.dimensions.isNotEmpty()) put("dimensions", JsonArray(project.sketch.dimensions.values.map { dimension -> buildJsonObject {
            put("id", dimension.id)
            fun reference(value: DimensionReference) = buildJsonObject {
                when (value) {
                    is WallFaceReference -> { put("wallId", value.wallId); put("side", value.side.name) }
                    is OpeningFrameReference -> { put("openingId", value.openingId); put("side", value.side.name) }
                    is ColumnLineReference -> { put("columnId", value.columnId); put("line", value.line.name) }
                }
            }
            put("first", reference(dimension.first)); put("second", reference(dimension.second))
            put("position", point(dimension.position.x, dimension.position.y))
        } }))
        put("walls", JsonArray(project.sketch.walls.values.map { wall -> buildJsonObject {
            put("id", wall.id); put("start", point(wall.start.x, wall.start.y)); put("end", point(wall.end.x, wall.end.y))
            put("thickness", wall.thickness); put("alignment", wall.alignment.name)
            wall.measuredLength?.let { put("measuredLength", it) }
        } }))
        put("openings", JsonArray(project.sketch.openings.values.map { opening -> buildJsonObject {
            put("id", opening.id); put("hostWallId", opening.hostWallId); put("offset", opening.offset)
            put("width", opening.width); put("type", opening.type.name)
            put("flipped", opening.flipped); put("hingeFlipped", opening.hingeFlipped)
        } }))
        put("columns", JsonArray(project.sketch.columns.values.map { column -> buildJsonObject {
            put("id", column.id); put("center", point(column.center.x, column.center.y))
            when (column) {
                is RectColumn -> { put("type", "RECTANGLE"); put("width", column.width); put("depth", column.depth); put("rotationDegrees", column.rotationDegrees) }
                is CircularColumn -> { put("type", "CIRCLE"); put("diameter", column.diameter) }
            }
        } }))
        put("bindings", JsonArray(project.bindings.map { binding -> buildJsonObject {
            put("measurementId", binding.measurementId); put("targetEntityId", binding.targetEntityId); put("property", binding.property.name)
        } }))
    }
    private fun dimension(data: JsonObject): WallDimension {
        data.keys("id", "first", "second", "position")
        fun reference(key: String): DimensionReference {
            val value = data.required(key).obj()
            return when {
                "wallId" in value -> {
                    value.keys("wallId", "side")
                    WallFaceReference(value.string("wallId"), WallFaceSide.valueOf(value.string("side")))
                }
                "openingId" in value -> {
                    value.keys("openingId", "side")
                    OpeningFrameReference(value.string("openingId"), OpeningFrameSide.valueOf(value.string("side")))
                }
                else -> {
                    value.keys("columnId", "line")
                    ColumnLineReference(value.string("columnId"), ColumnReferenceLine.valueOf(value.string("line")))
                }
            }
        }
        return WallDimension(data.string("id"), reference("first"), reference("second"), data.vector("position"))
    }
    private fun measurement(record: MeasurementRecord) = buildJsonObject {
        val item = record.measurement
        put("id", item.id); put("valueMm", item.valueMm); put("timestamp", item.timestamp)
        item.deviceId?.let { put("deviceId", it) }; item.operation?.let { put("operation", it.name) }
        item.accumulatedMm?.let { put("accumulatedMm", it) }
        put("rawHex", record.raw.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
        record.legacyOperation?.let { put("legacyOperation", it) }
    }
    private fun annotationDocument(document: AnnotationDocument) = buildJsonObject {
        put("photoAssetId", document.photoAssetId ?: fail(ProjectFormatFailure.INVALID_DATA))
        put("annotations", JsonArray(document.annotations.values.map { annotation -> buildJsonObject {
            put("id", annotation.id); put("type", annotation.kind.name); put("colorArgb", annotation.colorArgb)
            put("points", JsonArray(annotation.points.map { point(it.x, it.y) }))
            when (annotation) {
                is DimensionAnnotation -> { put("label", annotation.label); put("thicknessPreset", annotation.thicknessPreset) }
                is TextAnnotation -> { put("text", annotation.text); put("sizePreset", annotation.sizePreset); put("whiteBackground", annotation.whiteBackground) }
                is AngleAnnotation -> put("label", annotation.label)
                is AreaAnnotation -> put("label", annotation.label)
            }
        } }))
    }
    private fun asset(data: JsonObject): ProjectAsset {
        data.keys("id", "path", "mimeType", "pixelWidth", "pixelHeight")
        return ProjectAsset(data.string("id"), data.string("path"), data.string("mimeType"), data.optionalInt("pixelWidth"), data.optionalInt("pixelHeight"))
    }
    private fun wall(data: JsonObject): Wall {
        data.keys("id", "start", "end", "thickness", "alignment", "measuredLength")
        return Wall(data.string("id"), data.vector("start"), data.vector("end"), data.double("thickness"),
            WallAlignment.valueOf(data.string("alignment")), data.optionalDouble("measuredLength"))
    }
    private fun opening(data: JsonObject): Opening {
        data.keys("id", "hostWallId", "offset", "width", "type", "flipped", "hingeFlipped")
        return Opening(data.string("id"), data.string("hostWallId"), data.double("offset"), data.double("width"),
            OpeningType.valueOf(data.string("type")), data.boolean("flipped"), data.boolean("hingeFlipped"))
    }
    private fun column(data: JsonObject): Column = when (data.string("type")) {
        "RECTANGLE" -> {
            data.keys("id", "type", "center", "width", "depth", "rotationDegrees")
            RectColumn(data.string("id"), data.vector("center"), data.double("width"), data.double("depth"), data.double("rotationDegrees"))
        }
        "CIRCLE" -> {
            data.keys("id", "type", "center", "diameter")
            CircularColumn(data.string("id"), data.vector("center"), data.double("diameter"))
        }
        else -> fail(ProjectFormatFailure.INVALID_DATA)
    }
    private fun measurement(data: JsonObject): MeasurementRecord {
        data.keys("id", "valueMm", "timestamp", "deviceId", "operation", "accumulatedMm", "rawHex", "legacyOperation")
        val raw = data.string("rawHex")
        if (raw.length % 2 != 0 || raw.any { it.digitToIntOrNull(16) == null }) fail(ProjectFormatFailure.INVALID_DATA)
        return MeasurementRecord(Measurement(data.string("id"), data.double("valueMm"), data.long("timestamp"),
            data.optionalString("deviceId"), data.optionalString("operation")?.let(MeasurementOperation::valueOf),
            data.optionalDouble("accumulatedMm")), ByteArray(raw.length / 2) { raw.substring(it * 2, it * 2 + 2).toInt(16).toByte() },
            data.optionalString("legacyOperation"))
    }
    private fun annotationDocument(data: JsonObject): AnnotationDocument {
        data.keys("photoAssetId", "annotations")
        return AnnotationDocument(unique(data.array("annotations").map { annotation(it.obj()) }) { it.id }, data.string("photoAssetId"))
    }
    private fun annotation(data: JsonObject): PhotoAnnotation {
        val kind = AnnotationKind.valueOf(data.string("type"))
        val points = data.array("points").map { it.coordinates().let { pair -> PhotoPoint(pair.first, pair.second) } }
        val id = data.string("id"); val color = data.int("colorArgb")
        when (kind) {
            AnnotationKind.DIMENSION -> {
                data.keys("id", "type", "colorArgb", "points", "label", "thicknessPreset")
                if (points.size != 2) fail(ProjectFormatFailure.INVALID_DATA)
                return DimensionAnnotation(id, points[0], points[1], data.string("label"), color, data.int("thicknessPreset"))
            }
            AnnotationKind.TEXT -> {
                data.keys("id", "type", "colorArgb", "points", "text", "sizePreset", "whiteBackground")
                if (points.size != 1) fail(ProjectFormatFailure.INVALID_DATA)
                return TextAnnotation(id, points[0], data.string("text"), color, data.int("sizePreset"), data.boolean("whiteBackground"))
            }
            AnnotationKind.ANGLE -> {
                data.keys("id", "type", "colorArgb", "points", "label")
                if (points.size != 3) fail(ProjectFormatFailure.INVALID_DATA)
                return AngleAnnotation(id, points[0], points[1], points[2], data.string("label"), color)
            }
            AnnotationKind.AREA -> {
                data.keys("id", "type", "colorArgb", "points", "label")
                return AreaAnnotation(id, points, data.string("label"), color)
            }
        }
    }
    private fun binding(data: JsonObject): MeasurementBinding {
        data.keys("measurementId", "targetEntityId", "property")
        return MeasurementBinding(data.string("measurementId"), data.string("targetEntityId"), MeasurementProperty.valueOf(data.string("property")))
    }
    private fun validateGeometry(document: SketchDocument) {
        if (document.walls.values.any { Geometry.length(WallGeometry.referenceLine(it)) <= Geometry.EPSILON }) fail(ProjectFormatFailure.INVALID_DATA)
        if (document.openings.values.any { !OpeningGeometry.fits(it, document.walls.getValue(it.hostWallId)) ||
                it.width >= Geometry.length(WallGeometry.referenceLine(document.walls.getValue(it.hostWallId))) }) fail(ProjectFormatFailure.INVALID_DATA)
    }
    internal fun parseMetadata(text: String): JsonElement {
        if (text.length > MAX_TEXT_LENGTH) fail(ProjectFormatFailure.SIZE_LIMIT)
        val containers = mutableListOf<MutableSet<String>?>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{', '[' -> {
                    containers.add(if (text[index] == '{') mutableSetOf() else null)
                    if (containers.size > MAX_DEPTH) fail(ProjectFormatFailure.SIZE_LIMIT)
                }
                '}', ']' -> if (containers.isNotEmpty()) containers.removeAt(containers.lastIndex)
                '"' -> {
                    val start = index++
                    var escaped = false
                    while (index < text.length) {
                        val character = text[index]
                        if (!escaped && character == '"') break
                        escaped = !escaped && character == '\\'
                        index++
                    }
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':' && index < text.length) {
                        val key = Json.parseToJsonElement(text.substring(start, index + 1)).jsonPrimitive.content
                        val keys = containers.lastOrNull() ?: fail(ProjectFormatFailure.INVALID_JSON)
                        if (!keys.add(key)) fail(ProjectFormatFailure.INVALID_DATA)
                    }
                }
            }
            index++
        }
        return Json.parseToJsonElement(text)
    }
    private fun point(x: Double, y: Double) = JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))
    private fun JsonElement.obj() = this as? JsonObject ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonElement.array() = this as? JsonArray ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonObject.array(key: String) = required(key).array()
    private fun JsonObject.required(key: String) = get(key) ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonObject.primitive(key: String) = required(key) as? JsonPrimitive ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonObject.string(key: String): String = primitive(key).let { if (it.isString) it.content else fail(ProjectFormatFailure.INVALID_DATA) }
    private fun JsonObject.optionalString(key: String) = if (get(key) == null || get(key) == JsonNull) null else string(key)
    private fun JsonObject.int(key: String): Int = primitive(key).let { if (!it.isString) it.intOrNull else null } ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonObject.long(key: String): Long = primitive(key).let { if (!it.isString) it.longOrNull else null } ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonObject.double(key: String) = required(key).number()
    private fun JsonElement.number(): Double = (this as? JsonPrimitive)?.let { if (!it.isString) it.doubleOrNull else null }
        ?.takeIf { it.isFinite() } ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonObject.optionalDouble(key: String) = if (get(key) == null || get(key) == JsonNull) null else double(key)
    private fun JsonObject.optionalInt(key: String) = if (get(key) == null || get(key) == JsonNull) null else int(key)
    private fun JsonObject.boolean(key: String): Boolean = primitive(key).let { if (!it.isString) it.booleanOrNull else null } ?: fail(ProjectFormatFailure.INVALID_DATA)
    private fun JsonElement.coordinates(): Pair<Double, Double> {
        val values = array()
        if (values.size != 2) fail(ProjectFormatFailure.INVALID_DATA)
        return values[0].number() to values[1].number()
    }
    private fun JsonObject.vector(key: String) = required(key).coordinates().let { Vec2(it.first, it.second) }
    private fun JsonObject.keys(vararg allowed: String) { if (keys.any { it !in allowed }) fail(ProjectFormatFailure.INVALID_DATA) }
    private fun <T> unique(values: List<T>, id: (T) -> String): Map<String, T> {
        val result = linkedMapOf<String, T>()
        for (value in values) if (result.put(id(value), value) != null) fail(ProjectFormatFailure.INVALID_DATA)
        return result
    }
    private fun fail(reason: ProjectFormatFailure): Nothing = throw ProjectFormatException(reason)
}
