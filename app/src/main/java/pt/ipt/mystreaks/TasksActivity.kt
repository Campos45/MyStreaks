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

    private var currentSearchQuery: String = ""

    private var isShowingArchive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)


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
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                // Impede reordenação se estiveres no Arquivo ou nas Concluídas
                if (isShowingCompleted || isShowingArchive) return false

                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val currentList = adapter.currentList.toMutableList()
                java.util.Collections.swap(currentList, fromPosition, toPosition)

                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentList = adapter.currentList
                currentList.forEachIndexed { index, task ->
                    if (task.orderIndex != index) {
                        viewModel.update(task.copy(orderIndex = index))
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = adapter.currentList[position]

                if (!isShowingArchive) {
                    // Manda para o Arquivo!
                    viewModel.update(task.copy(isArchived = true))
                    logViewModel.registrarAcao("TAREFA", "Arquivou a tarefa '${task.name}'")
                    Snackbar.make(binding.root, "Tarefa arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(task.copy(isArchived = false)) }
                        .show()
                } else {
                    // Se já está no arquivo...
                    if (direction == ItemTouchHelper.RIGHT) {
                        // Restaura!
                        viewModel.update(task.copy(isArchived = false))
                        logViewModel.registrarAcao("TAREFA", "Restaurou a tarefa '${task.name}'")
                        Snackbar.make(binding.root, "Tarefa restaurada 📝", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.update(task.copy(isArchived = true)) }
                            .show()
                    } else {
                        // Elimina definitivamente!
                        viewModel.delete(task)
                        logViewModel.registrarAcao("TAREFA", "Eliminou definitivamente '${task.name}'")
                        Snackbar.make(binding.root, "Tarefa eliminada 🗑️", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.insert(task) }
                            .show()
                    }
                }
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
        // Mostra ou esconde a barra de pesquisa com UM CLIQUE na Lupa
        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Deteta quando escreves na barra
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                refreshUI()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        viewModel.pendingTasks.observe(this) { tasks ->
            pendingList = tasks ?: emptyList()
            if (!isShowingCompleted) refreshUI()
        }

        viewModel.completedTasks.observe(this) { tasks ->
            completedList = tasks ?: emptyList()
            if (isShowingCompleted) refreshUI()
        }

        binding.tvToggleArchive.setOnClickListener {
            isShowingArchive = !isShowingArchive
            if (isShowingArchive) isShowingCompleted = false // Garante que não mistura ecrãs
            refreshUI()
        }

        binding.tvToggleCompleted.setOnClickListener {
            isShowingCompleted = !isShowingCompleted
            if (isShowingCompleted) isShowingArchive = false // Garante que não mistura ecrãs
            refreshUI()
        }

        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
    }

    private fun refreshUI() {
        // Junta as listas e filtra o que está ou não arquivado
        val baseList = if (isShowingArchive) {
            (pendingList + completedList).filter { it.isArchived }
        } else if (isShowingCompleted) {
            completedList.filter { !it.isArchived }
        } else {
            pendingList.filter { !it.isArchived }
        }

        val currentList = baseList.filter {
            (currentTagFilter == null || it.tag == currentTagFilter) &&
                    (currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true))
        }

        adapter.submitList(currentList)

        if (currentList.isEmpty()) {
            binding.recyclerViewTasks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            if (isShowingArchive) {
                binding.tvEmptyEmoji.text = "🗄️"
                binding.tvEmptyTitle.text = "Arquivo Vazio"
                binding.tvEmptyDesc.text = "As tarefas que apagares vêm parar aqui."
            } else if (isShowingCompleted) {
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

        // Esconde os ícones que não interessam para poupar espaço na barra!
        if (isShowingArchive) {
            binding.fabAddTask.hide()
            binding.tvAppTitle.text = "📁"
            binding.tvToggleArchive.text = "⬅️"
            binding.tvToggleCompleted.visibility = View.GONE
        } else if (isShowingCompleted) {
            binding.fabAddTask.hide()
            binding.tvAppTitle.text = "🏆"
            binding.tvToggleCompleted.text = "⬅️"
            binding.tvToggleArchive.visibility = View.GONE
        } else {
            binding.fabAddTask.show()
            binding.tvAppTitle.text = "Tarefas 📝"
            binding.tvToggleCompleted.visibility = View.VISIBLE
            binding.tvToggleCompleted.text = "✅"
            binding.tvToggleArchive.visibility = View.VISIBLE
            binding.tvToggleArchive.text = "📁"
        }
    }

    private fun showAddTaskDialog(taskToEdit: Task? = null) {
        val dialogBinding = DialogAddTaskBinding.inflate(LayoutInflater.from(this))
        val isEditing = taskToEdit != null


        var selectedDueDate: Long? = taskToEdit?.dueDate

        dialogBinding.btnDatePicker.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            if (selectedDueDate != null) calendar.timeInMillis = selectedDueDate!!

            android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val selectedCal = java.util.Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth, 9, 0, 0) // Define para as 9h00 da manhã desse dia
                selectedDueDate = selectedCal.timeInMillis

                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                dialogBinding.btnDatePicker.text = "Prazo: ${sdf.format(selectedCal.time)}"
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = "Editar Tarefa"
            dialogBinding.etTaskName.setText(taskToEdit?.name)
            dialogBinding.etTag.setText(taskToEdit?.tag ?: "")
            dialogBinding.etTaskNotes.setText(taskToEdit?.notes ?: "") // NOVO: Preenche as notas
            // Mostrar a data no botão na edição
            if (selectedDueDate != null) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                dialogBinding.btnDatePicker.text = "Prazo: ${sdf.format(java.util.Date(selectedDueDate!!))}"
            }
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
            val fieldView = android.view.LayoutInflater.from(this).inflate(R.layout.item_subtask_input, dialogBinding.layoutSubtaskFields, false)
            val editText = fieldView.findViewById<android.widget.EditText>(R.id.etSubtaskName)
            val btnRemove = fieldView.findViewById<android.widget.ImageView>(R.id.btnRemoveSubtask)

            // NOVO: As nossas setas!
            val btnUp = fieldView.findViewById<android.widget.ImageView>(R.id.btnUpSubtask)
            val btnDown = fieldView.findViewById<android.widget.ImageView>(R.id.btnDownSubtask)

            editText.setText(text)

            // Botão de Remover
            btnRemove.setOnClickListener { dialogBinding.layoutSubtaskFields.removeView(fieldView) }

            // Lógica do Botão SUBIR
            btnUp.setOnClickListener {
                val parent = dialogBinding.layoutSubtaskFields
                val currentIndex = parent.indexOfChild(fieldView)
                if (currentIndex > 0) {
                    parent.removeView(fieldView) // Tira
                    parent.addView(fieldView, currentIndex - 1) // Põe um andar acima
                }
            }

            // Lógica do Botão DESCER
            btnDown.setOnClickListener {
                val parent = dialogBinding.layoutSubtaskFields
                val currentIndex = parent.indexOfChild(fieldView)
                if (currentIndex < parent.childCount - 1) {
                    parent.removeView(fieldView) // Tira
                    parent.addView(fieldView, currentIndex + 1) // Põe um andar abaixo
                }
            }

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

                val notesName = dialogBinding.etTaskNotes.text.toString().trim()
                val finalNotes = if (notesName.isNotEmpty()) notesName else null

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
                        viewModel.update(taskToEdit!!.copy(name = taskName, subTasks = newSubTasksList, tag = finalTag, notes = finalNotes, dueDate = selectedDueDate))
                        logViewModel.registrarAcao("TAREFA_EDIT", "Editou a tarefa '$taskName'")
                        scheduleTaskAlarm(taskName, selectedDueDate) // <--- ADICIONA ISTO AQUI
                    } else {
                        viewModel.insert(Task(name = taskName, subTasks = newSubTasksList, tag = finalTag, notes = finalNotes, dueDate = selectedDueDate))
                        logViewModel.registrarAcao("TAREFA_NOVA", "Criou a tarefa '$taskName'")
                        scheduleTaskAlarm(taskName, selectedDueDate) // <--- E ADICIONA ISTO AQUI TAMBÉM
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
    private fun scheduleTaskAlarm(taskName: String, dueDate: Long?) {
        if (dueDate == null) return

        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(this, TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_NAME", taskName)
        }

        // Criar um ID único para o alarme desta tarefa
        val requestCode = (System.currentTimeMillis() % 10000).toInt()
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, requestCode, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Se a data ainda não passou, agenda o alarme! (Lembra-te que no passo anterior definimos a data para as 9h00)
        if (dueDate > System.currentTimeMillis()) {
            try {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            }
        }
    }
}