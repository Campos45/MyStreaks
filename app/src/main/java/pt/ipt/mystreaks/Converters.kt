package pt.ipt.mystreaks

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromSubTaskList(value: List<SubTask>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toSubTaskList(value: String): List<SubTask> {
        val listType = object : TypeToken<List<SubTask>>() {}.type
        return Gson().fromJson(value, listType)
    }
}