package pt.ipt.mystreaks.ui.streak

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pt.ipt.mystreaks.R

data class CalendarDay(val dayNumber: String, val isCompleted: Boolean, val isCurrentMonth: Boolean)

class CalendarAdapter(private val days: List<CalendarDay>) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        holder.tvDay.text = day.dayNumber

        if (!day.isCurrentMonth) {
            holder.tvDay.visibility = View.INVISIBLE // Esconde dias de outros meses para ficar bonito
        } else {
            holder.tvDay.visibility = View.VISIBLE
            if (day.isCompleted) {
                // Pinta o quadrado de verde (Heatmap!) e o texto de branco
                holder.tvDay.setBackgroundColor(Color.parseColor("#4CAF50"))
                holder.tvDay.setTextColor(Color.WHITE)
            } else {
                // Fundo normal
                holder.tvDay.setBackgroundResource(R.drawable.widget_background)
                holder.tvDay.setTextColor(Color.parseColor("#333333"))
            }
        }
    }

    override fun getItemCount() = days.size
}