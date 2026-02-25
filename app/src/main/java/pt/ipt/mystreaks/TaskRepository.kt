package pt.ipt.mystreaks

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val pendingTasks: Flow<List<Task>> = taskDao.getPendingTasks()
    val completedTasks: Flow<List<Task>> = taskDao.getCompletedTasks()

    suspend fun insert(task: Task) { taskDao.insert(task) }
    suspend fun update(task: Task) { taskDao.update(task) }
    suspend fun delete(task: Task) { taskDao.delete(task) }
}