package pt.ipt.mystreaks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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

        // Pedir permissão para as notificações no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val adapter = StreakAdapter { streak, isChecked ->
            if (streak.isCompleted == isChecked) return@StreakAdapter

            val newCount = if (isChecked) {
                streak.count + 1
            } else {
                if (streak.count > 0) streak.count - 1 else 0
            }

            val updatedStreak = streak.copy(count = newCount, isCompleted = isChecked)
            viewModel.update(updatedStreak)
        }

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(this)

        // --- Lógica para Deslizar e Apagar (Swipe to Delete) com botão DESFAZER ---
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val streakToDelete = adapter.currentList[position]

                // 1. Apaga a atividade da Base de Dados
                viewModel.delete(streakToDelete)

                // 2. Mostra a barra preta (Snackbar) com a opção de "Desfazer"
                Snackbar.make(binding.recyclerViewStreaks, "${streakToDelete.name} apagada", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") {
                        // Se o utilizador clicar em DESFAZER, voltamos a inserir exatamente a mesma atividade!
                        viewModel.insert(streakToDelete)
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewStreaks)
        // -------------------------------------------------------------

        viewModel.allStreaks.observe(this) { streaks ->
            streaks?.let { adapter.submitList(it) }
        }

        binding.fabAddStreak.setOnClickListener {
            showAddStreakDialog()
        }

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