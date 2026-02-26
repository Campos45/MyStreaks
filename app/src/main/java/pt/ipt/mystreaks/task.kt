package pt.ipt.mystreaks

import androidx.room.Entity
import androidx.room.PrimaryKey

// Estrutura de um sub-passo
data class SubTask(
    val name: String,
    var isCompleted: Boolean = false
)

// Estrutura da Tarefa Principal
@Entity(tableName = "tasks_table")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    var isCompleted: Boolean = false,
    var completionDate: Long? = null, // Regista o dia em que foi concluída
    var subTasks: List<SubTask> = emptyList(), // A lista de sub-passos

    // NOVO:
    var tag: String? = null,
    var orderIndex: Int = 0
)