package com.example.movieapp.data.backend

import androidx.paging.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.HttpException

class BackendRepository(private val api: BackendApi, private val dao: DemoMovieDao) {
    @OptIn(ExperimentalPagingApi::class)
    private fun pages(query: String) = Pager(
        PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = true),
        remoteMediator = BackendRemoteMediator(api, dao, query),
        pagingSourceFactory = { dao.pagingSource(query) },
    ).flow

    fun homeMovies() = pages("")
    fun searchMovies(query: String): Flow<PagingData<DemoMovie>> =
        if (query.isBlank()) flowOf(PagingData.empty()) else pages(query.trim())
    fun observeMovie(id: Int) = dao.observeMovie(id)

    suspend fun refreshMovie(id: Int): Boolean {
        require(id > 0)
        val movie = try { api.movie(id) } catch (error: HttpException) {
            if (error.code() != 404) throw error
            dao.delete(id)
            return false
        }
        require(movie.id == id)
        dao.save(listOf(movie.toMovie()))
        return true
    }

    suspend fun reconcile() {
        for (id in dao.ids()) refreshMovie(id)
    }
}

@OptIn(ExperimentalPagingApi::class)
class BackendRemoteMediator(private val api: BackendApi, private val dao: DemoMovieDao,
    private val query: String = "") : RemoteMediator<Int, DemoMovie>() {
    private var nextPage: Int? = 1
    override suspend fun load(loadType: LoadType, state: PagingState<Int, DemoMovie>): MediatorResult {
        if (loadType == LoadType.PREPEND) return MediatorResult.Success(true)
        val page = if (loadType == LoadType.REFRESH) 1 else nextPage ?: return MediatorResult.Success(true)
        return try {
            val response = if (query.isBlank()) api.movies(page, 20) else api.search(query, page, 20)
            require(response.page == page && response.pageSize == 20 && response.totalItems >= 0)
            require(response.hasNextPage == (page < response.totalPages))
            val movies = response.items.map { it.toMovie() }
            dao.save(movies)
            nextPage = if (response.hasNextPage) page + 1 else null
            MediatorResult.Success(!response.hasNextPage)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { MediatorResult.Error(error) }
    }
}
