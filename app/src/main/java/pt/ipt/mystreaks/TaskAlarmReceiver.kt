package pt.ipt.mystreaks

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra("TASK_NAME") ?: "Tarefa pendente"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Constrói o cartão da Notificação que vai aparecer no telemóvel
        val notification = NotificationCompat.Builder(context, "mystreaks_channel")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("O prazo termina hoje! ⏳")
            .setContentText("Não te esqueças de concluir: $taskName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Usa um ID aleatório para poderes receber várias notificações de tarefas diferentes ao mesmo tempo
        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, notification)
    }
}