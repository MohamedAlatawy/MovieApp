package com.example.movieapp.data.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import com.example.movieapp.domain.model.CachedMovie
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import kotlinx.coroutines.flow.map
import com.example.movieapp.domain.model.MovieAccessException
import com.example.movieapp.domain.model.MovieDetails
import com.example.movieapp.domain.repository.MovieRepository as MovieRepositoryContract
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.example.movieapp.data.paging.HomeRemoteMediator
import com.example.movieapp.data.paging.SearchPagingSource

import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.remote.TmdbClient

class MovieRepository(
    private val client: TmdbClient,
    private val dao: MovieDao,
) : MovieRepositoryContract {
    @OptIn(ExperimentalPagingApi::class)
    override fun homeMovies(): Flow<PagingData<CachedMovie>> {
        val sources = InvalidatingPagingSourceFactory(dao::pagingSource)
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = true),
            remoteMediator = HomeRemoteMediator(client, dao, sources::invalidate),
            pagingSourceFactory = sources,
        ).flow.map { paging -> paging.map { it.toCachedMovie() } }
    }

    override fun searchMovies(query: String) = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { SearchPagingSource(query.trim(), client) },
    ).flow.map { paging -> paging.map { it.toMovieSummary() } }
    override fun observeCachedMovie(id: Int) = dao.observeMovie(id).map { it?.toCachedMovie() }

    override suspend fun fetchSearchDetails(movieId: Int): MovieDetails = movieAccess {
        if (movieId <= 0) throw MovieAccessException.InvalidIdentity()
        client.movieDetails(movieId).also {
            if (it.id != movieId) throw MovieAccessException.InvalidIdentity()
        }.toMovieDetails()
    }

    override suspend fun refreshHomeMovieDetails(id: Int): Unit = movieAccess {
        if (id <= 0) throw MovieAccessException.InvalidIdentity()
        if (dao.findMovie(id) == null) throw MovieAccessException.InvalidIdentity()
        val details = client.movieDetails(id)
        if (details.id != id) throw MovieAccessException.InvalidIdentity()
        dao.updateCachedDetails(details.toCacheEntity(), genresProvided = details.genres != null)
    }
}
