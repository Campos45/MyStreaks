package pt.ipt.mystreaks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // NOVO: Variável do Filtro de Tags
    private var currentTagFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = TaskAdapter(
            onTaskUpdate = { updatedTask ->
                viewModel.update(updatedTask)
                if (updatedTask.isCompleted) {
                    playConfettiAnimation()
                }
                val estado = if (updatedTask.isCompleted) "concluída" else "atualizada/pendente"
                logViewModel.registrarAcao("TAREFA", "A tarefa '${updatedTask.name}' ficou $estado")
            },
            onEditClicked = { task -> showAddTaskDialog(task) }
        )

        binding.recyclerViewTasks.adapter = adapter
        binding.recyclerViewTasks.layoutManager = LinearLayoutManager(this)

        // --- CORREÇÃO DO DRAG & DROP E SWIPE (FUNDIDOS NUM SÓ!) ---
        val swipeAndDragCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, // Direções de Arrastar
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Direções de Apagar
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (isShowingCompleted) return false // Não deixamos reordenar as concluídas

                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val currentList = adapter.currentList.toMutableList()
                val itemMoved = currentList.removeAt(fromPosition)
                currentList.add(toPosition, itemMoved)

                adapter.submitList(currentList)

                currentList.forEachIndexed { index, task ->
                    if (task.orderIndex != index) {
                        viewModel.update(task.copy(orderIndex = index))
                    }
                }
                return true
            }

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
        ItemTouchHelper(swipeAndDragCallback).attachToRecyclerView(binding.recyclerViewTasks)

        // NOVO: Clique do Botão de Filtro (Lupa)
        binding.ivFilter.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val tags = database.taskDao().getAllTagsSync()
                withContext(Dispatchers.Main) {
                    if (tags.isEmpty()) {
                        Toast.makeText(this@TasksActivity, "Ainda não tens categorias nas tarefas.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    val options = arrayOf("🌟 Todas") + tags.toTypedArray()

                    MaterialAlertDialogBuilder(this@TasksActivity)
                        .setTitle("Filtrar por Categoria")
                        .setItems(options) { _, which ->
                            currentTagFilter = if (which == 0) null else options[which]
                            refreshUI()
                        }
                        .show()
                }
            }
        }

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
        val baseList = if (isShowingCompleted) completedList else pendingList

        // NOVO: Aplicar o filtro de tags
        val currentList = if (currentTagFilter != null) {
            baseList.filter { it.tag == currentTagFilter }
        } else {
            baseList
        }

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

    private fun showAddTaskDialog(taskToEdit: Task? = null) {
        val dialogBinding = DialogAddTaskBinding.inflate(LayoutInflater.from(this))
        val isEditing = taskToEdit != null

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = "Editar Tarefa"
            dialogBinding.etTaskName.setText(taskToEdit?.name)
            dialogBinding.etTag.setText(taskToEdit?.tag ?: "") // NOVO: Preencher a tag na edição
        }

        // NOVO: Carregar Tags para as Sugestões Dropdown
        lifecycleScope.launch(Dispatchers.IO) {
            val existingTags = database.taskDao().getAllTagsSync()
            withContext(Dispatchers.Main) {
                val arrayAdapter = android.widget.ArrayAdapter(this@TasksActivity, android.R.layout.simple_dropdown_item_1line, existingTags)
                dialogBinding.etTag.setAdapter(arrayAdapter)
            }
        }

        fun addSubtaskField(text: String = "") {
            val fieldView = LayoutInflater.from(this).inflate(R.layout.item_subtask_input, dialogBinding.layoutSubtaskFields, false)
            val editText = fieldView.findViewById<android.widget.EditText>(R.id.etSubtaskName)
            val btnRemove = fieldView.findViewById<ImageView>(R.id.btnRemoveSubtask)

            editText.setText(text)
            btnRemove.setOnClickListener { dialogBinding.layoutSubtaskFields.removeView(fieldView) }
            dialogBinding.layoutSubtaskFields.addView(fieldView)
        }

        if (isEditing) {
            taskToEdit?.subTasks?.forEach { addSubtaskField(it.name) }
        }

        dialogBinding.btnAddSubtask.setOnClickListener { addSubtaskField() }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val taskName = dialogBinding.etTaskName.text.toString()

                // NOVO: Ler a Tag selecionada
                val tagName = dialogBinding.etTag.text.toString().trim()
                val finalTag = if (tagName.isNotEmpty()) tagName else null

                if (taskName.isNotBlank()) {
                    val newSubTasksList = mutableListOf<SubTask>()
                    for (i in 0 until dialogBinding.layoutSubtaskFields.childCount) {
                        val view = dialogBinding.layoutSubtaskFields.getChildAt(i)

                        // CORREÇÃO: Leitura mais robusta da caixa de texto do sub-passo
                        val editText = view.findViewById<android.widget.EditText>(R.id.etSubtaskName)
                        val text = editText?.text?.toString()?.trim() ?: ""

                        if (text.isNotBlank()) {
                            val wasCompleted = taskToEdit?.subTasks?.find { it.name == text }?.isCompleted ?: false
                            newSubTasksList.add(SubTask(name = text, isCompleted = wasCompleted))
                        }
                    }

                    if (isEditing) {
                        viewModel.update(taskToEdit!!.copy(name = taskName, subTasks = newSubTasksList, tag = finalTag))
                        logViewModel.registrarAcao("TAREFA_EDIT", "Editou a tarefa '$taskName'")
                    } else {
                        viewModel.insert(Task(name = taskName, subTasks = newSubTasksList, tag = finalTag))
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

    private fun playConfettiAnimation() {
        binding.lottieConfetti.visibility = View.VISIBLE
        binding.lottieConfetti.playAnimation()
        binding.lottieConfetti.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                binding.lottieConfetti.visibility = View.GONE
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }
}