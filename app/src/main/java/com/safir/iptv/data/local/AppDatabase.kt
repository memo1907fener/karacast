package com.safir.iptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        RecentEntity::class,
        ProgramEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDao(): RecentDao
    abstract fun programDao(): ProgramDao

    companion object {
        /**
         * Version 2: the guide is filed per access. Written out rather than left
         * to the destructive fallback, which would also take the favourites and
         * the recently-watched list with it.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programs ADD COLUMN sourceId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("DROP INDEX IF EXISTS index_programs_epgId_startMs")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_programs_sourceId_epgId_startMs " +
                        "ON programs (sourceId, epgId, startMs)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "safir-iptv.db"
            )
                // The catalogue is a cache that can always be re-synced, so a
                // destructive migration is an acceptable trade for simplicity.
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
