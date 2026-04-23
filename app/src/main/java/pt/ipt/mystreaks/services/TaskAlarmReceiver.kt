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
import pt.ipt.mystreaks.data.AppDatabase

class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. AÇÃO DE REBOOT (Requer proteção contra "morte" da app)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync() // Protege o processo
            val database = AppDatabase.getDatabase(context)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tasks = database.taskDao().getAllTasksSync()
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    for (task in tasks) {
                        val dueDateLocal = task.dueDate
                        if (!task.isCompleted && !task.isArchived && dueDateLocal != null && dueDateLocal > System.currentTimeMillis()) {
                            val alarmIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
                                putExtra("TASK_NAME", task.name)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context, task.name.hashCode(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateLocal, pendingIntent)
                                } else {
                                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, dueDateLocal, pendingIntent)
                                }
                            } catch (e: SecurityException) {
                                alarmManager.set(AlarmManager.RTC_WAKEUP, dueDateLocal, pendingIntent)
                            }
                        }
                    }
                } finally {
                    pendingResult.finish() // Libertar memória
                }
            }
            return
        }

        // 2. AÇÃO NORMAL DE NOTIFICAR (Síncrono, não precisa de goAsync)
        val taskName = intent.getStringExtra("TASK_NAME") ?: "Tens uma tarefa pendente!"
        showNotification(context, taskName)
    }

    private fun showNotification(context: Context, taskName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tasks_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Lembretes de Tarefas", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prazo a Terminar! ⏳")
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(taskName.hashCode(), notification)
    }
}