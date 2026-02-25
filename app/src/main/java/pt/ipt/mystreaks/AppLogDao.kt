package pt.ipt.mystreaks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLogDao {
    @Insert
    suspend fun insertLog(log: AppLog)

    // Caso no futuro queiras criar um ecrã para ler os logs, já temos a query pronta!
    @Query("SELECT * FROM logs_table ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AppLog>>
}