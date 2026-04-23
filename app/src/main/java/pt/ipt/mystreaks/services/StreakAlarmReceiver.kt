package pt.ipt.mystreaks.services

import android.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.ui.main.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class StreakAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val streakName = intent.getStringExtra("STREAK_NAME") ?: return
        val pendingResult = goAsync() // Segura o processo para não morrer

        val database = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val streaks = database.streakDao().getAllStreaksSync()
                val streak = streaks.find { it.name == streakName }

                if (streak != null && !streak.isArchived) {
                    val todayMidnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val isCompletedToday = streak.completedDates.contains(todayMidnight)

                    if (!isCompletedToday) {
                        sendNotification(context, streak)
                    }

                    // OBRIGATÓRIO: Marca o alarme exato para o dia de amanhã!
                    scheduleNextDay(context, streak)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleNextDay(context: Context, streak: Streak) {
        if (streak.remindHour == null || streak.remindMinute == null) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextIntent = Intent(context, StreakAlarmReceiver::class.java).apply {
            putExtra("STREAK_NAME", streak.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, streak.name.hashCode(), nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calcula a mesma hora para o dia seguinte
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, streak.remindHour!!)
            set(Calendar.MINUTE, streak.remindMinute!!)
            set(Calendar.SECOND, 0)
            add(Calendar.DATE, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }

    private fun sendNotification(context: Context, streak: Streak) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "custom_streak_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Lembretes", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, streak.id, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("🔥 ${streak.name}")
            .setContentText("Ainda não concluíste a tua atividade! Não quebres a streak!")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val uniqueId = streak.id * 100 + (System.currentTimeMillis() % 100).toInt()
        notificationManager.notify(uniqueId, notification)
    }
}