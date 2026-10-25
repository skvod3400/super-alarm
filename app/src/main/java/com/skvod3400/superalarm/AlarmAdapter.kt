package com.skvod3400.superalarm

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.skvod3400.superalarm.databinding.ItemAlarmBinding

class AlarmAdapter(
    private val onClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onLongClick: (Alarm) -> Unit,
) : RecyclerView.Adapter<AlarmAdapter.Holder>() {

    private var alarms: List<Alarm> = emptyList()

    class Holder(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Alarm>) {
        alarms = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = alarms.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val alarm = alarms[position]
        with(holder.binding) {
            time.text = alarm.timeText
            days.text = alarm.daysText
            label.text = alarm.label
            label.isVisible = alarm.label.isNotBlank()
            root.alpha = if (alarm.enabled) 1f else 0.6f

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = alarm.enabled
            toggle.setOnCheckedChangeListener { _, checked -> onToggle(alarm, checked) }

            root.setOnClickListener { onClick(alarm) }
            root.setOnLongClickListener {
                onLongClick(alarm)
                true
            }
        }
    }
}
