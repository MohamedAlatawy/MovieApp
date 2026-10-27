package com.example.movieapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MovieEntity::class], version = 2, exportSchema = true)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN discoveryOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    UPDATE movies SET discoveryOrder = (
                        SELECT COUNT(*) FROM movies AS preceding
                        WHERE preceding.title < movies.title
                           OR (preceding.title = movies.title AND preceding.id < movies.id)
                    )
                """.trimIndent())
            }
        }

        fun create(context: Context): MovieDatabase =
            Room.databaseBuilder(context.applicationContext, MovieDatabase::class.java, "verified-movies.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
