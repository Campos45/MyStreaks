package pt.ipt.mystreaks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.model.DiaryEntry

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE dateMillis = :dayMillis LIMIT 1")
    suspend fun getEntryByDate(dayMillis: Long): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE dateMillis = :dayMillis LIMIT 1")
    fun getEntryByDateFlow(dayMillis: Long): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entries")
    fun getAllEntriesFlow(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries")
    suspend fun getAllEntriesSync(): List<DiaryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)
}
