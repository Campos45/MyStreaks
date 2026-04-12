package pt.ipt.mystreaks.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pt.ipt.mystreaks.ui.lists.ListsFragment
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.ui.settings.SettingsFragment
import pt.ipt.mystreaks.ui.streak.StreaksFragment
import pt.ipt.mystreaks.ui.tasks.TasksFragment
import pt.ipt.mystreaks.databinding.ActivityMainBinding
import pt.ipt.mystreaks.services.StreakWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Liga o Trabalhador de Fundo para verificar os Resets todos os dias à meia-noite
        val streakWorkRequest = PeriodicWorkRequestBuilder<StreakWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StreakResetWork",
            ExistingPeriodicWorkPolicy.KEEP,
            streakWorkRequest
        )

        // Carrega o ecrã das Streaks logo ao abrir a app pela primeira vez
        if (savedInstanceState == null) {
            replaceFragment(StreaksFragment())
        }

        // Lógica dos cliques na barra de baixo (Bottom Navigation)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_streaks -> {
                    replaceFragment(StreaksFragment())
                    true
                }
                R.id.nav_tasks -> {
                    replaceFragment(TasksFragment())
                    true
                }
                R.id.nav_lists -> {
                    replaceFragment(ListsFragment())
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    // Função que troca o ecrã que está no meio da aplicação
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}