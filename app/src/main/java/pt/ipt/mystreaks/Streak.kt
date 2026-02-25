package pt.ipt.mystreaks

import androidx.room.Entity
import androidx.room.PrimaryKey

data class StreakRecord(val count: Int, val startDate: Long, val endDate: Long)

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
    var currentStartDate: Long? = null,
    var history: List<StreakRecord> = emptyList(),
    var orderIndex: Int = 0 // NOVO: Guarda a posição na lista
)