package com.example.movieapp.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieapp.data.backend.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DemoDatabaseTest {
    @Test fun atomicRollbackAndExistingOnlyMetadataUpdates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        try {
            val dao = db.movies()
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_two BEFORE INSERT ON demo_movies WHEN NEW.id = 2 BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
            assertTrue(runCatching { dao.save(listOf(DemoMovie(1,"First"),DemoMovie(2,"Second"))) }.isFailure)
            assertTrue(dao.ids().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_two")
            val old = DemoMovie(1,"Old",posterPath="https://example.org/poster.jpg",genres="Family")
            dao.save(listOf(old))
            dao.updateExisting(old.copy(title="New",posterPath=null,genres="",lastModified="2026-01-01T00:00:00Z"))
            assertEquals("https://example.org/poster.jpg",dao.movie(1)?.posterPath)
            assertEquals("",dao.movie(1)?.genres)
            dao.updateExisting(DemoMovie(9,"Uncached"))
            assertEquals(listOf(1),dao.ids())
        } finally { db.close() }
    }

    @Test fun detailsRequestDoesNotReinsertRowRemovedDuringNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        try {
            val dao = db.movies()
            dao.save(listOf(DemoMovie(1,"Old")))
            val api = object : BackendApi {
                override suspend fun movies(page: Int,size: Int): BackendPage = error("No list")
                override suspend fun search(query: String,page: Int,size: Int): BackendPage = error("No search")
                override suspend fun movie(id: Int): BackendMovieDto {
                    dao.delete(id)
                    return BackendMovieDto(id,"New","Demo","2020-01-01",null,6.0,emptyList(),false,null,
                        "2026-01-01T00:00:00Z","synthetic-demo","unverified",true)
                }
            }
            BackendRepository(api,dao).refreshHomeMovieDetails(1)
            assertTrue(dao.ids().isEmpty())
        } finally { db.close() }
    }

    @Test fun savedCacheSurvivesDatabaseReopenAndNetworkFailure() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "demo-test-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, DemoDatabase::class.java, name).build()
        try {
            db.movies().save(listOf(DemoMovie(9001, "Saved demo")))
            db.close()
            db = Room.databaseBuilder(context, DemoDatabase::class.java, name).build()
            val offline = object : BackendApi {
                override suspend fun movie(id: Int): BackendMovieDto = throw java.io.IOException()
                override suspend fun movies(page: Int, size: Int): BackendPage = throw java.io.IOException()
                override suspend fun search(query: String, page: Int, size: Int): BackendPage = throw java.io.IOException()
            }
            val repo = BackendRepository(offline, db.movies())
            assertEquals("Saved demo", repo.observeCachedMovie(9001).first()?.title)
            assertTrue(runCatching { repo.refreshHomeMovieDetails(9001) }.isFailure)
            assertEquals("Saved demo", repo.observeCachedMovie(9001).first()?.title)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun orderingTitleReconciliationAndVersionChecks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        try {
            val dao = db.movies()
            val first = DemoMovie(1, "Toy Harbor", lastModified = "2026-01-01T00:00:00Z")
            dao.save(listOf(DemoMovie(2, "A movie"), first))
            suspend fun rows(query: String) = (dao.pagingSource().load(
                PagingSource.LoadParams.Refresh(null,20,true)) as PagingSource.LoadResult.Page).data
            assertEquals(listOf(1,2), rows("").map { it.id })

            dao.save(listOf(first.copy(title = "Renamed", lastModified = "2026-02-01T00:00:00Z")))
            assertEquals("Renamed", rows("").first().title)
            dao.save(listOf(first))
            assertEquals("Renamed", dao.observeMovie(1).first()?.title)
            assertEquals(2, dao.ids().size)
        } finally { db.close() }
    }

    @Test fun demoWritesNeverTouchVerifiedRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val demo = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        val verified = Room.inMemoryDatabaseBuilder(context, MovieDatabase::class.java).build()
        try {
            verified.movieDao().saveVerified(MovieEntity(1,"Verified",null,null,null,null,null))
            demo.movies().save(listOf(DemoMovie(1,"Demo")))
            assertEquals("Verified", verified.movieDao().observeMovie(1).first()?.title)
            assertEquals("Demo", demo.movies().observeMovie(1).first()?.title)
        } finally { demo.close(); verified.close() }
    }
}
