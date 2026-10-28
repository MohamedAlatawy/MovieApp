package com.example.movieapp.data.backend

import androidx.paging.*
import com.example.movieapp.domain.model.*
import com.example.movieapp.domain.repository.MovieRepository
import kotlinx.coroutines.flow.*

/** Only Home listing seeds persistent rows. Search and Search Details never access the DAO. */
class BackendRepository(private val api: BackendApi, private val dao: DemoMovieDao) : MovieRepository {
    @OptIn(ExperimentalPagingApi::class)
    override fun homeMovies(): Flow<PagingData<CachedMovie>> {
        val sources = InvalidatingPagingSourceFactory { dao.pagingSource() }
        return Pager(PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = true),
            remoteMediator = BackendRemoteMediator(api, dao, sources::invalidate),
            pagingSourceFactory = sources).flow.map { page -> page.map { it.toCachedMovie() } }
    }

    override fun searchMovies(query: String): Flow<PagingData<MovieSummary>> =
        if (query.isBlank()) flowOf(PagingData.empty()) else Pager(
            PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { BackendSearchPagingSource(api, query.trim()) }).flow

    override fun observeCachedMovie(id: Int) = dao.observeMovie(id).map { it?.toCachedMovie() }

    override suspend fun fetchSearchDetails(movieId: Int): MovieDetails = backendAccess {
        validateId(movieId)
        val movie = api.movie(movieId)
        if (movie.id != movieId) throw MovieAccessException.InvalidIdentity()
        movie.toMovie().toDetails()
    }

    /** Missing rows are a no-op, including delayed or duplicate FCM work. Never seed Home here. */
    override suspend fun refreshHomeMovieDetails(id: Int): Unit = backendAccess {
        validateId(id)
        if (dao.movie(id) == null) return@backendAccess
        val movie = api.movie(id)
        if (movie.id != id) throw MovieAccessException.InvalidIdentity()
        dao.updateExisting(movie.toMovie())
    }

    suspend fun reconcile() {
        // One failed row must not prevent later cached IDs from reconciling.
        var failure: MovieAccessException? = null
        for (id in dao.ids()) {
            try { refreshHomeMovieDetails(id) } catch (error: MovieAccessException) { failure = error }
        }
        failure?.let { throw it }
    }
}

@OptIn(ExperimentalPagingApi::class)
class BackendRemoteMediator(
    private val api: BackendApi,
    private val dao: DemoMovieDao,
    private val invalidateEmptyPage: () -> Unit = {},
) : RemoteMediator<Int, DemoMovie>() {
    private var nextPage: Int? = 1
    override suspend fun load(loadType: LoadType, state: PagingState<Int, DemoMovie>): MediatorResult {
        if (loadType == LoadType.PREPEND) return MediatorResult.Success(true)
        if (loadType == LoadType.REFRESH) nextPage = 1
        val page = nextPage ?: return MediatorResult.Success(true)
        return try {
            backendAccess {
                val response = api.movies(page, 20)
                val movies = response.validatedMovies(page)
                val changed = dao.save(movies)
                nextPage = if (response.hasNextPage) page + 1 else null
                // Empty/unchanged pages do not invalidate Room; advance first, then permit the next native boundary.
                if (!changed && response.hasNextPage) invalidateEmptyPage()
                MediatorResult.Success(!response.hasNextPage)
            }
        } catch (error: MovieAccessException) { MediatorResult.Error(error) }
    }
}

class BackendSearchPagingSource(private val api: BackendApi, private val query: String) :
    PagingSource<Int, MovieSummary>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieSummary> {
        if (query.isBlank()) return LoadResult.Page(emptyList(), null, null)
        val page = params.key ?: 1
        return try {
            backendAccess {
                val response = api.search(query, page, 20)
                val movies = response.validatedMovies(page)
                LoadResult.Page(movies.map { MovieSummary(it.id, it.title, it.posterPath, it.releaseDate) },
                    if (page > 1) page - 1 else null, if (response.hasNextPage) page + 1 else null)
            }
        } catch (error: MovieAccessException) { LoadResult.Error(error) }
    }
    override fun getRefreshKey(state: PagingState<Int, MovieSummary>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }
}
