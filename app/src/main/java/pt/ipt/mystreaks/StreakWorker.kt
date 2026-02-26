package pt.ipt.mystreaks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar

class StreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.streakDao()
        val logDao = database.appLogDao()

        val streaks = dao.getActiveStreaksList()
        var needsUpdate = false
        val updatedStreaks = mutableListOf<Streak>()
        val now = Calendar.getInstance()

        for (streak in streaks) {
            val lastReset = Calendar.getInstance().apply { timeInMillis = streak.lastResetDate }
            var hasResetOccurred = false

            when (streak.type) {
                "D" -> {
                    if (now.get(Calendar.DAY_OF_YEAR) != lastReset.get(Calendar.DAY_OF_YEAR) || now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {
                        hasResetOccurred = true
                    } else if (!streak.isCompleted && now.get(Calendar.HOUR_OF_DAY) >= 20) {
                        // Envia notificação individual de fim de dia!
                        sendIndividualNotification(streak, "O dia está a acabar! Não percas a tua streak diária!")
                    }
                }
                "S" -> {
                    if (now.get(Calendar.WEEK_OF_YEAR) != lastReset.get(Calendar.WEEK_OF_YEAR) || now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {
                        hasResetOccurred = true
                    } else if (!streak.isCompleted && now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                        sendIndividualNotification(streak, "A semana termina hoje! Conclui a tua tarefa!")
                    }
                }
                "M" -> {
                    if (now.get(Calendar.MONTH) != lastReset.get(Calendar.MONTH) || now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {
                        hasResetOccurred = true
                    } else {
                        val lastDayOfMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                        if (!streak.isCompleted && (lastDayOfMonth - now.get(Calendar.DAY_OF_MONTH)) <= 5) {
                            sendIndividualNotification(streak, "O mês está a terminar! Não deixes a streak cair!")
                        }
                    }
                }
            }

            if (hasResetOccurred) {
                val newCount = if (streak.isCompleted) streak.count else 0
                var newHistory = streak.history
                var newCurrentStartDate = streak.currentStartDate

                if (!streak.isCompleted && streak.count > 0 && streak.currentStartDate != null) {
                    val record = StreakRecord(streak.count, streak.currentStartDate!!, System.currentTimeMillis())
                    newHistory = newHistory + record
                    newCurrentStartDate = null
                    logDao.insertLog(AppLog(type = "STREAK_QUEBRADA", message = "A atividade '${streak.name}' quebrou!"))
                } else if (!streak.isCompleted) {
                    newCurrentStartDate = null
                }

                updatedStreaks.add(streak.copy(
                    count = newCount, isCompleted = false, lastResetDate = System.currentTimeMillis(),
                    history = newHistory, currentStartDate = newCurrentStartDate
                ))
                needsUpdate = true
            }
        }

        if (needsUpdate && updatedStreaks.isNotEmpty()) {
            dao.updateAll(updatedStreaks)
        }
        return Result.success()
    }

    private fun sendIndividualNotification(streak: Streak, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "end_of_period_notifications"
        val channel = NotificationChannel(channelId, "Avisos de Fim de Prazo", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, streak.id + 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("⏳ ${streak.name}")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(streak.id + 1000, notification)
    }
}