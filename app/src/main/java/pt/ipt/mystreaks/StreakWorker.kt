package pt.ipt.mystreaks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar

class StreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.streakDao()
        val logDao = database.appLogDao()

        val streaks = dao.getActiveStreaksList()
        val updatedStreaks = mutableListOf<Streak>()
        val now = Calendar.getInstance()

        // Calcular a meia-noite de hoje para saber se a checkbox deve estar marcada
        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (streak in streaks) {
            val lastCheckCal = Calendar.getInstance().apply { timeInMillis = streak.lastResetDate }
            var streakBroken = false
            var newLastResetDate = streak.lastResetDate

            when (streak.type) {
                "D" -> { // DIÁRIA
                    // Se mudámos de dia desde a última verificação
                    if (now.get(Calendar.DAY_OF_YEAR) != lastCheckCal.get(Calendar.DAY_OF_YEAR) || now.get(Calendar.YEAR) != lastCheckCal.get(Calendar.YEAR)) {
                        // Verifica se foi feita ONTEM
                        val yesterday = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        if (!streak.completedDates.contains(yesterday)) {
                            streakBroken = true
                        }
                        newLastResetDate = System.currentTimeMillis()
                    }
                }
                "S" -> { // SEMANAL
                    // Se mudámos de semana desde a última verificação
                    if (now.get(Calendar.WEEK_OF_YEAR) != lastCheckCal.get(Calendar.WEEK_OF_YEAR) || now.get(Calendar.YEAR) != lastCheckCal.get(Calendar.YEAR)) {
                        // Verifica se foi feita na SEMANA PASSADA
                        val lastWeek = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -1) }
                        val wasDoneLastWeek = streak.completedDates.any { date ->
                            val c = Calendar.getInstance().apply { timeInMillis = date }
                            c.get(Calendar.WEEK_OF_YEAR) == lastWeek.get(Calendar.WEEK_OF_YEAR) && c.get(Calendar.YEAR) == lastWeek.get(Calendar.YEAR)
                        }

                        if (!wasDoneLastWeek) {
                            streakBroken = true
                        }
                        newLastResetDate = System.currentTimeMillis()
                    }
                }
                "M" -> { // MENSAL
                    // Se mudámos de mês desde a última verificação
                    if (now.get(Calendar.MONTH) != lastCheckCal.get(Calendar.MONTH) || now.get(Calendar.YEAR) != lastCheckCal.get(Calendar.YEAR)) {
                        // Verifica se foi feita no MÊS PASSADO
                        val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                        val wasDoneLastMonth = streak.completedDates.any { date ->
                            val c = Calendar.getInstance().apply { timeInMillis = date }
                            c.get(Calendar.MONTH) == lastMonth.get(Calendar.MONTH) && c.get(Calendar.YEAR) == lastMonth.get(Calendar.YEAR)
                        }

                        if (!wasDoneLastMonth) {
                            streakBroken = true
                        }
                        newLastResetDate = System.currentTimeMillis()
                    }
                }
            }

            // O estado REAL da checkbox (só está true se tiver sido feita HOJE)
            val isActuallyCompletedToday = streak.completedDates.contains(todayMidnight)

            // Aplicar o castigo se a streak quebrou e ainda tínhamos pontos
            var newCount = streak.count
            var newHistory = streak.history
            var newCurrentStartDate = streak.currentStartDate

            if (streakBroken && streak.count > 0) {
                newCount = 0 // Fogo a zero!
                if (streak.currentStartDate != null) {
                    val record = StreakRecord(streak.count, streak.currentStartDate!!, System.currentTimeMillis())
                    newHistory = newHistory + record // Guarda o recorde para o histórico
                }
                newCurrentStartDate = null
                logDao.insertLog(AppLog(type = "STREAK_QUEBRADA", message = "A atividade '${streak.name}' quebrou!"))
            } else if (streakBroken) {
                newCurrentStartDate = null // Garante que não inicia uma contagem fantasma
            }

            // Se houve QUALQUER alteração (seja no fogo, na checkbox ou na data de verificação), atualiza na Base de Dados
            if (streak.count != newCount || streak.isCompleted != isActuallyCompletedToday || streak.lastResetDate != newLastResetDate) {
                updatedStreaks.add(
                    streak.copy(
                        count = newCount,
                        isCompleted = isActuallyCompletedToday,
                        lastResetDate = newLastResetDate,
                        history = newHistory,
                        currentStartDate = newCurrentStartDate
                    )
                )
            }
        }

        if (updatedStreaks.isNotEmpty()) {
            dao.updateAll(updatedStreaks)
        }

        return Result.success()
    }
}