package pt.ipt.mystreaks

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ipt.mystreaks.databinding.ActivityMainBinding
import pt.ipt.mystreaks.databinding.DialogAddStreakBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { StreakRepository(database.streakDao()) }

    private val viewModel: StreakViewModel by viewModels {
        StreakViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = StreakAdapter { streak, isChecked ->
            if (isChecked) {
                streak.count += 1
                streak.isCompleted = true
            } else {
                if (streak.count > 0) {
                    streak.count -= 1
                }
                streak.isCompleted = false
            }
            viewModel.update(streak)
        }

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(this)

        viewModel.allStreaks.observe(this) { streaks ->
            streaks?.let { adapter.submitList(it) }
        }

        binding.fabAddStreak.setOnClickListener {
            showAddStreakDialog()
        }

        // --- NOVO: Ativar o verificador de tempo e notificações ---
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<StreakWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StreakCheck",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun showAddStreakDialog() {
        val dialogBinding = DialogAddStreakBinding.inflate(LayoutInflater.from(this))

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val activityName = dialogBinding.etActivityName.text.toString()

                if (activityName.isNotBlank()) {
                    val selectedTypeId = dialogBinding.rgFrequency.checkedRadioButtonId
                    val selectedRadioButton = dialogBinding.root.findViewById<RadioButton>(selectedTypeId)

                    val type = when (selectedRadioButton.text.toString()) {
                        "Diária (D)" -> "D"
                        "Semanal (S)" -> "S"
                        "Mensal (M)" -> "M"
                        else -> "D"
                    }

                    val newStreak = Streak(name = activityName, type = type)
                    viewModel.insert(newStreak)

                } else {
                    Toast.makeText(this, "O nome da atividade não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}