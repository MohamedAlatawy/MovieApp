package com.example.movieapp.di

import android.content.Context
import com.example.movieapp.data.backend.*
import com.example.movieapp.data.BuildConfig
import com.example.movieapp.domain.repository.MovieRepository
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
    @Provides @Singleton fun database(@ApplicationContext context: Context) = DemoDatabase.create(context)
    @Provides @Singleton fun dao(database: DemoDatabase) = database.movies()
    @Provides @Singleton fun api() = BackendApi.create(BuildConfig.BACKEND_BASE_URL)
    @Provides @Singleton fun repository(api: BackendApi, dao: DemoMovieDao) = BackendRepository(api, dao)
    @Provides fun contract(repository: BackendRepository): MovieRepository = repository
    @Provides fun home(repository: MovieRepository) = GetHomeMoviesUseCase(repository)
    @Provides fun search(repository: MovieRepository) = SearchMoviesUseCase(repository)
    @Provides fun observe(repository: MovieRepository) = ObserveCachedMovieUseCase(repository)
    @Provides fun refresh(repository: MovieRepository) = RefreshHomeMovieDetailsUseCase(repository)
    @Provides fun searchDetails(repository: MovieRepository) = GetSearchMovieDetailsUseCase(repository)
}
