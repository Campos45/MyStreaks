package pt.ipt.mystreaks.data.repository

import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.dao.MyListDao
import pt.ipt.mystreaks.data.model.MyList

class MyListRepository(private val myListDao: MyListDao) {
    val activeLists: Flow<List<MyList>> = myListDao.getActiveLists()
    val archivedLists: Flow<List<MyList>> = myListDao.getArchivedLists()

    suspend fun insert(myList: MyList) {
        myListDao.insert(myList)
    }

    suspend fun update(myList: MyList) {
        myListDao.update(myList)
    }

    suspend fun delete(myList: MyList) {
        myListDao.delete(myList)
    }
}