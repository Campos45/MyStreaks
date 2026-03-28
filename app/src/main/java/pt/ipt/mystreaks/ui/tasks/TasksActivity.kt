package pt.ipt.mystreaks.ui.tasks

import android.R
import android.animation.Animator
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.ui.logs.LogViewModel
import pt.ipt.mystreaks.ui.logs.LogViewModelFactory
import pt.ipt.mystreaks.services.TaskAlarmReceiver
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.SubTask
import pt.ipt.mystreaks.data.model.Task
import pt.ipt.mystreaks.data.repository.LogRepository
import pt.ipt.mystreaks.data.repository.TaskRepository
import pt.ipt.mystreaks.databinding.ActivityTasksBinding
import pt.ipt.mystreaks.databinding.DialogAddTaskBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        binding.recyclerViewTasks.layoutManager = LinearLayoutManager(this)

        binding.ivFilter.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val tags = database.tagDao().getAllTagsSyncList().filter { it.type == "T" }.map { it.name }
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

        binding.etSearch.setTextColor(Color.BLACK)
        binding.etSearch.setHintTextColor(Color.DKGRAY)

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
            if (isShowingArchive) isShowingCompleted = false
            refreshUI()
        }

        binding.tvToggleCompleted.setOnClickListener {
            isShowingCompleted = !isShowingCompleted
            if (isShowingCompleted) isShowingArchive = false
            refreshUI()
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

        dialogBinding.etTaskName.setTextColor(Color.BLACK)
        dialogBinding.etTaskName.setHintTextColor(Color.DKGRAY)
        dialogBinding.etTag.setTextColor(Color.BLACK)
        dialogBinding.etTag.setHintTextColor(Color.DKGRAY)
        dialogBinding.etTaskNotes.setTextColor(Color.BLACK)
        dialogBinding.etTaskNotes.setHintTextColor(Color.DKGRAY)

        // API MODERNA DE DATAS
        val initialDate = if (selectedDueDate != null) {
            Instant.ofEpochMilli(selectedDueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        } else {
            LocalDate.now()
        }

        dialogBinding.btnDatePicker.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val pickedDate = LocalDate.of(year, month + 1, dayOfMonth)
                val zdt = pickedDate.atTime(9, 0).atZone(ZoneId.systemDefault())
                selectedDueDate = zdt.toInstant().toEpochMilli()

                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                dialogBinding.btnDatePicker.text = "Prazo: ${pickedDate.format(formatter)}"
            }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth).show()
        }

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = "Editar Tarefa"
            dialogBinding.etTaskName.setText(taskToEdit?.name)
            dialogBinding.etTag.setText(taskToEdit?.tag ?: "")
            dialogBinding.etTaskNotes.setText(taskToEdit?.notes ?: "")

            dialogBinding.ratingPriority.rating = taskToEdit?.priority?.toFloat() ?: 3.0f

            if (selectedDueDate != null) {
                val date = Instant.ofEpochMilli(selectedDueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                dialogBinding.btnDatePicker.text = "Prazo: ${date.format(formatter)}"
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val existingTags = database.tagDao().getAllTagsSyncList().filter { it.type == "T" }.map { it.name }
            withContext(Dispatchers.Main) {
                val arrayAdapter = ArrayAdapter(this@TasksActivity, R.layout.simple_dropdown_item_1line, existingTags)
                dialogBinding.etTag.setAdapter(arrayAdapter)
            }
        }

        fun addSubtaskField(text: String = "") {
            val fieldView = LayoutInflater.from(this).inflate(pt.ipt.mystreaks.R.layout.item_subtask_input, dialogBinding.layoutSubtaskFields, false)
            val editText = fieldView.findViewById<EditText>(pt.ipt.mystreaks.R.id.etSubtaskName)
            val btnRemove = fieldView.findViewById<ImageView>(pt.ipt.mystreaks.R.id.btnRemoveSubtask)
            val btnUp = fieldView.findViewById<ImageView>(pt.ipt.mystreaks.R.id.btnUpSubtask)
            val btnDown = fieldView.findViewById<ImageView>(pt.ipt.mystreaks.R.id.btnDownSubtask)

            editText.setTextColor(Color.BLACK)
            editText.setHintTextColor(Color.DKGRAY)
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

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val taskName = dialogBinding.etTaskName.text.toString()
                val tagName = dialogBinding.etTag.text.toString().trim()
                val finalTag = if (tagName.isNotEmpty()) tagName else null
                val notesName = dialogBinding.etTaskNotes.text.toString().trim()
                val finalNotes = if (notesName.isNotEmpty()) notesName else null

                val priorityValue = dialogBinding.ratingPriority.rating.toInt()

                if (taskName.isNotBlank()) {
                    val newSubTasksList = mutableListOf<SubTask>()
                    for (i in 0 until dialogBinding.layoutSubtaskFields.childCount) {
                        val view = dialogBinding.layoutSubtaskFields.getChildAt(i)
                        val editText = view.findViewById<EditText>(pt.ipt.mystreaks.R.id.etSubtaskName)
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

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_NAME", taskName)
        }

        val requestCode = (System.currentTimeMillis() % 10000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
}