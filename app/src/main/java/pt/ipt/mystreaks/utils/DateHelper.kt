package pt.ipt.mystreaks.utils

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

object DateHelper {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale("pt", "PT"))

    fun toLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun startOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        return toLocalDate(millis).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun startOfDayMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun endOfDayMillis(date: LocalDate): Long {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L
    }

    fun endOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        return endOfDayMillis(toLocalDate(millis))
    }

    fun formatDate(millis: Long): String {
        return toLocalDate(millis).format(dateFormatter)
    }

    fun formatDateTime(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    fun formatRelative(millis: Long): String {
        val targetDate = toLocalDate(millis)
        val today = LocalDate.now()
        val daysDiff = ChronoUnit.DAYS.between(today, targetDate)

        return when (daysDiff) {
            0L -> "Hoje"
            1L -> "Amanhã"
            -1L -> "Ontem"
            in 2L..6L -> "Em $daysDiff dias"
            in -6L..-2L -> "Há ${-daysDiff} dias"
            else -> targetDate.format(shortDateFormatter)
        }
    }

    fun isToday(millis: Long): Boolean {
        return toLocalDate(millis) == LocalDate.now()
    }
}
