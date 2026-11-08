@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.editor.SketchEditor
import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.shared.tool.PointerContext
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import platform.Foundation.*
import kotlin.test.*

class IosProjectStoreTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString
    private val store = IosProjectStore(directory)

    @AfterTest
    fun clean() { NSFileManager.defaultManager.removeItemAtPath(directory, null) }

    @Test
    fun freshStoreRestoresSharedDocumentAndOrdersProjects() {
        assertTrue(store.list().isEmpty())
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(1500.5, 0.0), 240.0)
        val project = Project(ProjectManifest("first", "测试草图", 1), SketchDocument(walls = mapOf(wall.id to wall)))
        store.save(project)
        store.save(Project(ProjectManifest("second", "Other", 2)))
        val reopened = IosProjectStore(directory)
        assertEquals(project, reopened.load("first"))
        assertEquals(listOf("second", "first"), reopened.list().map { it.manifest.projectId })
    }

    @Test
    fun failedEncodePreservesPreviouslySavedProject() {
        val project = Project(ProjectManifest("first", "Original", 1))
        store.save(project)
        val invalid = project.copy(sketch = SketchDocument(walls = mapOf(
            "short" to Wall("short", Vec2(0.0, 0.0), Vec2(0.0, 0.0), 240.0))))
        assertFails { store.save(invalid) }
        assertEquals(project, store.load("first"))
    }

    @Test
    fun editorCommitsOnlyReleasedWallAndRestoresFromDisk() {
        val editor = SketchEditor()
        editor.setTool(ToolType.WALL)
        fun context(point: Vec2) = PointerContext(point, 24.0, 20.0, 10.0, 14.0, 20.0, 12.0)
        val start = Vec2(0.0, 0.0)
        val end = Vec2(2000.0, 0.0)
        editor.pointerDown(start, context(start))
        editor.pointerUp(start, context(start))
        editor.pointerDown(end, context(end))
        val project = Project(ProjectManifest("gesture", "Drawing", 1))
        store.save(project.copy(sketch = editor.document))
        assertTrue(store.load("gesture").sketch.walls.isEmpty())
        editor.pointerUp(end, context(end))
        store.save(project.copy(sketch = editor.document))
        val reopened = SketchEditor(IosProjectStore(directory).load("gesture").sketch)
        assertEquals(editor.document, reopened.document)
        assertEquals(end, reopened.document.walls.values.single().end)
        assertTrue(editor.undo())
        assertTrue(editor.document.walls.isEmpty())
        assertEquals(1, reopened.document.walls.size)
    }

    @Test
    fun rejectsPathsOutsideProjectDirectoryAndReportsCorruptFiles() {
        assertFailsWith<IllegalArgumentException> { store.load("../outside") }
        store.save(Project(ProjectManifest("first", "Original", 1)))
        ("invalid" as NSString).writeToFile("$directory/first.json", true, NSUTF8StringEncoding, null)
        assertFails { store.list() }
    }
}
