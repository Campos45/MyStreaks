package pt.ipt.mystreaks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    // Tarefas por fazer
    @Query("SELECT * FROM tasks_table WHERE isCompleted = 0 ORDER BY id DESC")
    fun getPendingTasks(): Flow<List<Task>>

    // Tarefas concluídas
    @Query("SELECT * FROM tasks_table WHERE isCompleted = 1 ORDER BY completionDate DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks_table")
    suspend fun getAllTasksSync(): List<Task>

    // NOVO: Puxar todas as tags únicas das tarefas
    @Query("SELECT DISTINCT tag FROM tasks_table WHERE tag IS NOT NULL AND tag != ''")
    suspend fun getAllTagsSync(): List<String>
}