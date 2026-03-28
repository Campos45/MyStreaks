package pt.ipt.mystreaks.data.repository

import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.dao.TagDao
import pt.ipt.mystreaks.data.model.Tag

class TagRepository(private val tagDao: TagDao) {

    // Fica a "escutar" todas as tags em tempo real
    val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    // Envia uma tag nova para a base de dados
    suspend fun insert(tag: Tag) {
        tagDao.insert(tag)
    }

    // Apaga uma tag
    suspend fun delete(tag: Tag) {
        tagDao.delete(tag)
    }
}