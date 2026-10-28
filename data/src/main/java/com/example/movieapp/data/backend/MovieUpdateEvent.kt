package com.example.movieapp.data.backend

import java.time.Instant

object MovieUpdateEvent {
    fun movieId(data: Map<String, String>): Int? {
        if (data["eventType"] != "movie_updated") return null
        val raw = data["movieId"] ?: return null
        if (!raw.matches(Regex("[0-9]+"))) return null
        val id = raw.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val updatedAt = data["updatedAt"] ?: return null
        if (runCatching { Instant.parse(updatedAt) }.isFailure) return null
        return id
    }
}
