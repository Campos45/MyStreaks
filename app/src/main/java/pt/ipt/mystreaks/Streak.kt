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
    var orderIndex: Int = 0,

    // NOVO: Campos para as Notificações Personalizadas
    var remindHour: Int? = null,
    var remindMinute: Int? = null,
    var remindExtra: Int? = null // Guarda o Dia da Semana (1 a 7) ou Dia do Mês (1 a 31)
)