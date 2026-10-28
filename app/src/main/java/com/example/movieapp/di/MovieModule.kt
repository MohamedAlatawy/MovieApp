package com.example.movieapp.di

import android.content.Context
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieDatabase
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.domain.repository.MovieRepository as MovieRepositoryContract
import com.example.movieapp.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MovieModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): MovieDatabase = MovieDatabase.create(context)

    @Provides @Singleton
    fun movieDao(database: MovieDatabase): MovieDao = database.movieDao()

    @Provides @Singleton
    fun tmdbClient(): TmdbClient = TmdbClient.create()

    @Provides @Singleton
    fun repository(client: TmdbClient, dao: MovieDao): MovieRepositoryContract = MovieRepository(client, dao)
    @Provides
    fun home(repository: MovieRepositoryContract) = GetHomeMoviesUseCase(repository)

    @Provides
    fun search(repository: MovieRepositoryContract) = SearchMoviesUseCase(repository)

    @Provides
    fun observe(repository: MovieRepositoryContract) = ObserveCachedMovieUseCase(repository)

    @Provides
    fun refresh(repository: MovieRepositoryContract) = RefreshHomeMovieDetailsUseCase(repository)

    @Provides
    fun searchDetails(repository: MovieRepositoryContract) = GetSearchMovieDetailsUseCase(repository)
}