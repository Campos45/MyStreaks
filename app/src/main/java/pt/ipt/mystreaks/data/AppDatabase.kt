package pt.ipt.mystreaks.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pt.ipt.mystreaks.data.model.AppLog
import pt.ipt.mystreaks.data.dao.AppLogDao
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.dao.MyListDao
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.dao.StreakDao
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.dao.TagDao
import pt.ipt.mystreaks.data.model.Task
import pt.ipt.mystreaks.data.dao.TaskDao

// 1. Adicionámos a Tag::class à lista de entidades e mudámos a versão para 2!
@Database(entities = [Task::class, AppLog::class, Streak::class, Tag::class, MyList::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun appLogDao(): AppLogDao
    abstract fun streakDao(): StreakDao
    abstract fun tagDao(): TagDao // 2. Adicionámos o novo DAO

    abstract fun myListDao(): MyListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `type` TEXT NOT NULL DEFAULT 'S')")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lists_table` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `content` TEXT NOT NULL DEFAULT '',
                        `tag` TEXT,
                        `backgroundColor` TEXT,
                        `isPinned` INTEGER NOT NULL DEFAULT 0,
                        `orderIndex` INTEGER NOT NULL DEFAULT 0,
                        `isArchived` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mystreaks_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}