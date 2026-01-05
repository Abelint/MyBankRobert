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
import com.robert.mybank.server.*
import kotlinx.coroutines.*

class ActivityProduct : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView

    private val categories = mutableListOf<CategoryDto>()

    private val palette = listOf(
        0xFF007AFF.toInt(),
        0xFFFF3B30.toInt(),
        0xFF34C759.toInt(),
        0xFFFF9500.toInt(),
        0xFFAF52DE.toInt(),
        0xFF00C7BE.toInt()
    )

    private lateinit var categoriesAdapter: CategoriesAdapter
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        val date = intent.getStringExtra("date") ?: "не выбрано"
        Toast.makeText(this, "Дата: $date", Toast.LENGTH_SHORT).show()

        rvCategories = findViewById(R.id.rvCategories)

        categoriesAdapter = CategoriesAdapter(
            getCategories = { categories },
            getColor = { index -> palette[index % palette.size] },
            onCategoryClick = { cat -> openEntriesSheet(cat) },
            onCategoryLongClick = { cat -> showDeleteDialog(cat) },
            onAddClick = { showAddCategoryDialog() }
        )

        rvCategories.layoutManager = GridLayoutManager(this, 2)
        rvCategories.adapter = categoriesAdapter

        loadCategories()
    }
    private fun showDeleteDialog(cat: CategoryDto) {
        AlertDialog.Builder(this)
            .setTitle("Удаление категории")
            .setMessage("Хотите удалить категорию \"${cat.name}\"?")
            .setNegativeButton("Нет", null)
            .setPositiveButton("Да") { _, _ ->
                unlinkCategoryOnServer(cat)
            }
            .show()
    }

    private fun unlinkCategoryOnServer(cat: CategoryDto) {
        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.api.unlinkCategory(bearer(), cat.id) // DELETE /categories/{id}
                }

                if (res.ok) {
                    // Быстро обновим UI локально
                    val idx = categories.indexOfFirst { it.id == cat.id }
                    if (idx >= 0) {
                        categories.removeAt(idx)
                        categoriesAdapter.notifyDataSetChanged()
                    }
                    Toast.makeText(this@ActivityProduct, "Категория удалена", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ActivityProduct, "Ошибка: ${res.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityProduct, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bearer(): String {
        val token = TokenStore.token(this)
        return "Bearer ${token ?: ""}"
    }

    private fun loadCategories() {
        val token = TokenStore.token(this)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Нет токена. Авторизуйтесь заново.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.api.getCategories(bearer())
                }

                if (res.ok) {
                    categories.clear()
                    categories.addAll(res.items)
                    categoriesAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this@ActivityProduct, "Ошибка: ${res.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityProduct, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply { hint = "Название категории" }

        AlertDialog.Builder(this)
            .setTitle("Добавление категории")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Добавить") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    createCategoryOnServer(text)
                } else {
                    Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }


    private fun openEntriesSheet(category: CategoryDto) {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.bottom_sheet_entries)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvSheetTitle)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvEntries)

        tvTitle?.text = category.name

        if (rv == null) {
            dialog.show()
            return
        }

        val day = intent.getStringExtra("date") ?: "" // ожидаем "YYYY-MM-DD"
        if (day.isBlank()) {
            Toast.makeText(this, "Нет даты", Toast.LENGTH_SHORT).show()
            dialog.show()
            return
        }

        val entries = mutableListOf<Entry>()
        rv.layoutManager = LinearLayoutManager(this)

        // сколько записей было загружено с сервера (их НЕ отправляем повторно)
        var loadedCount = 0

        lateinit var adapter: EntriesAdapter
        adapter = EntriesAdapter(entries) {
            val last = entries.lastOrNull()
            val lastFilled = last != null && last.name.isNotBlank() && last.value.isNotBlank()
            if (lastFilled) {
                entries.add(Entry("", ""))
                adapter.notifyItemInserted(entries.size - 1)
            }
        }
        rv.adapter = adapter

        // 1) ЗАГРУЗКА существующих записей
        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.api.getRecords(bearer(), day)
                }

                if (!res.ok) {
                    Toast.makeText(this@ActivityProduct, "Ошибка загрузки: ${res.error}", Toast.LENGTH_SHORT).show()
                } else {
                    val list = res.items
                        .filter { it.category_id == category.id }
                        .sortedBy { it.date } // можно как хочешь

                    entries.clear()
                    // показываем существующие
                    list.forEach { r ->
                        entries.add(Entry(r.product_name, r.price.toString()))
                    }
                    loadedCount = entries.size

                    // всегда добавим пустую строку в конец
                    entries.add(Entry("", ""))

                    adapter.notifyDataSetChanged()
                }

            } catch (e: Exception) {
                Toast.makeText(this@ActivityProduct, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
                // даже если сеть упала — дадим вводить локально
                if (entries.isEmpty()) {
                    entries.add(Entry("", ""))
                    adapter.notifyDataSetChanged()
                }
            }
        }

        // 2) СОХРАНЕНИЕ новых строк при закрытии
        dialog.setOnDismissListener {
            val newRows = entries
                .drop(loadedCount) // только добавленные после загрузки
                .filter { it.name.isNotBlank() && it.value.isNotBlank() }

            if (newRows.isEmpty()) return@setOnDismissListener

            val items = newRows.mapNotNull { row ->
                val price = row.value.replace(",", ".").toDoubleOrNull() ?: return@mapNotNull null
                com.robert.mybank.server.RecordItemIn(
                    category_id = category.id,
                    name = row.name.trim(),
                    price = price
                )
            }

            if (items.isEmpty()) return@setOnDismissListener

            val dateTime = buildDateTimeForDay(day) // "YYYY-MM-DD HH:MM:SS"

            uiScope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        ApiClient.api.createRecords(
                            bearer(),
                            com.robert.mybank.server.CreateRecordsRequest(
                                date = dateTime,
                                items = items
                            )
                        )
                    }

                    if (res.ok) {
                        Toast.makeText(this@ActivityProduct, "Сохранено: ${res.records.size}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ActivityProduct, "Ошибка сохранения: ${res.error}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ActivityProduct, "Сеть/сервер (сохранение): ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun buildDateTimeForDay(day: String): String {
        // day: "YYYY-MM-DD"
        val now = java.util.Date()
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(now)
        return "$day $time"
    }
    private fun createCategoryOnServer(name: String) {
        val token = TokenStore.token(this)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Нет токена. Авторизуйтесь заново.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.api.createCategory(bearer(), CreateCategoryRequest(name))
                }

                if (res.ok) {
                    Toast.makeText(this@ActivityProduct, "Категория добавлена", Toast.LENGTH_SHORT).show()
                    // Надежно: перезагружаем список с сервера
                    loadCategories()
                } else {
                    Toast.makeText(this@ActivityProduct, "Ошибка: ${res.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityProduct, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }
}
