package pt.ipt.mystreaks

import androidx.room.Entity
import androidx.room.PrimaryKey

// NOVO: Estrutura que guarda os dados de um recorde passado
data class StreakRecord(
    val count: Int,
    val startDate: Long,
    val endDate: Long
)

@Entity(tableName = "streaks_table")
data class Streak(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String,
    var count: Int = 0,
    var isCompleted: Boolean = false,
    var lastResetDate: Long = System.currentTimeMillis(),
    var isArchived: Boolean = false,
    var currentStartDate: Long? = null, // Guarda o dia em que a streak atual começou
    var history: List<StreakRecord> = emptyList() // Guarda os recordes antigos
)