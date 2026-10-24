package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectInfo

/** Assets are addressed by logical IDs; the platform owns byte streams and filesystem locations. */
interface ProjectRepository {
    suspend fun save(project: Project)
    suspend fun load(id: String): Project?
    suspend fun list(): List<ProjectInfo>
    suspend fun delete(id: String)
}
