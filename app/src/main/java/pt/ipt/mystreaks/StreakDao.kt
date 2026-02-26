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

    // NOVO: Vai buscar apenas as atividades ativas (isArchived = 0)
    @Query("SELECT * FROM streaks_table WHERE isArchived = 0 ORDER BY orderIndex ASC, id ASC")
    fun getActiveStreaks(): Flow<List<Streak>>

    // NOVO: Vai buscar apenas o arquivo (isArchived = 1)
    @Query("SELECT * FROM streaks_table WHERE isArchived = 1 ORDER BY id ASC")
    fun getArchivedStreaks(): Flow<List<Streak>>

    // Para o motor de tempo (só afeta as ativas)
    @Query("SELECT * FROM streaks_table WHERE isArchived = 0")
    suspend fun getActiveStreaksList(): List<Streak>

    @Update
    suspend fun updateAll(streaks: List<Streak>)

    // Adiciona esta linha no teu StreakDao
    @Query("SELECT * FROM streaks_table WHERE id = :id")
    suspend fun getStreakById(id: Int): Streak?

    @Query("SELECT * FROM streaks_table")
    suspend fun getAllStreaksSync(): List<Streak>

    // NOVO: Puxar todas as tags únicas que existem
    @Query("SELECT DISTINCT tag FROM streaks_table WHERE tag IS NOT NULL AND tag != ''")
    suspend fun getAllTagsSync(): List<String>
}