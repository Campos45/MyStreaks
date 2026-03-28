package pt.ipt.mystreaks.utils

import java.util.Calendar

object DateHelper {
    // Configura o calendário para a nossa zona (Semana começa à Segunda-feira)
    private fun getCalendar(time: Long): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = time
            firstDayOfWeek = Calendar.MONDAY
        }
    }

}