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

    // Vai buscar todas as streaks e atualiza a lista automaticamente sempre que houver mudanças (usando Flow)
    @Query("SELECT * FROM streaks_table ORDER BY id ASC")
    fun getAllStreaks(): Flow<List<Streak>>
}