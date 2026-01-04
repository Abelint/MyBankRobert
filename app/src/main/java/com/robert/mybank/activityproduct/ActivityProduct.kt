package com.robert.mybank.activityproduct

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.robert.mybank.R

class ActivityProduct : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView

    private val categories = mutableListOf("Продукты", "Коммуналка", "Прочие")

    // Палитра цветов (по кругу)
    private val palette = listOf(
        0xFF007AFF.toInt(), // синий
        0xFFFF3B30.toInt(), // красный
        0xFF34C759.toInt(), // зелёный
        0xFFFF9500.toInt(), // оранжевый
        0xFFAF52DE.toInt(), // фиолетовый
        0xFF00C7BE.toInt()  // бирюзовый
    )

    private lateinit var categoriesAdapter: CategoriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        val date = intent.getStringExtra("date") ?: "не выбрано"
        Toast.makeText(this, "Дата: $date", Toast.LENGTH_SHORT).show()

        rvCategories = findViewById(R.id.rvCategories)

        categoriesAdapter = CategoriesAdapter(
            getCategories = { categories },
            getColor = { index -> palette[index % palette.size] },
            onCategoryClick = { name -> openEntriesSheet(name) },
            onAddClick = { showAddCategoryDialog() }
        )

        rvCategories.layoutManager = GridLayoutManager(this, 2)
        rvCategories.adapter = categoriesAdapter
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply {
            hint = "Название категории"
        }

        AlertDialog.Builder(this)
            .setTitle("Добавление категории")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Добавить") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    categories.add(text)
                    categoriesAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun openEntriesSheet(categoryName: String) {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.bottom_sheet_entries)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvSheetTitle)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvEntries)

        tvTitle?.text = categoryName

        if (rv != null) {
            val entries = mutableListOf(Entry("", "")) // минимум 1 пустая строка

            lateinit var adapter: EntriesAdapter

            adapter = EntriesAdapter(entries) {
                val last = entries.lastOrNull()
                val lastFilled = last != null &&
                        last.name.isNotBlank() &&
                        last.value.isNotBlank()

                if (lastFilled) {
                    entries.add(Entry("", ""))
                    adapter.notifyItemInserted(entries.size - 1)
                }
            }

            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = adapter
        }

        dialog.show()
    }

}