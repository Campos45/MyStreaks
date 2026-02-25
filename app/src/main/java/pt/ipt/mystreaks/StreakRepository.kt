package pt.ipt.mystreaks

import kotlinx.coroutines.flow.Flow

class StreakRepository(private val streakDao: StreakDao) {

    val activeStreaks: Flow<List<Streak>> = streakDao.getActiveStreaks()
    val archivedStreaks: Flow<List<Streak>> = streakDao.getArchivedStreaks()

    suspend fun insert(streak: Streak) {
        streakDao.insert(streak)
    }

    suspend fun update(streak: Streak) {
        streakDao.update(streak)
    }

    suspend fun delete(streak: Streak) {
        streakDao.delete(streak)
    }
}