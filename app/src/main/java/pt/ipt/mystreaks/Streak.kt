package pt.ipt.mystreaks

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streaks_table")
data class Streak(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String, // Vamos usar "D" (Diária), "S" (Semanal), "M" (Mensal)
    var count: Int = 0, // O número atual da streak
    var isCompleted: Boolean = false, // Estado da checkbox no ciclo atual
    var lastResetDate: Long = System.currentTimeMillis() // Para o sistema saber quando deve fazer o reset das checkboxes/prazos
)