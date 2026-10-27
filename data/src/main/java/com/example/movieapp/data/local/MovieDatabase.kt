package com.example.movieapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MovieEntity::class], version = 1, exportSchema = true)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao

    companion object {
        fun create(context: Context): MovieDatabase =
            Room.databaseBuilder(context.applicationContext, MovieDatabase::class.java, "verified-movies.db")
                .build()
    }
}
