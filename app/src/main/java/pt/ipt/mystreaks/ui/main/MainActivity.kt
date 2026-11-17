package pt.ipt.mystreaks.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.databinding.ActivityMainBinding
import pt.ipt.mystreaks.services.DiaryAlarmReceiver
import pt.ipt.mystreaks.services.StreakWorker
import pt.ipt.mystreaks.ui.diary.DiaryFragment
import pt.ipt.mystreaks.ui.lists.ListsFragment
import pt.ipt.mystreaks.ui.settings.SettingsFragment
import pt.ipt.mystreaks.ui.streak.StreaksFragment
import pt.ipt.mystreaks.ui.tasks.TasksFragment
import pt.ipt.mystreaks.utils.AppPreferences
import pt.ipt.mystreaks.utils.TooltipHelper
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar modo escuro e idioma guardados antes de inflar a UI
        AppPreferences.applyDarkMode(AppPreferences.isDarkMode(this))
        AppPreferences.applyLanguage(AppPreferences.getLanguage(this))

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Liga o Trabalhador de Fundo para verificar os Resets todos os dias à meia-noite
        val now = java.util.Calendar.getInstance()
        val nextMidnight = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 2)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val initialDelay = (nextMidnight.timeInMillis - now.timeInMillis).coerceAtLeast(0L)

        val streakWorkRequest = PeriodicWorkRequestBuilder<StreakWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StreakResetWork",
            ExistingPeriodicWorkPolicy.KEEP,
            streakWorkRequest
        )

        // Agendar lembrete diário do Diário
        DiaryAlarmReceiver.scheduleDailyReminder(this)

        // Configurar ViewPager2 para swipe horizontal entre tabs
        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = when (position) {
                    TAB_STREAKS -> R.id.nav_streaks
                    TAB_TASKS -> R.id.nav_tasks
                    TAB_DIARY -> R.id.nav_diary
                    TAB_LISTS -> R.id.nav_lists
                    TAB_SETTINGS -> R.id.nav_settings
                    else -> R.id.nav_streaks
                }
                if (binding.bottomNavigation.selectedItemId != itemId) {
                    binding.bottomNavigation.selectedItemId = itemId
                }
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val targetPos = when (item.itemId) {
                R.id.nav_streaks -> TAB_STREAKS
                R.id.nav_tasks -> TAB_TASKS
                R.id.nav_diary -> TAB_DIARY
                R.id.nav_lists -> TAB_LISTS
                R.id.nav_settings -> TAB_SETTINGS
                else -> TAB_STREAKS
            }
            if (binding.viewPager.currentItem != targetPos) {
                binding.viewPager.setCurrentItem(targetPos, true)
            }
            true
        }

        // Tooltips informativos nos botões da barra de navegação
        TooltipHelper.attachBottomNavTooltips(
            binding.bottomNavigation,
            mapOf(
                R.id.nav_streaks to R.string.tooltip_nav_streaks,
                R.id.nav_tasks to R.string.tooltip_nav_tasks,
                R.id.nav_diary to R.string.tooltip_nav_diary,
                R.id.nav_lists to R.string.tooltip_nav_lists,
                R.id.nav_settings to R.string.tooltip_nav_settings
            )
        )

        handleOpenTabIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenTabIntent(intent)
    }

    private fun handleOpenTabIntent(intent: Intent?) {
        val tab = intent?.getIntExtra(EXTRA_OPEN_TAB, -1) ?: -1
        if (tab in TAB_STREAKS..TAB_SETTINGS) {
            binding.viewPager.setCurrentItem(tab, false)
        }
    }

    private class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                TAB_STREAKS -> StreaksFragment()
                TAB_TASKS -> TasksFragment()
                TAB_DIARY -> DiaryFragment()
                TAB_LISTS -> ListsFragment()
                TAB_SETTINGS -> SettingsFragment()
                else -> StreaksFragment()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB"
        const val TAB_STREAKS = 0
        const val TAB_TASKS = 1
        const val TAB_DIARY = 2
        const val TAB_LISTS = 3
        const val TAB_SETTINGS = 4
    }
}
