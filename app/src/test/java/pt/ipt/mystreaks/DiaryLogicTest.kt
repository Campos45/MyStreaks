package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.data.model.DiaryEntry
import pt.ipt.mystreaks.utils.DateHelper
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DiaryLogicTest {

    @Test
    fun startAndEndOfDayMillis_coverExactFullDay() {
        val date = LocalDate.of(2026, 11, 17)
        val start = DateHelper.startOfDayMillis(date)
        val end = DateHelper.endOfDayMillis(date)
        val nextDayStart = DateHelper.startOfDayMillis(date.plusDays(1))

        assertTrue(start < end)
        assertEquals(nextDayStart, end + 1L)
    }

    @Test
    fun taskDueDateWithinDayRange_isMatchedCorrectly() {
        val date = LocalDate.of(2026, 11, 17)
        val start = DateHelper.startOfDayMillis(date)
        val end = DateHelper.endOfDayMillis(date)

        val noonMillis = date.atTime(12, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevDayLate = date.minusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nextDayEarly = date.plusDays(1).atTime(0, 0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertTrue(noonMillis in start..end)
        assertFalse(prevDayLate in start..end)
        assertFalse(nextDayEarly in start..end)
    }

    @Test
    fun diaryEntry_preservesDateAndContent() {
        val dayMillis = DateHelper.startOfDayMillis(LocalDate.of(2026, 11, 17))
        val entry = DiaryEntry(dateMillis = dayMillis, content = "Reunião importante às 10h")
        assertEquals(dayMillis, entry.dateMillis)
        assertEquals("Reunião importante às 10h", entry.content)
        assertTrue(entry.updatedAt > 0L)
    }

    @Test
    fun calendarLeadingBlanks_calculatedCorrectlyForDifferentMonths() {
        // November 2026 starts on Sunday (dayOfWeek.value == 7 -> 6 leading blanks in Mon..Sun grid)
        val nov2026 = YearMonth.of(2026, 11)
        assertEquals(6, nov2026.atDay(1).dayOfWeek.value - 1)
        assertEquals(30, nov2026.lengthOfMonth())

        // June 2026 starts on Monday (dayOfWeek.value == 1 -> 0 leading blanks)
        val jun2026 = YearMonth.of(2026, 6)
        assertEquals(0, jun2026.atDay(1).dayOfWeek.value - 1)

        // February 2028 is a leap year (29 days)
        val feb2028 = YearMonth.of(2028, 2)
        assertEquals(29, feb2028.lengthOfMonth())
    }
}
