package pt.ipt.mystreaks.data.repository

import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.dao.DiaryDao
import pt.ipt.mystreaks.data.model.DiaryEntry

class DiaryRepository(private val diaryDao: DiaryDao) {
    val allEntries: Flow<List<DiaryEntry>> = diaryDao.getAllEntriesFlow()

    fun getEntryByDateFlow(dayMillis: Long): Flow<DiaryEntry?> = diaryDao.getEntryByDateFlow(dayMillis)

    suspend fun getEntryByDate(dayMillis: Long): DiaryEntry? = diaryDao.getEntryByDate(dayMillis)

    suspend fun upsert(entry: DiaryEntry) = diaryDao.upsert(entry)

    suspend fun delete(entry: DiaryEntry) = diaryDao.delete(entry)
}
