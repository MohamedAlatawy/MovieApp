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
    suspend fun saveVerified(movie: MovieEntity) {
        val order = findMovie(movie.id)?.discoveryOrder ?: nextDiscoveryOrder()
        upsert(movie.copy(discoveryOrder = order))
    }

    /** Only the verifying repository should call this in production. */
    @Upsert
    suspend fun upsert(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun delete(id: Int)
}
