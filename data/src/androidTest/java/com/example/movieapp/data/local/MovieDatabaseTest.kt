package com.example.movieapp.data.local

import androidx.room.Room
import androidx.paging.PagingSource
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieDatabaseTest {
    private lateinit var database: MovieDatabase
    private lateinit var dao: MovieDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MovieDatabase::class.java,
        ).build()
        dao = database.movieDao()
    }

    @After fun tearDown() { database.close() }

    @Test fun pagingSourceKeepsVerifiedInsertionOrderAcrossUpdates() = runBlocking {
        dao.saveVerified(movie().copy(id = 2, title = "Zulu"))
        dao.saveVerified(movie().copy(id = 1, title = "Alpha"))
        dao.saveVerified(movie().copy(id = 2, title = "Updated"))
        val source = dao.pagingSource()
        val result = source.load(PagingSource.LoadParams.Refresh<Int>(null, 20, false))
            as PagingSource.LoadResult.Page
        assertEquals(listOf(2, 1), result.data.map { it.id })
        assertEquals(listOf(0L, 1L), result.data.map { it.discoveryOrder })
        assertNull(result.nextKey)
    }

    @Test fun versionOneMigrationPreservesRowsAndTheirPreviousOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ordering-migration-${java.util.UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("CREATE TABLE movies (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, overview TEXT, posterPath TEXT, backdropPath TEXT, releaseDate TEXT, voteAverage REAL)")
                old.execSQL("INSERT INTO movies VALUES (9, 'Zulu', 'Keep me', '/poster.jpg', NULL, '2025-01-01', 7.5)")
                old.execSQL("INSERT INTO movies (id, title) VALUES (3, 'Alpha'), (1, 'Alpha')")
                old.version = 1
            }
            val migrated = Room.databaseBuilder(context, MovieDatabase::class.java, name)
                .addMigrations(MovieDatabase.MIGRATION_1_2).build()
            try {
                    val migratedDao = migrated.movieDao()
                    val rows = migratedDao.observeMovies().first()
                    assertEquals(listOf(1, 3, 9), rows.map { it.id })
                    assertEquals(listOf(0L, 1L, 2L), rows.map { it.discoveryOrder })
                    assertEquals(MovieEntity(9, "Zulu", "Keep me", "/poster.jpg", null, "2025-01-01", 7.5, 2), rows.last())
                    migratedDao.saveVerified(movie().copy(id = 10, title = "A new movie"))
                    migratedDao.saveVerified(rows.last().copy(title = "Renamed"))
                    assertEquals(listOf(1, 3, 9, 10), migratedDao.observeMovies().first().map { it.id })
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun insertReadUpdateAndDelete() = runBlocking {
        val movie = movie()
        dao.upsert(movie)
        assertEquals(movie, dao.findMovie(42))
        dao.upsert(movie.copy(title = "Updated"))
        assertEquals("Updated", dao.findMovie(42)?.title)
        dao.delete(42)
        assertNull(dao.findMovie(42))
    }

    @Test fun realRoomFlowsEmitInsertionAndRevocation() = runBlocking {
        val lists = Channel<List<MovieEntity>>(Channel.UNLIMITED)
        val singles = Channel<MovieEntity?>(Channel.UNLIMITED)
        val listJob = launch(start = CoroutineStart.UNDISPATCHED) { dao.observeMovies().collect { lists.send(it) } }
        val singleJob = launch(start = CoroutineStart.UNDISPATCHED) { dao.observeMovie(42).collect { singles.send(it) } }
        try {
            withTimeout(5_000) {
                assertTrue(lists.receive().isEmpty())
                assertNull(singles.receive())
                val movie = movie()
                dao.upsert(movie)
                assertEquals(listOf(movie), lists.receive())
                assertEquals(movie, singles.receive())
                dao.delete(42)
                assertTrue(lists.receive().isEmpty())
                assertNull(singles.receive())
            }
        } finally {
            listJob.cancel()
            singleJob.cancel()
            listJob.join()
            singleJob.join()
            lists.close()
            singles.close()
        }
    }

    private fun movie() = MovieEntity(42, "Example", "Overview", null, null, "2026-01-01", 7.5)
}
