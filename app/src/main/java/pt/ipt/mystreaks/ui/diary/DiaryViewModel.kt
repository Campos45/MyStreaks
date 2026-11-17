package pt.ipt.mystreaks.ui.diary

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.ipt.mystreaks.data.model.DiaryEntry
import pt.ipt.mystreaks.data.model.Task
import pt.ipt.mystreaks.data.repository.DiaryRepository
import pt.ipt.mystreaks.data.repository.TaskRepository
import pt.ipt.mystreaks.utils.DateHelper
import java.time.LocalDate

class DiaryViewModel(
    private val diaryRepository: DiaryRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _selectedDate = MutableLiveData<LocalDate>(LocalDate.now())
    val selectedDate: LiveData<LocalDate> = _selectedDate

    val currentDayEntry: LiveData<DiaryEntry?> = _selectedDate.switchMap { date ->
        val startMillis = DateHelper.startOfDayMillis(date)
        diaryRepository.getEntryByDateFlow(startMillis).asLiveData()
    }

    val currentDayTasks: LiveData<List<Task>> = _selectedDate.switchMap { date ->
        val startMillis = DateHelper.startOfDayMillis(date)
        val endMillis = DateHelper.endOfDayMillis(date)
        taskRepository.getTasksByDateRange(startMillis, endMillis).asLiveData()
    }

    val datesWithNotes: LiveData<Set<LocalDate>> = diaryRepository.allEntries
        .map { entries ->
            entries.filter { it.content.isNotBlank() }
                .map { DateHelper.toLocalDate(it.dateMillis) }
                .toSet()
        }.asLiveData()

    val datesWithTasks: LiveData<Set<LocalDate>> = taskRepository.allTasksWithDueDate
        .map { tasks ->
            tasks.mapNotNull { it.dueDate }
                .map { DateHelper.toLocalDate(it) }
                .toSet()
        }.asLiveData()

    fun selectDate(date: LocalDate) {
        if (_selectedDate.value != date) {
            _selectedDate.value = date
        }
    }

    fun saveNote(date: LocalDate, content: String) = viewModelScope.launch {
        val dayMillis = DateHelper.startOfDayMillis(date)
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            val existing = diaryRepository.getEntryByDate(dayMillis)
            if (existing != null) {
                diaryRepository.delete(existing)
            }
        } else {
            diaryRepository.upsert(
                DiaryEntry(
                    dateMillis = dayMillis,
                    content = trimmed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun insertTaskAndGetId(task: Task): Long = taskRepository.insert(task)
    fun updateTask(task: Task) = viewModelScope.launch { taskRepository.update(task) }
    fun deleteTask(task: Task) = viewModelScope.launch { taskRepository.delete(task) }
}

class DiaryViewModelFactory(
    private val diaryRepository: DiaryRepository,
    private val taskRepository: TaskRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DiaryViewModel(diaryRepository, taskRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
