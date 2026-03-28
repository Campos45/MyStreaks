package pt.ipt.mystreaks.data.repository

import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.dao.AppLogDao
import pt.ipt.mystreaks.data.model.AppLog

class LogRepository(private val logDao: AppLogDao) {
    // NOVO: Lê todos os logs!
    val allLogs: Flow<List<AppLog>> = logDao.getAllLogs()

    suspend fun deleteAll() {
        logDao.deleteAll()
    }
    suspend fun insertLog(log: AppLog) {
        logDao.insertLog(log)
    }
}