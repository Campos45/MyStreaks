package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.ui.lists.EditListActivity
import pt.ipt.mystreaks.ui.main.MainActivity
import pt.ipt.mystreaks.utils.DateHelper
import java.time.LocalDate

class NavigationAndShortcutsTest {

    @Test
    fun navigationTabIndices_matchBottomNavOrder() {
        assertEquals(0, MainActivity.TAB_STREAKS)
        assertEquals(1, MainActivity.TAB_TASKS)
        assertEquals(2, MainActivity.TAB_DIARY)
        assertEquals(3, MainActivity.TAB_LISTS)
        assertEquals(4, MainActivity.TAB_SETTINGS)
    }

    @Test
    fun shortcutActionConstants_areDefinedCorrectly() {
        assertEquals("EXTRA_OPEN_TAB", MainActivity.EXTRA_OPEN_TAB)
        assertEquals("EXTRA_ACTION", MainActivity.EXTRA_ACTION)
        assertEquals("ACTION_NEW_STREAK", MainActivity.ACTION_NEW_STREAK)
        assertEquals("ACTION_NEW_TASK", MainActivity.ACTION_NEW_TASK)
        assertEquals("ACTION_NEW_LIST", MainActivity.ACTION_NEW_LIST)
    }

    @Test
    fun editListActivity_extraConstantIsDefined() {
        assertEquals("EXTRA_LIST_ID", EditListActivity.EXTRA_LIST_ID)
    }

    @Test
    fun myList_copyForFullPageEditing_preservesContentAndMetadata() {
        val original = MyList(
            id = 5,
            name = "Lista de Compras",
            content = "<b>Pão, Leite, Fruta</b>",
            tag = "Casa",
            backgroundColor = "#FFFBEB",
            isPinned = true,
            isArchived = false
        )

        val updated = original.copy(
            name = "Lista de Compras Atualizada",
            content = "<b>Pão, Leite, Fruta, Café</b>",
            backgroundColor = "#ECFDF5",
            isPinned = false
        )

        assertEquals(5, updated.id)
        assertEquals("Lista de Compras Atualizada", updated.name)
        assertEquals("<b>Pão, Leite, Fruta, Café</b>", updated.content)
        assertEquals("Casa", updated.tag)
        assertEquals("#ECFDF5", updated.backgroundColor)
        assertFalse(updated.isPinned)
        assertFalse(updated.isArchived)
    }

    @Test
    fun widgetDateFormatting_isTodayAccurate() {
        val today = LocalDate.now()
        val todayMillis = DateHelper.startOfDayMillis(today)
        assertTrue(DateHelper.isToday(todayMillis))

        val yesterdayMillis = DateHelper.startOfDayMillis(today.minusDays(1))
        assertFalse(DateHelper.isToday(yesterdayMillis))
    }
}
