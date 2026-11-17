package pt.ipt.mystreaks.services

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
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.ui.main.MainActivity
import pt.ipt.mystreaks.utils.AppPreferences
import pt.ipt.mystreaks.utils.DateHelper
import java.time.LocalDate
import java.util.Calendar

class DiaryAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            scheduleDailyReminder(context)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val today = LocalDate.now()
                val startMillis = DateHelper.startOfDayMillis(today)
                val endMillis = DateHelper.endOfDayMillis(today)

                val todayTasks = db.taskDao().getPendingTasksByDateRangeSync(startMillis, endMillis)
                val todayEntry = db.diaryDao().getEntryByDate(startMillis)
                val hasNote = todayEntry != null && todayEntry.content.isNotBlank()

                if (todayTasks.isNotEmpty() || hasNote) {
                    val bodyParts = mutableListOf<String>()
                    if (todayTasks.isNotEmpty()) {
                        bodyParts.add(context.getString(R.string.diary_notification_tasks, todayTasks.size))
                        val previewNames = todayTasks.take(3).joinToString(", ") { it.name }
                        bodyParts.add(previewNames)
                    }
                    if (hasNote) {
                        bodyParts.add(context.getString(R.string.diary_notification_note))
                    }
                    showNotification(context, bodyParts.joinToString("\n"))
                }
            } finally {
                // Schedule next day's reminder
                scheduleDailyReminder(context)
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "diary_daily_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.nav_diary),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_DIARY)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            DIARY_ALARM_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_diary)
            .setContentTitle(context.getString(R.string.diary_notification_title))
            .setContentText(body.lines().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(DIARY_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val DIARY_ALARM_REQUEST_CODE = 9001
        private const val DIARY_NOTIFICATION_ID = 9002

        fun scheduleDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DiaryAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DIARY_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val hour = AppPreferences.getDiaryReminderHour(context)
            val minute = AppPreferences.getDiaryReminderMinute(context)

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
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
    }
}
