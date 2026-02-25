package pt.ipt.mystreaks

import kotlinx.coroutines.flow.Flow

class LogRepository(private val logDao: AppLogDao) {
    // NOVO: Lê todos os logs!
    val allLogs: Flow<List<AppLog>> = logDao.getAllLogs()

    suspend fun insertLog(log: AppLog) {
        logDao.insertLog(log)
    }
}