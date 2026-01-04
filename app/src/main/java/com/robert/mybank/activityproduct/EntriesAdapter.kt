package com.robert.mybank.activityproduct

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.robert.mybank.R

class EntriesAdapter(
    private val items: MutableList<Entry>,
    private val onMaybeNeedNewRow: () -> Unit
) : RecyclerView.Adapter<EntriesAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_entry_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.clearWatchers()

        holder.etName.setText(item.name)
        holder.etValue.setText(item.value)

        holder.nameWatcher = simpleWatcher { text ->
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                items[pos].name = text
                if (pos == items.lastIndex) onMaybeNeedNewRow()
            }
        }

        holder.valueWatcher = simpleWatcher { text ->
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                items[pos].value = text
                if (pos == items.lastIndex) onMaybeNeedNewRow()
            }
        }

        holder.etName.addTextChangedListener(holder.nameWatcher)
        holder.etValue.addTextChangedListener(holder.valueWatcher)
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val etName: EditText = itemView.findViewById(R.id.etName)
        val etValue: EditText = itemView.findViewById(R.id.etValue)

        var nameWatcher: TextWatcher? = null
        var valueWatcher: TextWatcher? = null

        fun clearWatchers() {
            nameWatcher?.let { etName.removeTextChangedListener(it) }
            valueWatcher?.let { etValue.removeTextChangedListener(it) }
            nameWatcher = null
            valueWatcher = null
        }
    }

    private fun simpleWatcher(onChanged: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s?.toString()?.trim().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        }
    }
}