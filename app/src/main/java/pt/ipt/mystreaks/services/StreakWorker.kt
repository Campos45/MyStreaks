package pt.ipt.mystreaks.services

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.AppLog
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.StreakRecord

class StreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.streakDao()
        val logDao = database.appLogDao()

        val streaks = dao.getActiveStreaksList()
        val updatedStreaks = mutableListOf<Streak>()

        for (streak in streaks) {
            val dynamicStreak = streak.toDynamicStreak()
            val hadActiveStreak = streak.count > 0
            val isNowBroken = hadActiveStreak && dynamicStreak.count == 0

            var newHistory = streak.history
            var newCurrentStartDate = streak.currentStartDate

            if (isNowBroken) {
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

            updatedStreaks.add(
                streak.copy(
                    count = dynamicStreak.count,
                    isCompleted = dynamicStreak.isCompleted,
                    history = newHistory,
                    currentStartDate = newCurrentStartDate
                )
            )
        }

        if (updatedStreaks.isNotEmpty()) {
            dao.updateAll(updatedStreaks)
        }

        // Notificar o Widget para atualizar na Home Screen
        applicationContext.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            setPackage(applicationContext.packageName)
        })

        return Result.success()
    }
}
