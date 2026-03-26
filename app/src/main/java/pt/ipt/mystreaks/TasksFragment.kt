package pt.ipt.mystreaks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.databinding.DialogAddTaskBinding
import pt.ipt.mystreaks.databinding.FragmentTasksBinding
import java.util.Calendar

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getDatabase(requireContext()) }
    private val repository by lazy { TaskRepository(database.taskDao()) }
    private val viewModel: TaskViewModel by viewModels { TaskViewModelFactory(repository) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }

    private var isShowingCompleted = false
    private var isShowingArchive = false
    private var pendingList = emptyList<Task>()
    private var completedList = emptyList<Task>()
    private lateinit var adapter: TaskAdapter
    private var currentTagFilter: String? = null
    private var currentSearchQuery: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTasksBinding.bind(view)

        adapter = TaskAdapter(
            onTaskUpdate = { updatedTask ->
                viewModel.update(updatedTask)
                if (updatedTask.isCompleted && !updatedTask.isArchived) {
                    playConfettiAnimation()
                }
                val estado = if (updatedTask.isCompleted) "concluída" else "atualizada/pendente"
                logViewModel.registrarAcao("TAREFA", "A tarefa '${updatedTask.name}' ficou $estado")
            },
            onEditClicked = { task -> showAddTaskDialog(task) }
        )

        binding.recyclerViewTasks.adapter = adapter
        binding.recyclerViewTasks.layoutManager = LinearLayoutManager(requireContext())

        val swipeAndDragCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
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
                    viewModel.update(task.copy(isArchived = true))
                    logViewModel.registrarAcao("TAREFA", "Arquivou a tarefa '${task.name}'")
                    Snackbar.make(binding.root, "Tarefa arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(task.copy(isArchived = false)) }
                        .show()
                } else {
                    if (direction == ItemTouchHelper.RIGHT) {
                        viewModel.update(task.copy(isArchived = false))
                        logViewModel.registrarAcao("TAREFA", "Restaurou a tarefa '${task.name}'")
                        Snackbar.make(binding.root, "Tarefa restaurada 📝", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.update(task.copy(isArchived = true)) }
                            .show()
                    } else {
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

        binding.ivFilter.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val tags = database.taskDao().getAllTagsSync()
                withContext(Dispatchers.Main) {
                    if (tags.isEmpty()) {
                        Toast.makeText(requireContext(), "Ainda não tens categorias nas tarefas.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    val options = arrayOf("🌟 Todas") + tags.toTypedArray()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Filtrar por Categoria")
                        .setItems(options) { _, which ->
                            currentTagFilter = if (which == 0) null else options[which]
                            refreshUI()
                        }
                        .show()
                }
            }
        }

        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                refreshUI()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
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

        val currentList = baseList.filter {
            (currentTagFilter == null || it.tag == currentTagFilter) &&
                    (currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true))
        }

        adapter.submitList(currentList)

        if (currentList.isEmpty()) {
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
                binding.tvEmptyTitle.text = "Nenhuma Tarefa"
                binding.tvEmptyDesc.text = "Clica em 'Nova' para adicionares uma tarefa!"
            }
        } else {
            binding.recyclerViewTasks.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }

        if (isShowingArchive || isShowingCompleted) binding.fabAddTask.hide() else binding.fabAddTask.show()
    }

    private fun showAddTaskDialog(taskToEdit: Task? = null) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        val isEditing = taskToEdit != null
        var selectedDueDate: Long? = taskToEdit?.dueDate

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = "Editar Tarefa"
            dialogBinding.etTaskName.setText(taskToEdit?.name)
            dialogBinding.etTag.setText(taskToEdit?.tag ?: "")
            dialogBinding.etTaskNotes.setText(taskToEdit?.notes ?: "")
            if (selectedDueDate != null) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                dialogBinding.btnDatePicker.text = "Prazo: ${sdf.format(java.util.Date(selectedDueDate!!))}"
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val existingTags = database.taskDao().getAllTagsSync()
            withContext(Dispatchers.Main) {
                val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, existingTags)
                dialogBinding.etTag.setAdapter(arrayAdapter)
            }
        }

        dialogBinding.btnDatePicker.setOnClickListener {
            val calendar = Calendar.getInstance()
            if (selectedDueDate != null) calendar.timeInMillis = selectedDueDate!!

            android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth, 9, 0, 0)
                selectedDueDate = selectedCal.timeInMillis
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                dialogBinding.btnDatePicker.text = "Prazo: ${sdf.format(selectedCal.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
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
                        viewModel.update(taskToEdit!!.copy(name = taskName, subTasks = newSubTasksList, tag = finalTag, notes = finalNotes, dueDate = selectedDueDate))
                        logViewModel.registrarAcao("TAREFA_EDIT", "Editou a tarefa '$taskName'")
                        scheduleTaskAlarm(taskName, selectedDueDate)
                    } else {
                        viewModel.insert(Task(name = taskName, subTasks = newSubTasksList, tag = finalTag, notes = finalNotes, dueDate = selectedDueDate))
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
        val alarmManager = requireContext().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(requireContext(), TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_NAME", taskName)
        }
        val requestCode = (System.currentTimeMillis() % 10000).toInt()
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            requireContext(), requestCode, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (dueDate > System.currentTimeMillis()) {
            try {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}