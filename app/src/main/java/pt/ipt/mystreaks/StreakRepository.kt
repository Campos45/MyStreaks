package pt.ipt.mystreaks

import kotlinx.coroutines.flow.Flow

class StreakRepository(private val streakDao: StreakDao) {

    // O Flow vai emitir os dados automaticamente sempre que a base de dados for alterada
    val allStreaks: Flow<List<Streak>> = streakDao.getAllStreaks()

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