package com.example.movieapp.data.backend

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Entity(tableName = "demo_movies")
data class DemoMovie(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val genres: String? = null,
    val lastModified: String = "1970-01-01T00:00:00Z",
)

@Dao
abstract class DemoMovieDao {
    @Query("SELECT * FROM demo_movies ORDER BY id")
    abstract fun pagingSource(): PagingSource<Int, DemoMovie>
    @Query("SELECT * FROM demo_movies WHERE id = :id")
    abstract fun observeMovie(id: Int): Flow<DemoMovie?>
    @Query("SELECT * FROM demo_movies WHERE id = :id")
    abstract suspend fun movie(id: Int): DemoMovie?
    @Query("SELECT id FROM demo_movies ORDER BY id")
    abstract suspend fun ids(): List<Int>
    @Query("DELETE FROM demo_movies WHERE id = :id")
    abstract suspend fun delete(id: Int)
    @Upsert protected abstract suspend fun upsert(movie: DemoMovie)

    /** Atomic Home page merge. Server ordering is stable ID order; no schema/order column is needed. */
    @Transaction
    open suspend fun save(movies: List<DemoMovie>): Boolean {
        var changed = false
        for (incoming in movies) {
            val old = movie(incoming.id)
            val merged = incoming.copy(posterPath = incoming.posterPath ?: old?.posterPath)
            if (old != merged && (old == null || !Instant.parse(incoming.lastModified).isBefore(Instant.parse(old.lastModified)))) {
                upsert(merged)
                changed = true
            }
        }
        return changed
    }

    /** Recheck existence inside the transaction, including when network work was delayed. */
    @Transaction
    open suspend fun updateExisting(movie: DemoMovie) {
        if (movie(movie.id) != null) save(listOf(movie))
    }
}

@Database(entities = [DemoMovie::class], version = 1, exportSchema = true)
abstract class DemoDatabase : RoomDatabase() {
    abstract fun movies(): DemoMovieDao
    companion object {
        fun create(context: Context): DemoDatabase = Room.databaseBuilder(
            context, DemoDatabase::class.java, "backend-demo.db").build()
    }
}
