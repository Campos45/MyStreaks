package pt.ipt.mystreaks

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking

class StreakWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return StreakWidgetFactory(this.applicationContext)
    }
}

class StreakWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var streaks: List<Streak> = emptyList()
    private val dao = AppDatabase.getDatabase(context).streakDao()

    override fun onCreate() {}

    // O Android chama isto para atualizar a lista
    override fun onDataSetChanged() {
        // Corre em modo "bloqueio" rápido porque os widgets precisam da resposta imediata
        runBlocking {
            streaks = dao.getActiveStreaksList()
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
        val icon = if (streak.isCompleted) "✅" else "🔥"
        views.setTextViewText(R.id.tvWidgetStreakCount, "$icon ${streak.count}")

        // Diz que se clicarmos neste item, ele deve disparar o Intent que abre a App
        views.setOnClickFillInIntent(R.id.widgetItemContainer, Intent())
        return views
    }
}