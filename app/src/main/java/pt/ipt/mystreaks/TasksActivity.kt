package pt.ipt.mystreaks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import pt.ipt.mystreaks.databinding.ActivityTasksBinding
import pt.ipt.mystreaks.databinding.DialogAddTaskBinding

class TasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTasksBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { TaskRepository(database.taskDao()) }
    private val viewModel: TaskViewModel by viewModels { TaskViewModelFactory(repository) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }

    private var isShowingCompleted = false
    private var pendingList = emptyList<Task>()
    private var completedList = emptyList<Task>()
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // NOVO: Adicionado onEditClicked
        adapter = TaskAdapter(
            onTaskUpdate = { updatedTask ->
                viewModel.update(updatedTask)
                val estado = if (updatedTask.isCompleted) "concluída" else "atualizada/pendente"
                logViewModel.registrarAcao("TAREFA", "A tarefa '${updatedTask.name}' ficou $estado")
            },
            onEditClicked = { task ->
                showAddTaskDialog(task) // Abrir a mesma janela, mas com a tarefa a editar
            }
        )

        binding.recyclerViewTasks.adapter = adapter
        binding.recyclerViewTasks.layoutManager = LinearLayoutManager(this)

        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = adapter.currentList[position]
                viewModel.delete(task)

                logViewModel.registrarAcao("TAREFA", "Eliminou a tarefa '${task.name}'")

                Snackbar.make(binding.root, "Tarefa eliminada 🗑️", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") { viewModel.insert(task) }
                    .show()
            }
        }
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(binding.recyclerViewTasks)

        viewModel.pendingTasks.observe(this) { tasks ->
            pendingList = tasks ?: emptyList()
            if (!isShowingCompleted) refreshUI()
        }

        viewModel.completedTasks.observe(this) { tasks ->
            completedList = tasks ?: emptyList()
            if (isShowingCompleted) refreshUI()
        }

        binding.tvToggleCompleted.setOnClickListener {
            isShowingCompleted = !isShowingCompleted
            refreshUI()
        }

        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
    }

    private fun refreshUI() {
        val currentList = if (isShowingCompleted) completedList else pendingList
        adapter.submitList(currentList)

        if (currentList.isEmpty()) {
            binding.recyclerViewTasks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            if (isShowingCompleted) {
                binding.tvEmptyEmoji.text = "🏆"
                binding.tvEmptyTitle.text = "Sem tarefas concluídas"
                binding.tvEmptyDesc.text = "As tuas vitórias vão aparecer aqui."
            } else {
                binding.tvEmptyEmoji.text = "📋"
                binding.tvEmptyTitle.text = "Nenhuma Tarefa"
                binding.tvEmptyDesc.text = "Clica em 'Nova' para adicionares uma tarefa!"
            }
        } else {
            binding.recyclerViewTasks.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }

        if (isShowingCompleted) {
            binding.fabAddTask.hide()
            binding.tvAppTitle.text = "Concluídas 🏆"
            binding.tvToggleCompleted.text = "⬅️ Tarefas"
        } else {
            binding.fabAddTask.show()
            binding.tvAppTitle.text = "Tarefas 📝"
            binding.tvToggleCompleted.text = "✅ Concluídas"
        }
    }

    // A nossa função reformulada para suportar Criação E Edição
    private fun showAddTaskDialog(taskToEdit: Task? = null) {
        val dialogBinding = DialogAddTaskBinding.inflate(LayoutInflater.from(this))
        val isEditing = taskToEdit != null

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = "Editar Tarefa"
            dialogBinding.etTaskName.setText(taskToEdit?.name)
        }

        // Função local para injetar uma caixa de texto dinamicamente
        fun addSubtaskField(text: String = "") {
            val fieldView = LayoutInflater.from(this).inflate(R.layout.item_subtask_input, dialogBinding.layoutSubtaskFields, false)
            val editText = fieldView.findViewById<TextInputEditText>(R.id.etSubtaskName)
            val btnRemove = fieldView.findViewById<ImageView>(R.id.btnRemoveSubtask)

            editText.setText(text)
            btnRemove.setOnClickListener {
                dialogBinding.layoutSubtaskFields.removeView(fieldView)
            }
            dialogBinding.layoutSubtaskFields.addView(fieldView)
        }

        // Se estamos a editar e já há sub-passos, cria as caixas para eles!
        if (isEditing) {
            taskToEdit?.subTasks?.forEach { subTask ->
                addSubtaskField(subTask.name)
            }
        }

        // Evento do Botão "+"
        dialogBinding.btnAddSubtask.setOnClickListener {
            addSubtaskField() // Adiciona uma caixa de texto vazia
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val taskName = dialogBinding.etTaskName.text.toString()

                if (taskName.isNotBlank()) {
                    // Extrair os textos de todas as caixas de sub-tarefa criadas
                    val newSubTasksList = mutableListOf<SubTask>()
                    for (i in 0 until dialogBinding.layoutSubtaskFields.childCount) {
                        val view = dialogBinding.layoutSubtaskFields.getChildAt(i)
                        val editText = view.findViewById<TextInputEditText>(R.id.etSubtaskName)
                        val text = editText.text.toString().trim()

                        if (text.isNotBlank()) {
                            // Se estivermos a editar e a sub-tarefa já existia com o mesmo nome, preserva o isCompleted dela
                            val wasCompleted = taskToEdit?.subTasks?.find { it.name == text }?.isCompleted ?: false
                            newSubTasksList.add(SubTask(name = text, isCompleted = wasCompleted))
                        }
                    }

                    if (isEditing) {
                        viewModel.update(taskToEdit!!.copy(name = taskName, subTasks = newSubTasksList))
                        logViewModel.registrarAcao("TAREFA_EDIT", "Editou a tarefa '$taskName'")
                    } else {
                        viewModel.insert(Task(name = taskName, subTasks = newSubTasksList))
                        logViewModel.registrarAcao("TAREFA_NOVA", "Criou a tarefa '$taskName'")
                    }
                } else {
                    Toast.makeText(this, "O nome da tarefa não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}