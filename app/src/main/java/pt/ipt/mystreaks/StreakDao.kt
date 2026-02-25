package pt.ipt.mystreaks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(streak: Streak)

    @Update
    suspend fun update(streak: Streak)

    @Delete
    suspend fun delete(streak: Streak)

    @Query("SELECT * FROM streaks_table ORDER BY id ASC")
    fun getAllStreaks(): Flow<List<Streak>>

    // --- NOVO: Funções para o motor de tempo (Worker) ---
    @Query("SELECT * FROM streaks_table")
    suspend fun getStreaksList(): List<Streak>

    @Update
    suspend fun updateAll(streaks: List<Streak>)
}