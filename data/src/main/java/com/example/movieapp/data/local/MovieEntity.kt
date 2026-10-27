package com.example.movieapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Displayable cache row. Production writes must go through MovieRepository verification. */
@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
) {
    init { require(id > 0) { "Movie ID must be positive." } }
}
