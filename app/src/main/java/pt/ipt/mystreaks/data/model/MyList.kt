package pt.ipt.mystreaks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lists_table")
data class MyList(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    var content: String = "",
    var tag: String? = null,
    var backgroundColor: String? = null,

    // NOVO: Define se a lista está afixada no topo
    var isPinned: Boolean = false,

    var orderIndex: Int = 0,
    var isArchived: Boolean = false
)