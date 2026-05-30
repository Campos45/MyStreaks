package pt.ipt.mystreaks.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.ipt.mystreaks.data.dao.StreakDao
import pt.ipt.mystreaks.data.model.Streak

class StreakRepository(private val streakDao: StreakDao) {

    val activeStreaks: Flow<List<Streak>> = streakDao.getActiveStreaks().map { list ->
        list.map { it.toDynamicStreak() }
    }
    val archivedStreaks: Flow<List<Streak>> = streakDao.getArchivedStreaks().map { list ->
        list.map { it.toDynamicStreak() }
    }

    suspend fun insert(streak: Streak): Long {
        return streakDao.insert(streak)
    }

    suspend fun update(streak: Streak) {
        streakDao.update(streak)
    }

    suspend fun delete(streak: Streak) {
        streakDao.delete(streak)
    }
}