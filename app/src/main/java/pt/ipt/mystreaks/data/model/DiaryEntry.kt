package pt.ipt.mystreaks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey val dateMillis: Long, // Normalized start of day in local timezone millis
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
