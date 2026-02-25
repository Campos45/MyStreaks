package pt.ipt.mystreaks

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streaks_table")
data class Streak(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String,
    var count: Int = 0,
    var isCompleted: Boolean = false,
    var lastResetDate: Long = System.currentTimeMillis(),
    var isArchived: Boolean = false // NOVO: Controla se está no ecrã principal ou no arquivo
)