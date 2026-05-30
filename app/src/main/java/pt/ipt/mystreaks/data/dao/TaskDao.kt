package pt.ipt.mystreaks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.model.Task

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insert(task: Task): Long

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


}