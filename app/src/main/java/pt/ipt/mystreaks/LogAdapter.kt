package pt.ipt.mystreaks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ipt.mystreaks.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : ListAdapter<AppLog, LogAdapter.LogViewHolder>(LogComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: AppLog) {
            binding.tvLogType.text = log.type
            binding.tvLogMessage.text = log.message

            // Formatar a data para algo legível (ex: 12/10/2023 15:30)
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvLogDate.text = sdf.format(Date(log.timestamp))
        }
    }

    class LogComparator : DiffUtil.ItemCallback<AppLog>() {
        override fun areItemsTheSame(oldItem: AppLog, newItem: AppLog) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppLog, newItem: AppLog) = oldItem == newItem
    }
}