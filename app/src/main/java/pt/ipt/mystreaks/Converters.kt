package pt.ipt.mystreaks

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromSubTaskList(value: List<SubTask>): String = Gson().toJson(value)

    @TypeConverter
    fun toSubTaskList(value: String): List<SubTask> {
        val listType = object : TypeToken<List<SubTask>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    // NOVO: Conversores para o Histórico de Streaks
    @TypeConverter
    fun fromStreakRecordList(value: List<StreakRecord>): String = Gson().toJson(value)

    @TypeConverter
    fun toStreakRecordList(value: String): List<StreakRecord> {
        val listType = object : TypeToken<List<StreakRecord>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    // Adiciona isto dentro da tua classe Converters
    @androidx.room.TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return com.google.gson.Gson().toJson(value)
    }

    @androidx.room.TypeConverter
    fun toLongList(value: String): List<Long> {
        val listType = object : com.google.gson.reflect.TypeToken<List<Long>>() {}.type
        return com.google.gson.Gson().fromJson(value, listType) ?: emptyList()
    }
}