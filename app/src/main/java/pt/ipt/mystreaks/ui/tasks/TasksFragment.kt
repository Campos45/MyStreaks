package pt.ipt.mystreaks.ui.tasks

import android.animation.Animator
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import pt.ipt.mystreaks.ui.logs.LogViewModel
import pt.ipt.mystreaks.ui.logs.LogViewModelFactory
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.ui.settings.TagDropdownAdapter
import pt.ipt.mystreaks.ui.settings.TagViewModel
import pt.ipt.mystreaks.services.TaskAlarmReceiver
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.SubTask
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.model.Task
import pt.ipt.mystreaks.data.repository.LogRepository
import pt.ipt.mystreaks.data.repository.TaskRepository
import pt.ipt.mystreaks.databinding.DialogAddTaskBinding
import pt.ipt.mystreaks.databinding.FragmentTasksBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getDatabase(requireContext()) }
    private val repository by lazy { TaskRepository(database.taskDao()) }
    private val viewModel: TaskViewModel by viewModels { TaskViewModelFactory(repository) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }
    private val tagViewModel: TagViewModel by viewModels()

    private var isShowingCompleted = false
    private var isShowingArchive = false
    private var pendingList = emptyList<Task>()
    private var completedList = emptyList<Task>()
    private lateinit var adapter: TaskAdapter

    // Controlo de Filtros e Ordenação
    private var currentTagFilter: String = "ALL" // "ALL", "NONE" ou "NomeDaTag"
    private var currentPriorityFilter: Int? = null // 1 a 5, ou null para mostrar todas
    private var currentSortOrder: String? = null // "ASC", "DESC" ou null
    private var currentSearchQuery: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTasksBinding.bind(view)

        binding.etSearch.setTextColor(Color.BLACK)
        binding.etSearch.setHintTextColor(Color.DKGRAY)

        adapter = TaskAdapter(
            onTaskUpdate = { updatedTask ->
                viewModel.update(updatedTask)
                if (updatedTask.isCompleted && !updatedTask.isArchived) {
                    playConfettiAnimation()
                }
                val estado = if (updatedTask.isCompleted) "concluída" else "atualizada/pendente"
                logViewModel.registrarAcao("TAREFA", "A tarefa '${updatedTask.name}' ficou $estado")
            },
            onEditClicked = { task -> showAddTaskDialog(task) },
            onArchiveClicked = { task ->
                if (!isShowingArchive) {
                    viewModel.update(task.copy(isArchived = true))
                    logViewModel.registrarAcao("TAREFA", "Arquivou a tarefa '${task.name}'")
                    Snackbar.make(binding.root, "Tarefa arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(task.copy(isArchived = false)) }
                        .show()
                } else {
                    viewModel.update(task.copy(isArchived = false))
                    logViewModel.registrarAcao("TAREFA", "Restaurou a tarefa '${task.name}'")
                    Snackbar.make(binding.root, "Tarefa restaurada 📝", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(task.copy(isArchived = true)) }
                        .show()
                }
            },
            onDeleteClicked = { task ->
                viewModel.delete(task)
                logViewModel.registrarAcao("TAREFA", "Eliminou '${task.name}'")
                Snackbar.make(binding.root, "Tarefa eliminada 🗑️", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") { viewModel.insert(task) }.show()
            }
        )

        binding.recyclerViewTasks.adapter = adapter
        binding.recyclerViewTasks.layoutManager = LinearLayoutManager(requireContext())

        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            val taskTags = allTags.filter { it.type == "T" }
            adapter.setTags(taskTags)
        }

        binding.ivFilter.setOnClickListener {
            val filterTypes = arrayOf("🏷️ Filtrar por Categoria", "⭐ Ordenar ou Filtrar por Prioridade", "❌ Limpar Todos os Filtros")

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtros")
                .setItems(filterTypes) { _, which ->
                    when (which) {
                        0 -> { // Por Categoria
                            val taskTags = tagViewModel.allTags.value?.filter { it.type == "T" } ?: emptyList()
                            val options = arrayOf("🌟 Todas as Categorias", "🚫 Sem Categoria") + taskTags.map { it.name }.toTypedArray()

                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Escolher Categoria")
                                .setItems(options) { _, tagIndex ->
                                    currentTagFilter = when (tagIndex) {
                                        0 -> "ALL"
                                        1 -> "NONE"
                                        else -> options[tagIndex]
                                    }
                                    refreshUI()
                                }.show()
                        }
                        1 -> { // Ordenação e Filtro por Prioridade
                            val prioOptions = arrayOf(
                                "⬆️ Ordenar: Menor Prioridade Primeiro",
                                "⬇️ Ordenar: Maior Prioridade Primeiro",
                                "--- Mostrar Apenas: ---",
                                "⭐ 1 (Muito Baixa)",
                                "⭐⭐ 2 (Baixa)",
                                "⭐⭐⭐ 3 (Média)",
                                "⭐⭐⭐⭐ 4 (Alta)",
                                "⭐⭐⭐⭐⭐ 5 (Urgente)"
                            )
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Prioridade")
                                .setItems(prioOptions) { _, prioIndex ->
                                    when (prioIndex) {
                                        0 -> { currentSortOrder = "ASC"; currentPriorityFilter = null }
                                        1 -> { currentSortOrder = "DESC"; currentPriorityFilter = null }
                                        2 -> { /* Separador, ignorar */ }
                                        else -> {
                                            // Como temos 3 opções antes (0, 1 e 2), subtraímos 2 para bater certo com a prioridade (1 a 5)
                                            currentPriorityFilter = prioIndex - 2
                                            currentSortOrder = null // Limpa a ordenação se o utilizador quiser ver só uma específica
                                        }
                                    }
                                    refreshUI()
                                }.show()
                        }
                        2 -> { // Limpar Filtros
                            currentTagFilter = "ALL"
                            currentPriorityFilter = null
                            currentSortOrder = null
                            refreshUI()
                            Toast.makeText(requireContext(), "Filtros limpos!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.show()
        }

        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                refreshUI()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        viewModel.pendingTasks.observe(viewLifecycleOwner) { tasks ->
            pendingList = tasks ?: emptyList()
            if (!isShowingCompleted && !isShowingArchive) refreshUI()
        }

        viewModel.completedTasks.observe(viewLifecycleOwner) { tasks ->
            completedList = tasks ?: emptyList()
            if (isShowingCompleted) refreshUI()
        }

        binding.toggleGroupTasks.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnPendingTasks -> { isShowingCompleted = false; isShowingArchive = false }
                    R.id.btnCompletedTasks -> { isShowingCompleted = true; isShowingArchive = false }
                    R.id.btnArchivedTasks -> { isShowingCompleted = false; isShowingArchive = true }
                }
                refreshUI()
            }
        }

        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
    }

    private fun refreshUI() {
        val baseList = if (isShowingArchive) {
            (pendingList + completedList).filter { it.isArchived }
        } else if (isShowingCompleted) {
            completedList.filter { !it.isArchived }
        } else {
            pendingList.filter { !it.isArchived }
        }

        // 1. LÓGICA DE FILTRAGEM TRIPLA
        val filteredList = baseList.filter { task ->
            val matchesTag = when (currentTagFilter) {
                "ALL" -> true
                "NONE" -> task.tag.isNullOrBlank()
                else -> task.tag == currentTagFilter
            }
            val matchesPriority = currentPriorityFilter == null || task.priority == currentPriorityFilter
            val matchesSearch = currentSearchQuery.isEmpty() || task.name.contains(currentSearchQuery, ignoreCase = true)

            matchesTag && matchesPriority && matchesSearch
        }

        // 2. ORDENAÇÃO
        val finalList = when (currentSortOrder) {
            "ASC" -> filteredList.sortedBy { it.priority }
            "DESC" -> filteredList.sortedByDescending { it.priority }
            else -> filteredList
        }

        adapter.submitList(finalList)

        if (finalList.isEmpty()) {
            binding.recyclerViewTasks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            if (isShowingArchive) {
                binding.tvEmptyEmoji.text = "🗃️"
                binding.tvEmptyTitle.text = "Arquivo Vazio"
                binding.tvEmptyDesc.text = "As tarefas que apagares vêm parar aqui."
            } else if (isShowingCompleted) {
                binding.tvEmptyEmoji.text = "🏆"
                binding.tvEmptyTitle.text = "Sem tarefas concluídas"
                binding.tvEmptyDesc.text = "As tuas vitórias vão aparecer aqui."
            } else {
                binding.tvEmptyEmoji.text = "📋"
                binding.tvEmptyTitle.text = "Nenhum Resultado"
                binding.tvEmptyDesc.text = "Não encontrámos tarefas com estes filtros."
            }
        } else {
            binding.recyclerViewTasks.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }
    }

    private fun showAddTaskDialog(taskToEdit: Task? = null) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        val isEditing = taskToEdit != null
        var selectedDueDate: Long? = taskToEdit?.dueDate

        val taskTags = tagViewModel.allTags.value?.filter { it.type == "T" } ?: emptyList()
        val customAdapter = TagDropdownAdapter(requireContext(), taskTags)
        dialogBinding.etTag.setAdapter(customAdapter)

        dialogBinding.etTag.setOnClickListener { dialogBinding.etTag.showDropDown() }
        dialogBinding.etTag.setOnItemClickListener { parent, _, position, _ ->
            val selectedTag = parent.getItemAtPosition(position) as Tag
            dialogBinding.etTag.setText(selectedTag.name, false)
        }

        // API MODERNA DE DATAS (LocalDate) no Fragment, tal como tínhamos ajustado
        val initialDate = if (selectedDueDate != null) {
            Instant.ofEpochMilli(selectedDueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        } else {
            LocalDate.now()
        }

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = "Editar Tarefa"
            dialogBinding.etTaskName.setText(taskToEdit?.name)
            dialogBinding.etTag.setText(taskToEdit?.tag ?: "", false)
            dialogBinding.etTaskNotes.setText(taskToEdit?.notes ?: "")
            dialogBinding.ratingPriority.rating = taskToEdit?.priority?.toFloat() ?: 3.0f

            if (selectedDueDate != null) {
                val zdt = Instant.ofEpochMilli(selectedDueDate!!).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                dialogBinding.btnDatePicker.text = "Prazo: ${zdt.format(formatter)}"
            }
        } else {
            dialogBinding.ratingPriority.rating = 3.0f
        }

        dialogBinding.btnDatePicker.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val pickedDate = LocalDate.of(year, month + 1, dayOfMonth)

                TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                    val zdt = pickedDate.atTime(hourOfDay, minute).atZone(ZoneId.systemDefault())
                    selectedDueDate = zdt.toInstant().toEpochMilli()

                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    dialogBinding.btnDatePicker.text = "Prazo: ${zdt.toLocalDateTime().format(formatter)}"
                }, 9, 0, true).show()

            }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth).show()
        }

        fun addSubtaskField(text: String = "") {
            val fieldView = LayoutInflater.from(requireContext()).inflate(R.layout.item_subtask_input, dialogBinding.layoutSubtaskFields, false)
            val editText = fieldView.findViewById<EditText>(R.id.etSubtaskName)
            val btnRemove = fieldView.findViewById<ImageView>(R.id.btnRemoveSubtask)
            val btnUp = fieldView.findViewById<ImageView>(R.id.btnUpSubtask)
            val btnDown = fieldView.findViewById<ImageView>(R.id.btnDownSubtask)

            editText.setText(text)

            btnRemove.setOnClickListener { dialogBinding.layoutSubtaskFields.removeView(fieldView) }
            btnUp.setOnClickListener {
                val parent = dialogBinding.layoutSubtaskFields
                val currentIndex = parent.indexOfChild(fieldView)
                if (currentIndex > 0) {
                    parent.removeView(fieldView)
                    parent.addView(fieldView, currentIndex - 1)
                }
            }
            btnDown.setOnClickListener {
                val parent = dialogBinding.layoutSubtaskFields
                val currentIndex = parent.indexOfChild(fieldView)
                if (currentIndex < parent.childCount - 1) {
                    parent.removeView(fieldView)
                    parent.addView(fieldView, currentIndex + 1)
                }
            }
            dialogBinding.layoutSubtaskFields.addView(fieldView)
        }

        if (isEditing) {
            taskToEdit?.subTasks?.forEach { addSubtaskField(it.name) }
        }

        dialogBinding.btnAddSubtask.setOnClickListener { addSubtaskField() }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val taskName = dialogBinding.etTaskName.text.toString()
                val tagName = dialogBinding.etTag.text.toString().trim()
                val finalTag = if (tagName.isNotEmpty()) tagName else null
                val priorityValue = dialogBinding.ratingPriority.rating.toInt()

                if (finalTag != null) {
                    val exists = tagViewModel.allTags.value?.any { it.name.trim().equals(finalTag, true) && it.type == "T" } ?: false
                    if (!exists) {
                        tagViewModel.insert(Tag(name = finalTag, color = "#757575", type = "T"))
                    }
                }

                val notesName = dialogBinding.etTaskNotes.text.toString().trim()
                val finalNotes = if (notesName.isNotEmpty()) notesName else null

                if (taskName.isNotBlank()) {
                    val newSubTasksList = mutableListOf<SubTask>()
                    for (i in 0 until dialogBinding.layoutSubtaskFields.childCount) {
                        val view = dialogBinding.layoutSubtaskFields.getChildAt(i)
                        val editText = view.findViewById<EditText>(R.id.etSubtaskName)
                        val text = editText?.text?.toString()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            val wasCompleted = taskToEdit?.subTasks?.find { it.name == text }?.isCompleted ?: false
                            newSubTasksList.add(SubTask(name = text, isCompleted = wasCompleted))
                        }
                    }

                    if (isEditing) {
                        viewModel.update(taskToEdit!!.copy(name = taskName, subTasks = newSubTasksList, tag = finalTag, notes = finalNotes, dueDate = selectedDueDate, priority = priorityValue))
                        logViewModel.registrarAcao("TAREFA_EDIT", "Editou a tarefa '$taskName'")
                        scheduleTaskAlarm(taskName, selectedDueDate)
                    } else {
                        viewModel.insert(
                            Task(
                                name = taskName,
                                subTasks = newSubTasksList,
                                tag = finalTag,
                                notes = finalNotes,
                                dueDate = selectedDueDate,
                                priority = priorityValue
                            )
                        )
                        logViewModel.registrarAcao("TAREFA_NOVA", "Criou a tarefa '$taskName'")
                        scheduleTaskAlarm(taskName, selectedDueDate)
                    }
                } else {
                    Toast.makeText(requireContext(), "O nome da tarefa não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun playConfettiAnimation() {
        binding.lottieConfetti.visibility = View.VISIBLE
        binding.lottieConfetti.playAnimation()
        binding.lottieConfetti.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                binding.lottieConfetti.visibility = View.GONE
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }

    private fun scheduleTaskAlarm(taskName: String, dueDate: Long?) {
        if (dueDate == null) return
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_NAME", taskName)
        }
        val requestCode = (System.currentTimeMillis() % 10000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (dueDate > System.currentTimeMillis()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}