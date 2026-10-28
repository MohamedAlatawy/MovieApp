package com.example.movieapp.di

import android.content.Context
import com.example.movieapp.data.backend.*
import com.example.movieapp.data.BuildConfig
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
}