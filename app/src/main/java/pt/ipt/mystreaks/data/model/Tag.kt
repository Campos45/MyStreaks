package pt.ipt.mystreaks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: String,
    val type: String
) {
    // ESTA LINHA É A CHAVE: Diz ao Android para mostrar apenas o nome no Dropdown
    override fun toString(): String {
        return name
    }
}