package com.example.movieapp.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/** Home cache row; the legacy certification column is retained for schema compatibility. */
@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
    @ColumnInfo(defaultValue = "0") val discoveryOrder: Long = 0,
    val genres: String? = null,
    val usCertifications: String? = null,
) {
    init { require(id > 0) { "Movie ID must be positive." } }
}
