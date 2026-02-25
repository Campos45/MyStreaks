package pt.ipt.mystreaks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Streak::class], version = 2, exportSchema = false) // Versão 2!
abstract class AppDatabase : RoomDatabase() {

    abstract fun streakDao(): StreakDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mystreaks_database"
                )
                    .fallbackToDestructiveMigration() // Se a versão mudar, recria a tabela sem dar erro
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}