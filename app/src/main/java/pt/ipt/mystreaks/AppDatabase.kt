package pt.ipt.mystreaks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Streak::class, Task::class, AppLog::class], version = 13, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun streakDao(): StreakDao
    abstract fun taskDao(): TaskDao
    abstract fun appLogDao(): AppLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // NOVO: A Regra para não perder dados! (Ajusta os números 11 e 12 conforme as tuas versões)
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Adiciona Notas e Prazos às Tarefas
                database.execSQL("ALTER TABLE tasks_table ADD COLUMN notes TEXT")
                database.execSQL("ALTER TABLE tasks_table ADD COLUMN dueDate INTEGER")

                // Adiciona Dias Específicos às Streaks (como é uma lista, o TypeConverter guarda como texto JSON vazio '[]')
                database.execSQL("ALTER TABLE streaks_table ADD COLUMN notifyDays TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks_table ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mystreaks_database"
                )
                    .addMigrations(MIGRATION_11_12) // <- ADICIONA ISTO AQUI!
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                    // .fallbackToDestructiveMigration() <- Podes deixar isto se já lá estava, o addMigrations tem prioridade.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}