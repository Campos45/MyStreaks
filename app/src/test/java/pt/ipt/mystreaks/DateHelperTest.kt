package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.utils.DateHelper
import java.time.LocalDate
import java.time.ZoneId

class DateHelperTest {

    private fun dateToMillis(localDate: LocalDate): Long {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun formatRelative_today_returnsHoje() {
        val today = LocalDate.now()
        val formatted = DateHelper.formatRelative(dateToMillis(today))
        assertEquals("Hoje", formatted)
        assertTrue(DateHelper.isToday(dateToMillis(today)))
    }

    @Test
    fun formatRelative_tomorrow_returnsAmanha() {
        val tomorrow = LocalDate.now().plusDays(1)
        val formatted = DateHelper.formatRelative(dateToMillis(tomorrow))
        assertEquals("Amanhã", formatted)
        assertFalse(DateHelper.isToday(dateToMillis(tomorrow)))
    }

    @Test
    fun formatRelative_yesterday_returnsOntem() {
        val yesterday = LocalDate.now().minusDays(1)
        val formatted = DateHelper.formatRelative(dateToMillis(yesterday))
        assertEquals("Ontem", formatted)
    }

    @Test
    fun formatRelative_futureDays_returnsEmXDias() {
        val in3Days = LocalDate.now().plusDays(3)
        val formatted = DateHelper.formatRelative(dateToMillis(in3Days))
        assertEquals("Em 3 dias", formatted)
    }

    @Test
    fun formatRelative_pastDays_returnsHaXDias() {
        val threeDaysAgo = LocalDate.now().minusDays(3)
        val formatted = DateHelper.formatRelative(dateToMillis(threeDaysAgo))
        assertEquals("Há 3 dias", formatted)
    }
}
