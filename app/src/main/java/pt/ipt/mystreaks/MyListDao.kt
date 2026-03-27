package pt.ipt.mystreaks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MyListDao {

    @Query("SELECT * FROM lists_table")
    fun getAllListsSync(): List<MyList>

    // Traz as listas normais (Afixadas primeiro, depois pela ordem de criação/arrasto)
    @Query("SELECT * FROM lists_table WHERE isArchived = 0 ORDER BY isPinned DESC, orderIndex ASC")
    fun getActiveLists(): Flow<List<MyList>>

    // Traz as listas arquivadas
    @Query("SELECT * FROM lists_table WHERE isArchived = 1 ORDER BY orderIndex ASC")
    fun getArchivedLists(): Flow<List<MyList>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(myList: MyList)

    @Update
    suspend fun update(myList: MyList)

    @Delete
    suspend fun delete(myList: MyList)



}
