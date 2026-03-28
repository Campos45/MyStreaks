package pt.ipt.mystreaks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs_table")
data class AppLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(), // Regista o milissegundo exato
    val type: String, // Ex: "STREAK", "TAREFA"
    val message: String // Ex: "Marcou a atividade Beber Água"
)