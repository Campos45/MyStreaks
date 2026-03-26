package pt.ipt.mystreaks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import pt.ipt.mystreaks.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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