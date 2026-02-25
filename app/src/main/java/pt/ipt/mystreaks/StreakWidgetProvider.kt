package pt.ipt.mystreaks

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

import pt.ipt.mystreaks.R
class StreakWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_streak)

            // Ligar a Lista ao nosso Service
            val intent = Intent(context, StreakWidgetService::class.java)
            views.setRemoteAdapter(R.id.widgetListView, intent)
            views.setEmptyView(R.id.widgetListView, R.id.widgetEmptyView)

            // Criar a ação de abrir a MainActivity ao clicar
            val mainIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Se clicar no título ou num item da lista, abre a app
            views.setOnClickPendingIntent(R.id.widgetHeader, pendingIntent)
            views.setPendingIntentTemplate(R.id.widgetListView, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}