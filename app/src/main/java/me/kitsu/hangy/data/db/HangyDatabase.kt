package me.kitsu.hangy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RoutineEntity::class,
        SessionEntity::class,
        RepResultEntity::class,
        SampleEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HangyDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * v2 adds the per-rep tension window (`tStartMs`/`tEndMs`) so a session's rep slices can be
         * reconstructed for the timeline and rep-comparison charts. Rows from v1 default to `0/0`,
         * which the charts read as "no tension window" (no marks drawn).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rep_results ADD COLUMN tStartMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rep_results ADD COLUMN tEndMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        // No destructive fallback: migrations preserve existing data across schema changes.
        fun build(context: Context): HangyDatabase = Room.databaseBuilder(
            context.applicationContext,
            HangyDatabase::class.java,
            "hangy.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
