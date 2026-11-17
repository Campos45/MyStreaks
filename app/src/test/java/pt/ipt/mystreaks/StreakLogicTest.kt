package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.data.model.Streak
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StreakLogicTest {

    private fun dateToMillis(localDate: LocalDate): Long {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun dailyStreak_completedToday_counts1() {
        val today = LocalDate.now()
        val streak = Streak(
            name = "Exercício",
            type = "D",
            completedDates = listOf(dateToMillis(today))
        )

        assertEquals(1, streak.calculateCurrentStreak())
        assertTrue(streak.isCompletedToday())
    }

    @Test
    fun dailyStreak_completedYesterday_counts1_butNotCompletedToday() {
        val yesterday = LocalDate.now().minusDays(1)
        val streak = Streak(
            name = "Leitura",
            type = "D",
            completedDates = listOf(dateToMillis(yesterday))
        )

        // Ontem conta para a streak (o utilizador ainda tem hoje para cumprir)
        assertEquals(1, streak.calculateCurrentStreak())
        assertFalse(streak.isCompletedToday())
    }

    @Test
    fun dailyStreak_consecutive5Days_counts5() {
        val today = LocalDate.now()
        val dates = (0..4).map { dateToMillis(today.minusDays(it.toLong())) }

        val streak = Streak(
            name = "Meditação",
            type = "D",
            completedDates = dates
        )

        assertEquals(5, streak.calculateCurrentStreak())
        assertTrue(streak.isCompletedToday())
    }

    @Test
    fun dailyStreak_brokenTwoDaysAgo_counts0() {
        val twoDaysAgo = LocalDate.now().minusDays(2)
        val streak = Streak(
            name = "Alongamentos",
            type = "D",
            completedDates = listOf(
                dateToMillis(twoDaysAgo),
                dateToMillis(twoDaysAgo.minusDays(1))
            )
        )

        // Quebrou porque ontem e hoje não foi feita
        assertEquals(0, streak.calculateCurrentStreak())
        assertFalse(streak.isCompletedToday())
    }

    @Test
    fun dailyStreak_duplicateDatesOnSameDay_deduplicatesCorrectly() {
        val today = LocalDate.now()
        val todayMillis = dateToMillis(today)
        val streak = Streak(
            name = "Beber Água",
            type = "D",
            completedDates = listOf(todayMillis, todayMillis, todayMillis)
        )

        assertEquals(1, streak.calculateCurrentStreak())
        assertTrue(streak.isCompletedToday())
    }

    @Test
    fun weeklyStreak_completedThisWeek_countsCorrectly() {
        val today = LocalDate.now()
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val lastMonday = thisMonday.minusWeeks(1)

        val streak = Streak(
            name = "Limpar a Casa",
            type = "S",
            completedDates = listOf(
                dateToMillis(thisMonday),
                dateToMillis(lastMonday)
            )
        )

        assertEquals(2, streak.calculateCurrentStreak())
        assertTrue(streak.isCompletedToday())
    }

    @Test
    fun weeklyStreak_brokenTwoWeeksAgo_counts0() {
        val today = LocalDate.now()
        val twoWeeksAgo = today.with(DayOfWeek.MONDAY).minusWeeks(2)

        val streak = Streak(
            name = "Corrida Longa",
            type = "S",
            completedDates = listOf(dateToMillis(twoWeeksAgo))
        )

        assertEquals(0, streak.calculateCurrentStreak())
        assertFalse(streak.isCompletedToday())
    }

    @Test
    fun monthlyStreak_completedThisMonth_countsCorrectly() {
        val today = LocalDate.now()
        val thisMonth = today.withDayOfMonth(1)
        val lastMonth = thisMonth.minusMonths(1)

        val streak = Streak(
            name = "Orçamento Mensal",
            type = "M",
            completedDates = listOf(
                dateToMillis(thisMonth),
                dateToMillis(lastMonth)
            )
        )

        assertEquals(2, streak.calculateCurrentStreak())
        assertTrue(streak.isCompletedToday())
    }

    @Test
    fun monthlyStreak_brokenTwoMonthsAgo_counts0() {
        val today = LocalDate.now()
        val twoMonthsAgo = today.withDayOfMonth(1).minusMonths(2)

        val streak = Streak(
            name = "Revisão do Carro",
            type = "M",
            completedDates = listOf(dateToMillis(twoMonthsAgo))
        )

        assertEquals(0, streak.calculateCurrentStreak())
        assertFalse(streak.isCompletedToday())
    }

    @Test
    fun toDynamicStreak_synchronizesCountAndStatus() {
        val today = LocalDate.now()
        val dates = listOf(dateToMillis(today), dateToMillis(today.minusDays(1)))

        val streak = Streak(
            name = "Estudo",
            type = "D",
            count = 0, // stale count
            isCompleted = false, // stale status
            completedDates = dates
        )

        val dynamic = streak.toDynamicStreak()
        assertEquals(2, dynamic.count)
        assertTrue(dynamic.isCompleted)
    }
}
