package com.example.boschlaser.shared.topology

import com.example.boschlaser.shared.geometry.Vec2

enum class WallEndpoint { START, END }
data class WallEndpointRef(val wallId: String, val endpoint: WallEndpoint)
data class WallJunction(val id: String, val position: Vec2, val endpoints: Set<WallEndpointRef>, val hostWallIds: Set<String> = emptySet())
