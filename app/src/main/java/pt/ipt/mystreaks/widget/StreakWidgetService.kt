package pt.ipt.mystreaks.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.Streak
import java.time.LocalDate
import java.time.ZoneId

class StreakWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return StreakWidgetFactory(this.applicationContext)
    }
}

class StreakWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var streaks: List<Streak> = emptyList()
    private val dao = AppDatabase.getDatabase(context).streakDao()

    override fun onCreate() {}

    // O Android chama isto para atualizar a lista do widget
    override fun onDataSetChanged() {
        // Corre em modo "bloqueio" rápido porque os widgets precisam da resposta imediata
        runBlocking {
            streaks = dao.getActiveStreaksList().map { it.toDynamicStreak() }
        }
    }

    override fun onDestroy() { streaks = emptyList() }
    override fun getCount(): Int = streaks.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = streaks[position].id.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val streak = streaks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_streak_item)

        views.setTextViewText(R.id.tvWidgetStreakName, streak.name)

        // --- O UPGRADE: CALCULAR SE FOI FEITO HOJE ---
        // Pegamos na meia-noite exata do dia atual
        val todayMidnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Verificamos se essa meia-noite consta no histórico de dias concluídos desta Streak
        val isCompletedToday = streak.completedDates.contains(todayMidnight)

        // Desenha o ícone correto com base na realidade de hoje!
        val icon = if (isCompletedToday) "✅" else "🔥"
        // ---------------------------------------------

        views.setTextViewText(R.id.tvWidgetStreakCount, "$icon ${streak.count}")

        // Diz que se clicarmos neste item, ele deve disparar o Intent que abre a App
        views.setOnClickFillInIntent(R.id.widgetItemContainer, Intent())
        return views
    }
}