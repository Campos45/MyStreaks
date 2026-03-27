package pt.ipt.mystreaks

import java.util.Calendar

object DateHelper {
    // Configura o calendário para a nossa zona (Semana começa à Segunda-feira)
    private fun getCalendar(time: Long): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = time
            firstDayOfWeek = Calendar.MONDAY
        }
    }

    fun isSameDay(date1: Long, date2: Long): Boolean {
        if (date1 == 0L || date2 == 0L) return false
        val c1 = getCalendar(date1)
        val c2 = getCalendar(date2)
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun isSameWeek(date1: Long, date2: Long): Boolean {
        if (date1 == 0L || date2 == 0L) return false
        val c1 = getCalendar(date1)
        val c2 = getCalendar(date2)
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.WEEK_OF_YEAR) == c2.get(Calendar.WEEK_OF_YEAR)
    }

    fun isSameMonth(date1: Long, date2: Long): Boolean {
        if (date1 == 0L || date2 == 0L) return false
        val c1 = getCalendar(date1)
        val c2 = getCalendar(date2)
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
    }

    // A Matemática Implacável que diz se perdeste o hábito
    fun isStreakBroken(frequency: String, lastInc: Long, now: Long): Boolean {
        if (lastInc == 0L) return false // Se nunca começou, não pode quebrar

        val cLast = getCalendar(lastInc)
        val cNow = getCalendar(now)

        return when (frequency) {
            "Diário" -> {
                // Remove as horas para comparar apenas os dias redondos
                cLast.set(Calendar.HOUR_OF_DAY, 0); cLast.set(Calendar.MINUTE, 0); cLast.set(Calendar.SECOND, 0); cLast.set(Calendar.MILLISECOND, 0)
                cNow.set(Calendar.HOUR_OF_DAY, 0); cNow.set(Calendar.MINUTE, 0); cNow.set(Calendar.SECOND, 0); cNow.set(Calendar.MILLISECOND, 0)
                val diffDays = (cNow.timeInMillis - cLast.timeInMillis) / (1000 * 60 * 60 * 24)
                diffDays > 1 // Se passou mais de 1 dia desde a meia-noite anterior = Quebrou
            }
            "Semanal" -> {
                val yearDiff = cNow.get(Calendar.YEAR) - cLast.get(Calendar.YEAR)
                val weekDiff = cNow.get(Calendar.WEEK_OF_YEAR) - cLast.get(Calendar.WEEK_OF_YEAR)
                val totalWeeksDiff = (yearDiff * 52) + weekDiff
                totalWeeksDiff > 1
            }
            "Mensal" -> {
                val yearDiff = cNow.get(Calendar.YEAR) - cLast.get(Calendar.YEAR)
                val monthDiff = cNow.get(Calendar.MONTH) - cLast.get(Calendar.MONTH)
                val totalMonthsDiff = (yearDiff * 12) + monthDiff
                totalMonthsDiff > 1
            }
            else -> false
        }
    }
}