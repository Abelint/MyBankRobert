package com.robert.mybank.activityproduct

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.robert.mybank.R
import com.robert.mybank.server.CategoryDto

class CategoriesAdapter(
    private val getCategories: () -> List<CategoryDto>,
    private val getColor: (index: Int) -> Int,
    private val onCategoryClick: (CategoryDto) -> Unit,
    private val onCategoryLongClick: (CategoryDto) -> Unit,
    private val onIncomeClick: () -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_CATEGORY = 1
    private val TYPE_ADD = 2
    private val TYPE_EMPTY = 3
    private val TYPE_INCOME = 4

    override fun getItemCount(): Int = getCategories().size + 3

    override fun getItemViewType(position: Int): Int {
        val n = getCategories().size
        return when (position) {
            in 0 until n -> TYPE_CATEGORY
            n -> TYPE_ADD
            n + 1 -> TYPE_EMPTY
            n + 2 -> TYPE_INCOME
            else -> TYPE_CATEGORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ADD ->
                AddVH(inflater.inflate(R.layout.item_add_tile, parent, false))

            TYPE_EMPTY ->
                EmptyVH(inflater.inflate(R.layout.item_empty_tile, parent, false))

            TYPE_INCOME ->
                IncomeVH(inflater.inflate(R.layout.item_category_tile, parent, false))

            else ->
                CategoryVH(inflater.inflate(R.layout.item_category_tile, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {

            is CategoryVH -> {
                val item = getCategories()[position]
                holder.tvName.text = item.name
                holder.card.setCardBackgroundColor(getColor(position))
                holder.itemView.setOnClickListener { onCategoryClick(item) }
                holder.itemView.setOnLongClickListener {
                    onCategoryLongClick(item)
                    true
                }
            }

            is AddVH -> {
                holder.itemView.setOnClickListener { onAddClick() }
            }

            is EmptyVH -> {
                // ничего не делаем
            }

            is IncomeVH -> {
                holder.tvName.text = "Доход"
                holder.card.setCardBackgroundColor(0xFFFFD54F.toInt()) // жёлтый
                holder.itemView.setOnClickListener { onIncomeClick() }
                holder.itemView.setOnLongClickListener { true }
            }
        }
    }

    class CategoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
    }

    class IncomeVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
    }

    class AddVH(itemView: View) : RecyclerView.ViewHolder(itemView)

    class EmptyVH(itemView: View) : RecyclerView.ViewHolder(itemView)
}
