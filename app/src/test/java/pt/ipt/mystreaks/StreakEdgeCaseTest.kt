package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ipt.mystreaks.data.model.Streak
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StreakEdgeCaseTest {

    private fun dateToMillis(localDate: LocalDate): Long {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun weeklyStreak_multipleCompletionsInSameWeek_doesNotBreakStreak() {
        val today = LocalDate.now()
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val thisWednesday = thisMonday.plusDays(2)
        val thisSaturday = thisMonday.plusDays(5)

        val lastMonday = thisMonday.minusWeeks(1)
        val lastFriday = lastMonday.plusDays(4)

        // Utilizador cumpriu 2 vezes na semana passada e 2 vezes nesta semana
        val streak = Streak(
            name = "Treino Semanal",
            type = "S",
            completedDates = listOf(
                dateToMillis(thisWednesday),
                dateToMillis(thisSaturday),
                dateToMillis(lastMonday),
                dateToMillis(lastFriday)
            )
        )

        // Deveria ser 2 semanas consecutivas cumpridas!
        assertEquals(2, streak.calculateCurrentStreak())
    }

    @Test
    fun monthlyStreak_multipleCompletionsInSameMonth_doesNotBreakStreak() {
        val today = LocalDate.now()
        val thisMonthDay5 = today.withDayOfMonth(5)
        val thisMonthDay20 = today.withDayOfMonth(20)

        val lastMonthDay10 = today.minusMonths(1).withDayOfMonth(10)
        val lastMonthDay25 = today.minusMonths(1).withDayOfMonth(25)

        val streak = Streak(
            name = "Relatório Mensal",
            type = "M",
            completedDates = listOf(
                dateToMillis(thisMonthDay5),
                dateToMillis(thisMonthDay20),
                dateToMillis(lastMonthDay10),
                dateToMillis(lastMonthDay25)
            )
        )

        // Deveria ser 2 meses consecutivos cumpridos!
        assertEquals(2, streak.calculateCurrentStreak())
    }
}
