package com.example.boschlaser

import com.example.boschlaser.shared.annotation.*

internal fun DimensionMark.toCoreAnnotation(id: String) = DimensionAnnotation(id,
    PhotoPoint(startX.toDouble(), startY.toDouble()), PhotoPoint(endX.toDouble(), endY.toDouble()), label, color, thickness)
internal fun TextMark.toCoreAnnotation(id: String) = TextAnnotation(id, PhotoPoint(x.toDouble(), y.toDouble()),
    text, color, textSize, whiteBackground)
internal fun AngleMark.toCoreAnnotation(id: String) = AngleAnnotation(id, PhotoPoint(vertexX.toDouble(), vertexY.toDouble()),
    PhotoPoint(firstX.toDouble(), firstY.toDouble()), PhotoPoint(secondX.toDouble(), secondY.toDouble()), label, color)
internal fun AreaMark.toCoreAnnotation(id: String) = AreaAnnotation(id, points.map { PhotoPoint(it.x.toDouble(), it.y.toDouble()) }, label, color)

/** Old files have no annotation IDs. Deterministic IDs are local to each imported photo document. */
internal fun AnnotationState.toAnnotationDocument(photoAssetId: String? = null) = AnnotationDocument(buildMap {
    dimensions.forEachIndexed { index, mark -> runCatching { mark.toCoreAnnotation("dimension-$index") }.getOrNull()?.let { put(it.id, it) } }
    texts.forEachIndexed { index, mark -> runCatching { mark.toCoreAnnotation("text-$index") }.getOrNull()?.let { put(it.id, it) } }
    angles.forEachIndexed { index, mark -> runCatching { mark.toCoreAnnotation("angle-$index") }.getOrNull()?.let { put(it.id, it) } }
    areas.forEachIndexed { index, mark -> runCatching { mark.toCoreAnnotation("area-$index") }.getOrNull()?.let { put(it.id, it) } }
}, photoAssetId)

internal fun AnnotationDocument.toAnnotationState() = AnnotationState(
    annotations.values.filterIsInstance<DimensionAnnotation>().map { DimensionMark(it.start.x.toFloat(), it.start.y.toFloat(),
        it.end.x.toFloat(), it.end.y.toFloat(), it.label, it.colorArgb, it.thicknessPreset) },
    annotations.values.filterIsInstance<TextAnnotation>().map { TextMark(it.position.x.toFloat(), it.position.y.toFloat(),
        it.text, it.colorArgb, it.sizePreset, it.whiteBackground) },
    annotations.values.filterIsInstance<AngleAnnotation>().map { AngleMark(it.vertex.x.toFloat(), it.vertex.y.toFloat(),
        it.first.x.toFloat(), it.first.y.toFloat(), it.second.x.toFloat(), it.second.y.toFloat(), it.label, it.colorArgb) },
    annotations.values.filterIsInstance<AreaAnnotation>().map { AreaMark(it.points.map { point -> AreaPoint(point.x.toFloat(), point.y.toFloat()) }, it.label, it.colorArgb) },
)
