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
import pt.ipt.mystreaks.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { StreakRepository(database.streakDao()) }
    private val viewModel: StreakViewModel by viewModels { StreakViewModelFactory(repository) }
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

        adapter = StreakAdapter(
            onStreakCheckChanged = { streak, isChecked ->
                if (streak.isCompleted == isChecked) return@StreakAdapter

                if (isShowingArchive) {
                    Toast.makeText(this, "Restaura a atividade primeiro para a marcares!", Toast.LENGTH_SHORT).show()
                    binding.recyclerViewStreaks.adapter?.notifyDataSetChanged()
                    return@StreakAdapter
                }

                val newCount = if (isChecked) streak.count + 1 else if (streak.count > 0) streak.count - 1 else 0

                var newStartDate = streak.currentStartDate
                if (isChecked && streak.count == 0) {
                    newStartDate = System.currentTimeMillis()
                } else if (!isChecked && newCount == 0) {
                    newStartDate = null
                }

                val updatedStreak = streak.copy(count = newCount, isCompleted = isChecked, currentStartDate = newStartDate)
                viewModel.update(updatedStreak)

                val acao = if (isChecked) "Concluiu" else "Desmarcou"
                logViewModel.registrarAcao("STREAK", "$acao a atividade '${streak.name}' (Fogo: $newCount)")
            },
            onHistoryClicked = { streak ->
                showStreakHistoryDialog(streak)
            }
        )

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(this)

        // --- INÍCIO DA ZONA ALTERADA PARA O DRAG & DROP ---
        val swipeAndDragCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, // Permite arrastar para cima e baixo
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Permite deslizar
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (isShowingArchive) return false // No arquivo não deixamos reordenar

                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // Cria uma cópia da lista atual mutável
                val currentList = adapter.currentList.toMutableList()

                // Troca os itens de posição na memória
                val itemMoved = currentList.removeAt(fromPosition)
                currentList.add(toPosition, itemMoved)

                // Atualiza o ecrã instantaneamente
                adapter.submitList(currentList)

                // Atualiza a posição (orderIndex) na base de dados
                currentList.forEachIndexed { index, streak ->
                    if (streak.orderIndex != index) {
                        viewModel.update(streak.copy(orderIndex = index))
                    }
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val streak = adapter.currentList[position]

                if (!isShowingArchive) {
                    viewModel.update(streak.copy(isArchived = true))
                    logViewModel.registrarAcao("STREAK", "Arquivou a atividade '${streak.name}'")
                    Snackbar.make(binding.root, "${streak.name} arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = false)) }
                        .show()
                } else {
                    if (direction == ItemTouchHelper.RIGHT) {
                        viewModel.update(streak.copy(isArchived = false))
                        logViewModel.registrarAcao("STREAK", "Restaurou '${streak.name}'")
                        Snackbar.make(binding.root, "${streak.name} restaurada 🔥", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = true)) }
                            .show()
                    } else {
                        viewModel.delete(streak)
                        logViewModel.registrarAcao("STREAK", "Eliminou definitivamente '${streak.name}'")
                        Snackbar.make(binding.root, "${streak.name} eliminada 🗑️", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.insert(streak) }
                            .show()
                    }
                }
            }
        }
        ItemTouchHelper(swipeAndDragCallback).attachToRecyclerView(binding.recyclerViewStreaks)
        // --- FIM DA ZONA ALTERADA ---

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
            startActivity(android.content.Intent(this, TasksActivity::class.java))
        }
        binding.fabLogs.setOnClickListener {
            startActivity(android.content.Intent(this, LogsActivity::class.java))
        }
        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        val workRequest = androidx.work.PeriodicWorkRequestBuilder<StreakWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("StreakCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun showStreakHistoryDialog(streak: Streak) {
        val sortedHistory = streak.history.sortedByDescending { it.count }

        if (sortedHistory.isEmpty()) {
            Toast.makeText(this, "Esta atividade ainda não quebrou nenhuma sequência.", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

        val historyItems = sortedHistory.mapIndexed { index, record ->
            val start = sdf.format(java.util.Date(record.startDate))
            val end = sdf.format(java.util.Date(record.endDate))

            val unit = when(streak.type) {
                "D" -> if (record.count == 1) "dia" else "dias"
                "S" -> if (record.count == 1) "semana" else "semanas"
                "M" -> if (record.count == 1) "mês" else "meses"
                else -> ""
            }

            "${index + 1}º -> 🔥 ${record.count} $unit | $start a $end"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("🏆 Recordes: ${streak.name}")
            .setItems(historyItems, null)
            .setPositiveButton("Fechar") { dialog, _ -> dialog.dismiss() }
            .show()
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

        // --- SOLUÇÃO DO ECRÃ PRETO (CORRER EM SEGUNDO PLANO) ---
        Thread {
            try {
                val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                val widgetComponent = android.content.ComponentName(this@MainActivity, StreakWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widgetListView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
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
                        "Diária" -> "D"
                        "Semanal" -> "S"
                        "Mensal" -> "M"
                        else -> "D"
                    }
                    viewModel.insert(Streak(name = activityName, type = type))
                    logViewModel.registrarAcao("STREAK_NOVA", "Criou a atividade '$activityName' ($type)")
                } else {
                    Toast.makeText(this, "O nome não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}