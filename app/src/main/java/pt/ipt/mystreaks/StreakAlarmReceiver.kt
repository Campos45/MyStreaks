package pt.ipt.mystreaks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class StreakAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val streakId = intent.getIntExtra("STREAK_ID", -1)
        if (streakId == -1) return

        val database = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            val streak = database.streakDao().getStreakById(streakId)

            if (streak != null && !streak.isArchived) {
                // Verifica se foi concluída HOJE (Meia-noite de hoje)
                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val isCompletedToday = streak.completedDates.contains(todayCal.timeInMillis)

                // Só notifica se NÃO tiver sido concluída hoje!
                if (!isCompletedToday) {
                    sendNotification(context, streak)
                }
            }
        }
    }

    private fun sendNotification(context: Context, streak: Streak) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "custom_streak_notifications"

        val channel = NotificationChannel(channelId, "Lembretes", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        // Torna a notificação clicável para abrir a app!
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, streak.id, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("🔥 ${streak.name}")
            .setContentText("Ainda não concluíste a tua atividade! Não quebres a streak!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // Liga o clique!
            .setAutoCancel(true) // Desaparece quando clicas
            .build()

        // ID único (streak.id + número aleatório) para receberes as notificações às 19h e 23h sem as antigas desaparecerem
        val uniqueId = streak.id * 100 + (System.currentTimeMillis() % 100).toInt()
        notificationManager.notify(uniqueId, notification)
    }
}