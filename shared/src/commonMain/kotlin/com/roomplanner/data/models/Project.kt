package com.roomplanner.data.models

import com.roomplanner.common.generateUUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a saved Room Planner project.
 * Stored as JSON on device filesystem.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
) {
    companion object {
        /**
         * Create new empty project
         */
        fun create(name: String): Project {
            val now = Clock.System.now()
            return Project(
                id = generateUUID(),
                name = name,
                createdAt = now,
                modifiedAt = now,
            )
        }
    }
}
