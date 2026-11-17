package pt.ipt.mystreaks.ui.diary

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import pt.ipt.mystreaks.R
import java.time.LocalDate
import java.time.YearMonth

data class DiaryDayCell(
    val date: LocalDate? // null means leading blank cell
)

class DiaryCalendarAdapter(
    private val onDateSelected: (LocalDate) -> Unit
) : RecyclerView.Adapter<DiaryCalendarAdapter.DayViewHolder>() {

    private var cells: List<DiaryDayCell> = emptyList()
    private var selectedDate: LocalDate = LocalDate.now()
    private var datesWithNotes: Set<LocalDate> = emptySet()
    private var datesWithTasks: Set<LocalDate> = emptySet()

    fun setMonth(month: YearMonth) {
        val firstOfMonth = month.atDay(1)
        // Monday = 1 .. Sunday = 7 -> offset 0..6
        val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()

        val list = mutableListOf<DiaryDayCell>()
        repeat(leadingBlanks) { list.add(DiaryDayCell(null)) }
        for (day in 1..daysInMonth) {
            list.add(DiaryDayCell(month.atDay(day)))
        }
        cells = list
        notifyDataSetChanged()
    }

    fun setSelectedDate(date: LocalDate) {
        selectedDate = date
        notifyDataSetChanged()
    }

    fun setIndicators(notesDates: Set<LocalDate>, tasksDates: Set<LocalDate>) {
        datesWithNotes = notesDates
        datesWithTasks = tasksDates
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_diary_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(cells[position])
    }

    override fun getItemCount(): Int = cells.size

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
        private val dotTask: View = itemView.findViewById(R.id.dotTask)
        private val dotNote: View = itemView.findViewById(R.id.dotNote)

        fun bind(cell: DiaryDayCell) {
            val date = cell.date
            if (date == null) {
                tvDayNumber.text = ""
                tvDayNumber.background = null
                dotTask.visibility = View.INVISIBLE
                dotNote.visibility = View.INVISIBLE
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                return
            }

            itemView.isClickable = true
            tvDayNumber.text = date.dayOfMonth.toString()

            val today = LocalDate.now()
            val isSelected = date == selectedDate
            val isToday = date == today

            when {
                isSelected -> {
                    tvDayNumber.setBackgroundResource(R.drawable.bg_diary_day_selected)
                    tvDayNumber.setTextColor(Color.WHITE)
                }
                isToday -> {
                    tvDayNumber.setBackgroundResource(R.drawable.bg_diary_day_today)
                    tvDayNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary))
                }
                else -> {
                    tvDayNumber.background = null
                    tvDayNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                }
            }

            dotTask.visibility = if (datesWithTasks.contains(date)) View.VISIBLE else View.GONE
            dotNote.visibility = if (datesWithNotes.contains(date)) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onDateSelected(date) }
        }
    }
}
