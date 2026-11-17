package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.data.Converters
import pt.ipt.mystreaks.data.model.StreakRecord
import pt.ipt.mystreaks.data.model.SubTask

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun subTaskList_serializationAndDeserialization_preservesData() {
        val original = listOf(
            SubTask("Comprar leite", isCompleted = false),
            SubTask("Pagar faturas", isCompleted = true),
            SubTask("Texto com \"aspas\" e acentuação: café & açúcar", isCompleted = false)
        )

        val json = converters.fromSubTaskList(original)
        val deserialized = converters.toSubTaskList(json)

        assertEquals(original.size, deserialized.size)
        assertEquals(original[0].name, deserialized[0].name)
        assertEquals(original[1].isCompleted, deserialized[1].isCompleted)
        assertEquals(original[2].name, deserialized[2].name)
    }

    @Test
    fun subTaskList_emptyList_handledSafely() {
        val json = converters.fromSubTaskList(emptyList())
        val deserialized = converters.toSubTaskList(json)
        assertTrue(deserialized.isEmpty())
    }

    @Test
    fun streakRecordList_serializationAndDeserialization_preservesData() {
        val original = listOf(
            StreakRecord(count = 10, startDate = 1000L, endDate = 2000L),
            StreakRecord(count = 35, startDate = 3000L, endDate = 5000L)
        )

        val json = converters.fromStreakRecordList(original)
        val deserialized = converters.toStreakRecordList(json)

        assertEquals(original.size, deserialized.size)
        assertEquals(35, deserialized[1].count)
    }

    @Test
    fun longList_handlesNullAndEmpty() {
        val json = converters.fromLongList(listOf(100L, 200L, 300L))
        val deserialized = converters.toLongList(json)
        assertEquals(3, deserialized.size)
        assertEquals(200L, deserialized[1])
    }

    @Test
    fun intList_handlesDaysList() {
        val original = listOf(1, 15, 30)
        val json = converters.fromIntList(original)
        val deserialized = converters.toIntList(json)
        assertEquals(original, deserialized)
    }
}
