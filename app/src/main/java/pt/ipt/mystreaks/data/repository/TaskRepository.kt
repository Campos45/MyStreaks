package pt.ipt.mystreaks.data.repository

import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.dao.TaskDao
import pt.ipt.mystreaks.data.model.Task

class TaskRepository(private val taskDao: TaskDao) {
    val pendingTasks: Flow<List<Task>> = taskDao.getPendingTasks()
    val completedTasks: Flow<List<Task>> = taskDao.getCompletedTasks()

    suspend fun insert(task: Task): Long { return taskDao.insert(task) }
    suspend fun update(task: Task) { taskDao.update(task) }
    suspend fun delete(task: Task) { taskDao.delete(task) }
}