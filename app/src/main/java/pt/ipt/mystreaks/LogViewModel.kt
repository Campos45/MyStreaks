package pt.ipt.mystreaks

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class LogViewModel(private val repository: LogRepository) : ViewModel() {

    // NOVO: Expõe a lista de logs para o ecrã
    val allLogs: LiveData<List<AppLog>> = repository.allLogs.asLiveData()

    fun registrarAcao(type: String, message: String) = viewModelScope.launch {
        repository.insertLog(AppLog(type = type, message = message))
    }
}

class LogViewModelFactory(private val repository: LogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LogViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}