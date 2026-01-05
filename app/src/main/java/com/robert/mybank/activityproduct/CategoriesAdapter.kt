package com.robert.mybank.activityproduct

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.robert.mybank.R
import com.robert.mybank.server.*

class CategoriesAdapter(
    private val getCategories: () -> List<CategoryDto>,
    private val getColor: (index: Int) -> Int,
    private val onCategoryClick: (CategoryDto) -> Unit,
    private val onCategoryLongClick: (CategoryDto) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_CATEGORY = 1
    private val TYPE_ADD = 2

    override fun getItemCount(): Int = getCategories().size + 1 // + плитка "+"

    override fun getItemViewType(position: Int): Int {
        return if (position == getCategories().size) TYPE_ADD else TYPE_CATEGORY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ADD) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_add_tile, parent, false)
            AddVH(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category_tile, parent, false)
            CategoryVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddVH) {
            holder.itemView.setOnClickListener { onAddClick() }
        } else if (holder is CategoryVH) {
            val item = getCategories()[position]
            holder.tvName.text = item.name
            holder.card.setCardBackgroundColor(getColor(position))
            holder.itemView.setOnClickListener { onCategoryClick(item) }
            holder.itemView.setOnLongClickListener {
                onCategoryLongClick(item)
                true
            }
        }
    }

    class CategoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
    }

    class AddVH(itemView: View) : RecyclerView.ViewHolder(itemView)
}