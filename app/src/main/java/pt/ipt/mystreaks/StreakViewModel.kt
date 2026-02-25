package pt.ipt.mystreaks

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class StreakViewModel(private val repository: StreakRepository) : ViewModel() {

    // Converte o Flow do Repositório para LiveData para ser mais fácil de usar no Ecrã (MainActivity)
    val allStreaks: LiveData<List<Streak>> = repository.allStreaks.asLiveData()

    // Usamos o viewModelScope para que as tarefas de gravar na base de dados
    // rodem em segundo plano sem bloquear/congelar a interface
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

// Esta classe serve apenas para dizer ao Android como criar o nosso StreakViewModel com o Repositório
class StreakViewModelFactory(private val repository: StreakRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreakViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StreakViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}