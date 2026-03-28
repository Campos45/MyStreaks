package pt.ipt.mystreaks.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.AppLog
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.StreakRecord
import java.util.Calendar

class StreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.Companion.getDatabase(applicationContext)
        val dao = database.streakDao()
        val logDao = database.appLogDao()

        val streaks = dao.getActiveStreaksList()
        val updatedStreaks = mutableListOf<Streak>()
        val now = Calendar.getInstance()

        // Calcular a meia-noite de hoje (para saber se já foi feita hoje)
        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
            Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (streak in streaks) {
            var streakBroken = false

            // Vamos avaliar se a pessoa falhou o objetivo
            when (streak.type) {
                "D" -> { // DIÁRIA: Tinha de ter sido feita ontem
                    val yesterday = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(
                        Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (!streak.completedDates.contains(yesterday) && streak.count > 0) {
                        streakBroken = true
                    }
                }
                "S" -> { // SEMANAL: Hoje é Segunda-feira? Se sim, vamos ver se foi feita na semana passada
                    if (now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                        val lastWeek = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -1) }
                        val wasDoneLastWeek = streak.completedDates.any { date ->
                            val c = Calendar.getInstance().apply { timeInMillis = date }
                            c.get(Calendar.WEEK_OF_YEAR) == lastWeek.get(Calendar.WEEK_OF_YEAR) && c.get(
                                Calendar.YEAR) == lastWeek.get(Calendar.YEAR)
                        }
                        if (!wasDoneLastWeek && streak.count > 0) {
                            streakBroken = true
                        }
                    }
                }
                "M" -> { // MENSAL: Hoje é dia 1? Se sim, vamos ver se foi feita no mês passado
                    if (now.get(Calendar.DAY_OF_MONTH) == 1) {
                        val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                        val wasDoneLastMonth = streak.completedDates.any { date ->
                            val c = Calendar.getInstance().apply { timeInMillis = date }
                            c.get(Calendar.MONTH) == lastMonth.get(Calendar.MONTH) && c.get(Calendar.YEAR) == lastMonth.get(
                                Calendar.YEAR)
                        }
                        if (!wasDoneLastMonth && streak.count > 0) {
                            streakBroken = true
                        }
                    }
                }
            }

            var newCount = streak.count
            var newHistory = streak.history
            var newCurrentStartDate = streak.currentStartDate

            if (streakBroken) {
                newCount = 0 // Corta o fogo!
                if (streak.currentStartDate != null) {
                    val record = StreakRecord(
                        streak.count,
                        streak.currentStartDate!!,
                        System.currentTimeMillis()
                    )
                    newHistory = newHistory + record
                }
                newCurrentStartDate = null
                logDao.insertLog(
                    AppLog(
                        type = "STREAK_QUEBRADA",
                        message = "A atividade '${streak.name}' quebrou!"
                    )
                )
            }

            // O SEGREDO ESTÁ AQUI: A checkbox passa a reflectir apenas se a tarefa foi feita HOJE
            val isActuallyCompletedToday = streak.completedDates.contains(todayMidnight)

            // Atualiza sempre a streak na base de dados para garantir que a UI fica certa
            updatedStreaks.add(
                streak.copy(
                    count = newCount,
                    isCompleted = isActuallyCompletedToday, // Vai forçar a checkbox a desligar se hoje for um dia novo
                    history = newHistory,
                    currentStartDate = newCurrentStartDate
                )
            )
        }

        if (updatedStreaks.isNotEmpty()) {
            dao.updateAll(updatedStreaks)
        }

        return Result.success()
    }
}