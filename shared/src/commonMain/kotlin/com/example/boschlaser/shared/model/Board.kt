package com.example.boschlaser.shared.model

import com.example.boschlaser.shared.geometry.*
import kotlin.math.*

enum class BoardFill { WHITE, GRID, WOOD }
enum class BoardShape { RECTANGLE, POLYGON, CIRCLE }

data class Board(val id: String, val vertices: List<Vec2>, val fill: BoardFill = BoardFill.WHITE,
    val shape: BoardShape = inferShape(vertices)) {
    init {
        require(id.isNotBlank())
        require(valid(vertices))
        when (shape) {
            BoardShape.RECTANGLE -> require(inferShape(vertices) == BoardShape.RECTANGLE)
            BoardShape.CIRCLE -> require(vertices.size == 64 && circle(center,radius).zip(vertices).all {
                (a,b) -> Geometry.distance(a,b) <= 1e-6 * maxOf(1.0,radius)
            })
            BoardShape.POLYGON -> Unit
        }
    }
    val center: Vec2 get() = Vec2((vertices.minOf { it.x } + vertices.maxOf { it.x }) / 2,
        (vertices.minOf { it.y } + vertices.maxOf { it.y }) / 2)
    val radius: Double get() = Geometry.distance(center, vertices.first())
    val controls: List<Vec2> get() = if (shape == BoardShape.CIRCLE) listOf(
        center + Vec2(radius, 0.0), center + Vec2(0.0, radius),
        center - Vec2(radius, 0.0), center - Vec2(0.0, radius)) else vertices

    fun withShape(value: BoardShape): Board {
        if (shape == value) return this
        val min = Vec2(vertices.minOf { it.x }, vertices.minOf { it.y })
        val max = Vec2(vertices.maxOf { it.x }, vertices.maxOf { it.y })
        val points = if (value == BoardShape.CIRCLE) circle(center, minOf(max.x-min.x,max.y-min.y)/2)
            else if (value == BoardShape.RECTANGLE) listOf(min, Vec2(max.x,min.y), max, Vec2(min.x,max.y)) else vertices
        return if (valid(points)) copy(vertices = points, shape = value) else this
    }
    val edgeMidpoints: List<Vec2> get() = if (shape == BoardShape.POLYGON)
        vertices.indices.map { (vertices[it] + vertices[(it+1)%vertices.size]) * .5 } else emptyList()

    fun insertedVertex(edge: Int): Board? {
        if (shape != BoardShape.POLYGON || edge !in vertices.indices || vertices.size >= 4096) return null
        val points = vertices.toMutableList().apply { add(edge+1,edgeMidpoints[edge]) }
        return if (valid(points)) copy(vertices = points) else null
    }
    fun removedVertex(index: Int): Board? {
        if (shape != BoardShape.POLYGON || vertices.size <= 3 || index !in vertices.indices) return null
        val points = vertices.filterIndexed { i, _ -> i != index }
        return if (valid(points)) copy(vertices = points) else null
    }
    fun editedControl(index: Int, point: Vec2): Board? {
        if (index !in controls.indices) return null
        val points = when (shape) {
            BoardShape.CIRCLE -> {
                val radius = Geometry.distance(center,point)
                if (radius < 1.0) return null
                circle(center,radius)
            }
            BoardShape.RECTANGLE -> {
                val opposite = vertices[(index+2)%4]
                listOf(Vec2(minOf(point.x,opposite.x),minOf(point.y,opposite.y)),
                    Vec2(maxOf(point.x,opposite.x),minOf(point.y,opposite.y)),
                    Vec2(maxOf(point.x,opposite.x),maxOf(point.y,opposite.y)),
                    Vec2(minOf(point.x,opposite.x),maxOf(point.y,opposite.y)))
            }
            BoardShape.POLYGON -> vertices.mapIndexed { i, old -> if (i == index) point else old }
        }
        return if (valid(points)) copy(vertices = points) else null
    }
    fun contains(point: Vec2, tolerance: Double = 0.0): Boolean {
        if (shape == BoardShape.CIRCLE) return Geometry.distance(center,point) <= radius + tolerance
        if (vertices.indices.any { Geometry.distanceToSegment(point, Segment2(vertices[it], vertices[(it + 1) % vertices.size])) <= tolerance }) return true
        var inside = false
        for (i in vertices.indices) {
            val a = vertices[i]; val b = vertices[(i + 1) % vertices.size]
            if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }
    companion object {
        fun inferShape(points: List<Vec2>): BoardShape = if (points.size == 4 &&
            points.indices.all { val a = points[it]; val b = points[(it+1)%4]; a.x == b.x || a.y == b.y }) BoardShape.RECTANGLE else BoardShape.POLYGON
        fun circle(center: Vec2, radius: Double): List<Vec2> = (0 until 64).map {
            val angle = it * 2 * PI / 64
            center + Vec2(cos(angle)*radius,sin(angle)*radius)
        }

        fun valid(points: List<Vec2>): Boolean {
            if (points.size !in 3..4096 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
            fun cross(a: Vec2, b: Vec2, c: Vec2) = (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
            var area = 0.0
            for (i in points.indices) {
                val a = points[i]; val b = points[(i+1)%points.size]
                if (Geometry.distance(a,b) < 1.0) return false
                area += a.x*b.y-b.x*a.y
                for (j in i+1 until points.size) {
                    if (j == i+1 || (i == 0 && j == points.lastIndex)) continue
                    val c = points[j]; val d = points[(j+1)%points.size]
                    if (maxOf(minOf(a.x,b.x),minOf(c.x,d.x)) <= minOf(maxOf(a.x,b.x),maxOf(c.x,d.x)) &&
                        maxOf(minOf(a.y,b.y),minOf(c.y,d.y)) <= minOf(maxOf(a.y,b.y),maxOf(c.y,d.y)) &&
                        cross(a,b,c)*cross(a,b,d) <= 0 && cross(c,d,a)*cross(c,d,b) <= 0) return false
                }
            }
            return area.isFinite() && abs(area) >= 2.0
        }
    }
}
