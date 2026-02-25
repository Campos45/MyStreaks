package pt.ipt.mystreaks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar

class StreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.streakDao()
        val logDao = database.appLogDao() // Para podermos fazer logs no Worker

        val streaks = dao.getActiveStreaksList()
        var needsUpdate = false
        val updatedStreaks = mutableListOf<Streak>()

        var sendDailyNotification = false
        var sendWeeklyNotification = false
        var sendMonthlyNotification = false

        val now = Calendar.getInstance()

        for (streak in streaks) {
            val lastReset = Calendar.getInstance().apply { timeInMillis = streak.lastResetDate }
            var hasResetOccurred = false

            when (streak.type) {
                "D" -> {
                    if (now.get(Calendar.DAY_OF_YEAR) != lastReset.get(Calendar.DAY_OF_YEAR) || now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {
                        hasResetOccurred = true
                    } else if (!streak.isCompleted && now.get(Calendar.HOUR_OF_DAY) >= 20) {
                        sendDailyNotification = true
                    }
                }
                "S" -> {
                    if (now.get(Calendar.WEEK_OF_YEAR) != lastReset.get(Calendar.WEEK_OF_YEAR) || now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {
                        hasResetOccurred = true
                    } else if (!streak.isCompleted && now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                        sendWeeklyNotification = true
                    }
                }
                "M" -> {
                    if (now.get(Calendar.MONTH) != lastReset.get(Calendar.MONTH) || now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {
                        hasResetOccurred = true
                    } else {
                        val lastDayOfMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                        if (!streak.isCompleted && (lastDayOfMonth - now.get(Calendar.DAY_OF_MONTH)) <= 5) {
                            sendMonthlyNotification = true
                        }
                    }
                }
            }

            if (hasResetOccurred) {
                val newCount = if (streak.isCompleted) streak.count else 0

                var newHistory = streak.history
                var newCurrentStartDate = streak.currentStartDate

                // SE FALHOU E A STREAK QUEBROU: Grava no histórico!
                if (!streak.isCompleted && streak.count > 0 && streak.currentStartDate != null) {
                    val record = StreakRecord(
                        count = streak.count,
                        startDate = streak.currentStartDate!!,
                        endDate = System.currentTimeMillis()
                    )
                    newHistory = newHistory + record
                    newCurrentStartDate = null // Reinicia a data porque quebrou

                    logDao.insertLog(AppLog(type = "STREAK_QUEBRADA", message = "A atividade '${streak.name}' quebrou a sequência de ${streak.count}!"))
                } else if (!streak.isCompleted) {
                    newCurrentStartDate = null
                }

                updatedStreaks.add(streak.copy(
                    count = newCount,
                    isCompleted = false,
                    lastResetDate = System.currentTimeMillis(),
                    history = newHistory,
                    currentStartDate = newCurrentStartDate
                ))
                needsUpdate = true
            }
        }

        if (needsUpdate && updatedStreaks.isNotEmpty()) {
            dao.updateAll(updatedStreaks)
        }

        if (sendDailyNotification) sendNotification("Atividades Diárias", "Faltam menos de 4 horas! Não percas a tua streak!", 1)
        if (sendWeeklyNotification) sendNotification("Atividades Semanais", "A semana está a terminar. Já fizeste as tarefas?", 2)
        if (sendMonthlyNotification) sendNotification("Atividades Mensais", "O mês está no fim. Mantém as tuas streaks vivas!", 3)

        return Result.success()
    }

    private fun sendNotification(title: String, message: String, notificationId: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "streak_notifications"
        val channel = NotificationChannel(channelId, "Streaks", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(notificationId, notification)
    }
}