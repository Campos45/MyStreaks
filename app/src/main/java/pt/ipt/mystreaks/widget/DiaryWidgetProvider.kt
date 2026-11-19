package pt.ipt.mystreaks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.ui.main.MainActivity
import pt.ipt.mystreaks.utils.DateHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DiaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val today = LocalDate.now()
                val startMillis = DateHelper.startOfDayMillis(today)
                val endMillis = DateHelper.endOfDayMillis(today)

                val entry = db.diaryDao().getEntryByDate(startMillis)
                val pendingTasks = db.taskDao().getPendingTasksByDateRangeSync(startMillis, endMillis)

                val locale = Locale.getDefault()
                val dateFmt = DateTimeFormatter.ofPattern("EEE, dd MMM", locale)
                val dateText = "📖 ${today.format(dateFmt).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }}"

                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_DIARY)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    DIARY_WIDGET_REQ_CODE,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_diary)
                    views.setTextViewText(R.id.tvWidgetDiaryDate, dateText)

                    // Notas do Dia
                    val noteContent = entry?.content?.trim()
                    if (!noteContent.isNullOrBlank()) {
                        views.setTextViewText(R.id.tvWidgetDiaryNotes, noteContent)
                    } else {
                        views.setTextViewText(R.id.tvWidgetDiaryNotes, context.getString(R.string.widget_diary_no_notes))
                    }

                    // Tarefas de Hoje
                    val taskCount = pendingTasks.size
                    if (taskCount > 0) {
                        views.setViewVisibility(R.id.tvWidgetDiaryTasksBadge, View.VISIBLE)
                        views.setTextViewText(R.id.tvWidgetDiaryTasksBadge, taskCount.toString())
                        val preview = pendingTasks.take(2).joinToString(", ") { it.name }
                        val summary = context.getString(R.string.widget_diary_tasks_count, taskCount) + ": $preview"
                        views.setTextViewText(R.id.tvWidgetDiaryTasksStatus, summary)
                    } else {
                        views.setViewVisibility(R.id.tvWidgetDiaryTasksBadge, View.GONE)
                        views.setTextViewText(R.id.tvWidgetDiaryTasksStatus, context.getString(R.string.widget_diary_no_tasks))
                    }

                    // Clicar no widget abre a tab Diário da app
                    views.setOnClickPendingIntent(R.id.widgetDiaryRoot, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widgetDiaryHeader, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widgetDiaryBody, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val DIARY_WIDGET_REQ_CODE = 8001

        fun triggerUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DiaryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, DiaryWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
