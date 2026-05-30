package pt.ipt.mystreaks.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import pt.ipt.mystreaks.data.model.StreakRecord
import pt.ipt.mystreaks.data.model.SubTask

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromSubTaskList(value: List<SubTask>): String = gson.toJson(value)

    @TypeConverter
    fun toSubTaskList(value: String): List<SubTask> {
        val listType = object : TypeToken<List<SubTask>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    // NOVO: Conversores para o Histórico de Streaks
    @TypeConverter
    fun fromStreakRecordList(value: List<StreakRecord>): String = gson.toJson(value)

    @TypeConverter
    fun toStreakRecordList(value: String): List<StreakRecord> {
        val listType = object : TypeToken<List<StreakRecord>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    // Adiciona isto dentro da tua classe Converters
    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> {
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }


    // NOVO: Ensina o Room a lidar com a List<Int> dos notifyDays
    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val listType = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
}