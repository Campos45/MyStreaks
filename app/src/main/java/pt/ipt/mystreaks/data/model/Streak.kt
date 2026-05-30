package pt.ipt.mystreaks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

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
    var remindHour: Int? = null,
    var remindMinute: Int? = null,
    var remindExtra: Int? = null,
    var tag: String? = null,

    var lastIncrementDate: Long = 0L,

    // NOVO: Guarda a lista de dias em que a tarefa foi feita
    var completedDates: List<Long> = emptyList(),

    var notifyDays: List<Int> = emptyList() // Guarda os dias da semana ou mês (ex: [1, 15, 30])
) {
    fun calculateCurrentStreak(): Int {
        if (completedDates.isEmpty()) return 0

        val todayLd = LocalDate.now()
        val sortedDates = completedDates.map {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }.distinct().sortedDescending() // Mais recente primeiro

        val latestCompletion = sortedDates.first()

        val expectedStart = when (type) {
            "D" -> {
                if (ChronoUnit.DAYS.between(latestCompletion, todayLd) > 1) {
                    return 0 // quebrou!
                }
                if (latestCompletion == todayLd) todayLd else todayLd.minusDays(1)
            }
            "S" -> {
                val latestMon = latestCompletion.with(DayOfWeek.MONDAY)
                val todayMon = todayLd.with(DayOfWeek.MONDAY)
                if (ChronoUnit.WEEKS.between(latestMon, todayMon) > 1) {
                    return 0 // quebrou!
                }
                if (latestMon == todayMon) todayMon else todayMon.minusWeeks(1)
            }
            "M" -> {
                val latestMonthStart = latestCompletion.withDayOfMonth(1)
                val todayMonthStart = todayLd.withDayOfMonth(1)
                if (ChronoUnit.MONTHS.between(latestMonthStart, todayMonthStart) > 1) {
                    return 0 // quebrou!
                }
                if (latestMonthStart == todayMonthStart) todayMonthStart else todayMonthStart.minusMonths(1)
            }
            else -> todayLd
        }

        var countCalc = 0
        var currentDate = expectedStart

        for (date in sortedDates) {
            val matches = when (type) {
                "D" -> date == currentDate
                "S" -> date.with(DayOfWeek.MONDAY) == currentDate.with(DayOfWeek.MONDAY)
                "M" -> date.withDayOfMonth(1) == currentDate.withDayOfMonth(1)
                else -> false
            }
            if (matches) {
                countCalc++
                currentDate = when (type) {
                    "D" -> currentDate.minusDays(1)
                    "S" -> currentDate.minusWeeks(1)
                    "M" -> currentDate.minusMonths(1)
                    else -> currentDate
                }
            } else {
                break // Quebra da sequência consecutiva!
            }
        }
        return countCalc
    }

    fun isCompletedToday(): Boolean {
        if (completedDates.isEmpty()) return false
        val todayLd = LocalDate.now()
        val latestCompletion = completedDates.map {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }.maxOrNull() ?: return false

        return when (type) {
            "D" -> latestCompletion == todayLd
            "S" -> ChronoUnit.WEEKS.between(latestCompletion.with(DayOfWeek.MONDAY), todayLd.with(DayOfWeek.MONDAY)) == 0L
            "M" -> ChronoUnit.MONTHS.between(latestCompletion.withDayOfMonth(1), todayLd.withDayOfMonth(1)) == 0L
            else -> false
        }
    }

    fun toDynamicStreak(): Streak {
        return this.copy(
            count = calculateCurrentStreak(),
            isCompleted = isCompletedToday()
        )
    }
}