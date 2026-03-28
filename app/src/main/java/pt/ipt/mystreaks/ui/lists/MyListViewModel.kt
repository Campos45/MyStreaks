package pt.ipt.mystreaks.ui.lists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.repository.MyListRepository

class MyListViewModel(private val repository: MyListRepository) : ViewModel() {
    val activeLists: LiveData<List<MyList>> = repository.activeLists.asLiveData()
    val archivedLists: LiveData<List<MyList>> = repository.archivedLists.asLiveData()

    fun insert(myList: MyList) = viewModelScope.launch {
        repository.insert(myList)
    }

    fun update(myList: MyList) = viewModelScope.launch {
        repository.update(myList)
    }

    fun delete(myList: MyList) = viewModelScope.launch {
        repository.delete(myList)
    }
}

class MyListViewModelFactory(private val repository: MyListRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}