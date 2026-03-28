package pt.ipt.mystreaks.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.repository.TagRepository

class TagViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TagRepository
    val allTags: LiveData<List<Tag>>

    init {
        // Liga-se à base de dados que acabaste de atualizar
        val tagDao = AppDatabase.Companion.getDatabase(application).tagDao()
        repository = TagRepository(tagDao)
        allTags = repository.allTags.asLiveData()
    }

    fun insert(tag: Tag) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(tag)
    }

    fun delete(tag: Tag) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(tag)
    }
}