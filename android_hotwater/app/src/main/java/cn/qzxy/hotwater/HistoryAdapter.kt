package cn.qzxy.hotwater

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter : ListAdapter<OperationHistory, HistoryAdapter.ViewHolder>(HistoryDiffCallback()) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.historyIcon)
        val typeText: TextView = itemView.findViewById(R.id.historyType)
        val timeText: TextView = itemView.findViewById(R.id.historyTime)
        val statusText: TextView = itemView.findViewById(R.id.historyStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_operation_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val history = getItem(position)

        // 设置图标
        holder.icon.text = if (history.success) "✓" else "✗"
        holder.icon.setBackgroundResource(
            if (history.success) R.drawable.widget_status_success
            else R.drawable.widget_status_error
        )

        // 设置类型
        holder.typeText.text = history.type.label

        // 设置时间
        holder.timeText.text = history.getFormattedDate()

        // 设置状态
        holder.statusText.text = history.getStatusText()
        holder.statusText.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (history.success) R.color.accent else R.color.text_secondary
            )
        )
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<OperationHistory>() {
        override fun areItemsTheSame(oldItem: OperationHistory, newItem: OperationHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OperationHistory, newItem: OperationHistory): Boolean {
            return oldItem == newItem
        }
    }
}
