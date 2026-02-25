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

    // Variáveis para controlar qual ecrã estamos a ver
    private var isShowingArchive = false
    private var activeList = emptyList<Streak>()
    private var archivedList = emptyList<Streak>()
    private lateinit var adapter: StreakAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Permissões
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // --- NOVO: Botão para abrir o Ecrã de Tarefas ---
        binding.tvNavTasks.setOnClickListener {
            val intent = android.content.Intent(this, TasksActivity::class.java)
            startActivity(intent)
        }

        // Configurar o Adapter
        adapter = StreakAdapter { streak, isChecked ->
            if (streak.isCompleted == isChecked) return@StreakAdapter

            // Se o utilizador tentar clicar na checkbox no arquivo, mandamos avisar para restaurar primeiro!
            if (isShowingArchive) {
                Toast.makeText(this, "Restaura a atividade primeiro para a marcares!", Toast.LENGTH_SHORT).show()
                binding.recyclerViewStreaks.adapter?.notifyDataSetChanged() // Força a checkbox a não mudar visualmente
                return@StreakAdapter
            }

            val newCount = if (isChecked) streak.count + 1 else if (streak.count > 0) streak.count - 1 else 0
            val updatedStreak = streak.copy(count = newCount, isCompleted = isChecked)
            viewModel.update(updatedStreak)
        }

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(this)

        // Lógica Inteligente dos Swipes!
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val streak = adapter.currentList[position]

                if (!isShowingArchive) {
                    // SE ESTAMOS NO PRINCIPAL: Deslizar = ARQUIVAR
                    viewModel.update(streak.copy(isArchived = true))
                    Snackbar.make(binding.root, "${streak.name} arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = false)) }
                        .show()
                } else {
                    // SE ESTAMOS NO ARQUIVO
                    if (direction == ItemTouchHelper.RIGHT) {
                        // Deslizar Direita = RESTAURAR
                        viewModel.update(streak.copy(isArchived = false))
                        Snackbar.make(binding.root, "${streak.name} restaurada 🔥", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = true)) }
                            .show()
                    } else {
                        // Deslizar Esquerda = ELIMINAR DE VEZ
                        viewModel.delete(streak)
                        Snackbar.make(binding.root, "${streak.name} eliminada 🗑️", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.insert(streak) }
                            .show()
                    }
                }
            }
        }
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(binding.recyclerViewStreaks)

        // Observar Ativas
        viewModel.activeStreaks.observe(this) { streaks ->
            activeList = streaks ?: emptyList()
            if (!isShowingArchive) refreshUI()
        }

        // Observar Arquivadas
        viewModel.archivedStreaks.observe(this) { streaks ->
            archivedList = streaks ?: emptyList()
            if (isShowingArchive) refreshUI()
        }

        // O Botão de Alternar o Ecrã
        binding.tvToggleArchive.setOnClickListener {
            isShowingArchive = !isShowingArchive
            refreshUI()
        }

        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        val workRequest = androidx.work.PeriodicWorkRequestBuilder<StreakWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("StreakCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    // Função que redesenha o ecrã conforme estamos no Arquivo ou não
    private fun refreshUI() {
        val currentList = if (isShowingArchive) archivedList else activeList
        adapter.submitList(currentList)

        if (currentList.isEmpty()) {
            binding.recyclerViewStreaks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE

            // Muda o texto da "Tenda" se for o arquivo que estiver vazio
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

        // Se estivermos no arquivo, esconde o botão '+' e muda o nome do topo
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
                } else {
                    Toast.makeText(this, "O nome não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}