package pt.ipt.mystreaks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
    private val viewModel: StreakViewModel by viewModels { StreakViewModelFactory(repository) }

    // NOVO: ViewModel dos Logs
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }

    private var isShowingArchive = false
    private var activeList = emptyList<Streak>()
    private var archivedList = emptyList<Streak>()
    private lateinit var adapter: StreakAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        adapter = StreakAdapter { streak, isChecked ->
            if (streak.isCompleted == isChecked) return@StreakAdapter

            if (isShowingArchive) {
                Toast.makeText(this, "Restaura a atividade primeiro para a marcares!", Toast.LENGTH_SHORT).show()
                binding.recyclerViewStreaks.adapter?.notifyDataSetChanged()
                return@StreakAdapter
            }

            val newCount = if (isChecked) streak.count + 1 else if (streak.count > 0) streak.count - 1 else 0
            val updatedStreak = streak.copy(count = newCount, isCompleted = isChecked)
            viewModel.update(updatedStreak)

            // NOVO: Escrever no Diário de Logs!
            val acao = if (isChecked) "Concluiu" else "Desmarcou"
            logViewModel.registrarAcao("STREAK", "$acao a atividade '${streak.name}' (Fogo: $newCount)")
        }

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(this)

        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val streak = adapter.currentList[position]

                if (!isShowingArchive) {
                    viewModel.update(streak.copy(isArchived = true))
                    logViewModel.registrarAcao("STREAK", "Arquivou a atividade '${streak.name}'") // LOG
                    Snackbar.make(binding.root, "${streak.name} arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") {
                            viewModel.update(streak.copy(isArchived = false))
                            logViewModel.registrarAcao("STREAK", "Desfez o arquivo de '${streak.name}'") // LOG
                        }
                        .show()
                } else {
                    if (direction == ItemTouchHelper.RIGHT) {
                        viewModel.update(streak.copy(isArchived = false))
                        logViewModel.registrarAcao("STREAK", "Restaurou a atividade '${streak.name}'") // LOG
                        Snackbar.make(binding.root, "${streak.name} restaurada 🔥", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = true)) }
                            .show()
                    } else {
                        viewModel.delete(streak)
                        logViewModel.registrarAcao("STREAK", "Eliminou definitivamente '${streak.name}'") // LOG
                        Snackbar.make(binding.root, "${streak.name} eliminada 🗑️", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.insert(streak) }
                            .show()
                    }
                }
            }
        }
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(binding.recyclerViewStreaks)

        viewModel.activeStreaks.observe(this) { streaks ->
            activeList = streaks ?: emptyList()
            if (!isShowingArchive) refreshUI()
        }

        viewModel.archivedStreaks.observe(this) { streaks ->
            archivedList = streaks ?: emptyList()
            if (isShowingArchive) refreshUI()
        }

        binding.tvToggleArchive.setOnClickListener {
            isShowingArchive = !isShowingArchive
            refreshUI()
        }

        binding.tvNavTasks.setOnClickListener {
            val intent = android.content.Intent(this, TasksActivity::class.java)
            startActivity(intent)
        }

        binding.fabLogs.setOnClickListener {
            startActivity(android.content.Intent(this, LogsActivity::class.java))
        }

        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        val workRequest = androidx.work.PeriodicWorkRequestBuilder<StreakWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("StreakCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun refreshUI() {
        val currentList = if (isShowingArchive) archivedList else activeList
        adapter.submitList(currentList)

        if (currentList.isEmpty()) {
            binding.recyclerViewStreaks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            if (isShowingArchive) {
                binding.tvEmptyEmoji.text = "🗄️"
                binding.tvEmptyTitle.text = "Arquivo Vazio"
                binding.tvEmptyDesc.text = "As tuas atividades arquivadas vão aparecer aqui."
            } else {
                binding.tvEmptyEmoji.text = "🏕️"
                binding.tvEmptyTitle.text = "Nenhuma atividade"
                binding.tvEmptyDesc.text = "Clica em 'Nova' para começares a tua streak!"
            }
        } else {
            binding.recyclerViewStreaks.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }

        if (isShowingArchive) {
            binding.fabAddStreak.hide()
            binding.tvAppTitle.text = "Arquivo 🗄️"
            binding.tvToggleArchive.text = "⬅️ Voltar"
        } else {
            binding.fabAddStreak.show()
            binding.tvAppTitle.text = "MyStreaks 🔥"
            binding.tvToggleArchive.text = "📁 Arquivo"
        }
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
                    viewModel.insert(Streak(name = activityName, type = type))
                    logViewModel.registrarAcao("STREAK_NOVA", "Criou a atividade '$activityName' ($type)") // LOG
                } else {
                    Toast.makeText(this, "O nome não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}