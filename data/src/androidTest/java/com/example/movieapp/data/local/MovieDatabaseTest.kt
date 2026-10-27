package com.example.movieapp.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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
