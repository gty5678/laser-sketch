@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.example.boschlaser.shared.annotation.AnnotationDocument
import com.example.boschlaser.shared.model.*
import platform.UIKit.*
import platform.Foundation.*
import kotlin.native.Platform
import kotlinx.coroutines.*

fun MainViewController(): UIViewController {
    val platform = IosPlatform()
    return ComposeUIViewController {
        val services = remember {
            val testId = if (Platform.isDebugBinary) NSProcessInfo.processInfo.environment["LASERSKETCH_TEST_ID"] as? String else null
            val testRoot = testId?.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }?.let { NSTemporaryDirectory() + "LaserSketchUITest-$it" }
            IosAppServices(testRoot).also { selectAppLanguage(it.preferences.stringForKey("appLanguage").orEmpty(), it.preferences) }
        }
        DisposableEffect(services) { onDispose { services.close(); platform.close() } }
        MaterialTheme { IosApplication(platform, services) }
    }.also { platform.controller = it }
}

@Composable
private fun IosApplication(platform: IosPlatform, services: IosAppServices) {
    val store = services.store
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var current by remember { mutableStateOf<Project?>(null) }
    var route by remember { mutableStateOf("home") }
    var photo by remember { mutableStateOf<String?>(null) }
    var isNewPhoto by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val serviceError by services.failure.collectAsState()
    var naming by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var appSettings by remember { mutableStateOf(false) }
    var languageChooser by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    suspend fun reload() { projects = withContext(Dispatchers.Default) { store.list() } }
    fun work(action: suspend () -> Unit) { scope.launch {
        working = true
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = label("operation_failed") }
        finally { working = false }
    } }
    LaunchedEffect(Unit) { work { reload() } }
    fun save(close: Boolean, action: suspend () -> Project) {
        services.persist(action) { updated ->
            current = updated
            if (close) { route = "detail"; work { reload() } }
        }
    }
    var pendingPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var pendingProject by remember { mutableStateOf<Project?>(null) }
    suspend fun importPhoto(bytes: ByteArray, project: Project?) {
            val base = project?.let { store.load(it.manifest.projectId) }
                ?: Project(ProjectManifest(uuid(), label("photo_project"), now()))
            val id = uuid()
            val asset = ProjectAsset(id, "assets/$id.png", "image/png")
            store.putAsset(base.manifest.projectId, asset.path, bytes)
            val updated = base.copy(assets = base.assets + (id to asset),
                annotations = base.annotations + (id to AnnotationDocument(photoAssetId = id)))
            store.save(updated); current = updated; photo = id; isNewPhoto = true; route = "photo"; reload()
        }
    fun addPhoto(project: Project?) {
        platform.pickPhoto(onError = { error = label("photo_error") }, onResult = { bytes ->
            pendingProject = project; pendingPhoto = bytes
        })
    }
    Surface(Modifier.fillMaxSize()) {
        val project = current
        val pending = pendingPhoto
        when {
            pending != null -> key(pending) {
                val source = remember { decodePhoto(pending, 3000) }
                val previewSource = remember { decodePhoto(pending, 700) }
                PhotoCorrectionScreen(source, PhotoCorrectionLabels(label("photo_correction_title"), label("photo_correction_hint"),
                    label("photo_correction_lens"), label("photo_correction_reset"), label("photo_correction_original"),
                    label("photo_correction_apply"), label("photo_correction_error"),
                    label("photo_correction_horizontal"), label("photo_correction_vertical"), label("photo_correction_rotation")),
                    render = { settings, maximum -> withContext(Dispatchers.Default) { correctedIosPhoto(previewSource, settings, maximum) } },
                    onDone = { settings ->
                        val bytes = if (settings == null) png(source) else withContext(Dispatchers.Default) { png(correctedIosPhoto(source, settings, 3000)) }
                        importPhoto(bytes, pendingProject)
                        pendingPhoto = null
                    })
            }
            project != null && route == "sketch" -> key(project.manifest.projectId) {
                IosSketch(project, platform, services) { document, close ->
                    save(close) { services.projects.saveSketch(project.manifest.projectId, document) }
                }
            }
            project != null && route == "photo" && photo != null -> key(project.manifest.projectId, photo) {
                val id = checkNotNull(photo)
                IosAnnotation(project, id, platform, services, isNewPhoto) { document, close ->
                    save(close) { services.projects.saveAnnotation(project.manifest.projectId, id, document) }
                }
            }
            else -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(project?.manifest?.name?.takeIf { route == "detail" } ?: label("app_name"), style = MaterialTheme.typography.headlineMedium)
                if (project != null && route == "detail") {
                    TextButton(onClick = { current = null; route = "home"; work { reload() } }) { Text(label("back")) }
                    Button(onClick = { route = "sketch" }) { Text(label("sketch")) }
                    Button(onClick = { addPhoto(project) }) { Text(label("add_photo")) }
                    project.annotations.keys.forEachIndexed { index, id ->
                        OutlinedButton(onClick = { photo = id; isNewPhoto = false; route = "photo" }) { Text(label("photo_number").replace("%s", (index + 1).toString())) }
                    }
                    TextButton(onClick = { work { platform.share(withContext(Dispatchers.Default) { store.export(project) }, "lsk") } }) { Text(label("export_project")) }
                } else {
                    TextButton(onClick = { appSettings = true }) { Text(label("app_settings")) }
                    Row {
                        listOf("projects", "measurements", "devices").forEachIndexed { index, key ->
                            FilterChip(tab == index, { tab = index }, label = { Text(label(key)) })
                        }
                    }
                    when (tab) {
                        0 -> {
                            Row {
                                Button(enabled = !working, onClick = { name = ""; naming = "" }) { Text(label("new_sketch")) }
                                TextButton(enabled = !working, onClick = { addPhoto(null) }) { Text(label("add_photo")) }
                            }
                            TextButton(enabled = !working, onClick = {
                                platform.importProject(onError = { error = label("import_error") }, onResult = { bytes -> work {
                                    current = withContext(Dispatchers.Default) { store.import(bytes) }; route = "detail"; reload()
                                } })
                            }) { Text(label("import_project")) }
                            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                            ProjectScreen(projects.map { ProjectCardState(it.manifest.projectId, it.manifest.name, "") },
                                ProjectLabels(label("empty_projects"), label("actions"), label("rename"), label("delete"), label("export_project"), label("more")),
                                onOpen = { id -> current = projects.first { it.manifest.projectId == id }; route = "detail" },
                                onRename = { id -> name = projects.first { it.manifest.projectId == id }.manifest.name; naming = id },
                                onDelete = { deleting = it }, onExport = { id -> work { platform.share(withContext(Dispatchers.Default) { store.export(store.load(id)) }, "lsk") } },
                                thumbnail = { id -> IosProjectThumbnail(projects.first { it.manifest.projectId == id }, store) })
                        }
                        1 -> IosMeasurementPanel(services, platform)
                        2 -> IosDevicePanel(services)
                    }
                }
            }
        }
        if (appSettings) AlertDialog(onDismissRequest = { appSettings = false },
            title = { Text(label("app_settings")) },
            text = { TextButton(onClick = { appSettings = false; languageChooser = true }) { Text(label("app_language")) } },
            confirmButton = { TextButton(onClick = { appSettings = false }) { Text(label("ok")) } })
        if (languageChooser) AlertDialog(onDismissRequest = { languageChooser = false },
            title = { Text(label("app_language")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf("" to "language_system", "zh-Hans" to "language_zh_hans", "zh-Hant" to "language_zh_hant",
                    "ja" to "language_ja", "ko" to "language_ko", "en" to "language_en").forEach { (tag, key) ->
                    TextButton(onClick = { selectAppLanguage(tag, services.preferences); languageChooser = false }) {
                        RadioButton(selected = appLanguage == tag, onClick = null)
                        Text(label(key))
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { languageChooser = false }) { Text(label("cancel")) } })
        if (naming != null) AlertDialog(onDismissRequest = { naming = null }, title = { Text(label("project_name")) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(label("project_name")) }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { work {
                val id = checkNotNull(naming)
                val item = if (id.isEmpty()) services.projects.createSketch(name.trim())
                    else services.projects.rename(id, name.trim())
                naming = null; reload()
                if (id.isEmpty()) { current = item; route = "sketch" }
            } }) { Text(label("apply")) } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text(label("cancel")) } })
        if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, text = { Text(label("delete_project_confirm")) },
            confirmButton = { TextButton(onClick = { work { services.projects.delete(checkNotNull(deleting)); deleting = null; reload() } }) { Text(label("delete")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(label("cancel")) } })
        (error ?: serviceError)?.let { message -> AlertDialog(onDismissRequest = { error = null; services.failure.value = null },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null; services.failure.value = null }) { Text(label("ok")) } }) }
    }
}
