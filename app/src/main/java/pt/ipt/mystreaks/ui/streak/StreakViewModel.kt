package pt.ipt.mystreaks.ui.streak

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.repository.StreakRepository

class StreakViewModel(private val repository: StreakRepository) : ViewModel() {

    val activeStreaks: LiveData<List<Streak>> = repository.activeStreaks.asLiveData()
    val archivedStreaks: LiveData<List<Streak>> = repository.archivedStreaks.asLiveData()

    fun insert(streak: Streak) = viewModelScope.launch {
        repository.insert(streak)
    }

    fun update(streak: Streak) = viewModelScope.launch {
        repository.update(streak)
    }

    fun delete(streak: Streak) = viewModelScope.launch {
        repository.delete(streak)
    }
}

class StreakViewModelFactory(private val repository: StreakRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreakViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StreakViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}