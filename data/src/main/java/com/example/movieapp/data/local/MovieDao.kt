package com.example.movieapp.data.local

import androidx.room.Dao
import androidx.paging.PagingSource
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY discoveryOrder, id")
    fun pagingSource(): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies ORDER BY discoveryOrder, id")
    fun observeMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id")
    fun observeMovie(id: Int): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun findMovie(id: Int): MovieEntity?

    @Query("SELECT COALESCE(MAX(discoveryOrder), -1) + 1 FROM movies")
    suspend fun nextDiscoveryOrder(): Long

    @Transaction
    suspend fun saveDiscoverPage(movies: List<MovieEntity>) {
        movies.distinctBy { it.id }.forEach { saveCachedMovie(it) }
    }

    @Transaction
    suspend fun saveCachedMovie(movie: MovieEntity) {
        val current = findMovie(movie.id)
        upsert(movie.mergeWith(current).copy(
            discoveryOrder = current?.discoveryOrder ?: nextDiscoveryOrder(),
            genres = current?.genres ?: movie.genres,
        ))
    }

    @Transaction
    suspend fun updateCachedDetails(movie: MovieEntity, genresProvided: Boolean) {
        val current = findMovie(movie.id) ?: return
        upsert(movie.mergeWith(current).copy(
            genres = if (genresProvided) movie.genres else current.genres,
        ))
    }

    @Upsert
    suspend fun upsert(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun delete(id: Int)
}

private fun MovieEntity.mergeWith(current: MovieEntity?) = copy(
    title = title.takeIf { it.isNotBlank() } ?: current?.title.orEmpty(),
    overview = overview?.takeIf { it.isNotBlank() } ?: current?.overview,
    posterPath = posterPath?.takeIf { it.isNotBlank() } ?: current?.posterPath,
    backdropPath = backdropPath?.takeIf { it.isNotBlank() } ?: current?.backdropPath,
    releaseDate = releaseDate?.takeIf { it.isNotBlank() } ?: current?.releaseDate,
    voteAverage = voteAverage?.takeIf { it.isFinite() && it in 0.0..10.0 } ?: current?.voteAverage,
    discoveryOrder = current?.discoveryOrder ?: discoveryOrder,
    usCertifications = current?.usCertifications ?: usCertifications,
)
