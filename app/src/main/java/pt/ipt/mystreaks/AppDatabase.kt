package pt.ipt.mystreaks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

        // 3. Ensinamos o Android a atualizar a base de dados da Versão 1 para a Versão 2 sem apagar os teus dados!
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mystreaks_database"
                )
                    .addMigrations(MIGRATION_1_2) // Adicionamos a migração aqui
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}