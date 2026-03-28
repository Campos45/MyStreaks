package pt.ipt.mystreaks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pt.ipt.mystreaks.data.model.Tag

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    fun getAllTagsSyncList(): List<Tag>

    // Mudámos de tags_table para tags
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)
}