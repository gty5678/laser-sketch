package com.example.boschlaser.ui

import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectInfo
import com.example.boschlaser.shared.storage.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The shared ProjectService owns partition merging and binding cleanup; disk IO stays off main. */
internal class IosProjectRepository(private val store: IosProjectStore) : ProjectRepository {
    override suspend fun save(project: Project) = withContext(Dispatchers.Default) { store.save(project) }
    override suspend fun load(id: String): Project? = withContext(Dispatchers.Default) { if (store.exists(id)) store.load(id) else null }
    override suspend fun list(): List<ProjectInfo> = withContext(Dispatchers.Default) {
        store.list().map { ProjectInfo(it.manifest.projectId, it.manifest.name, it.manifest.updatedAt) }
    }
    override suspend fun delete(id: String) = withContext(Dispatchers.Default) { store.delete(id) }
}
