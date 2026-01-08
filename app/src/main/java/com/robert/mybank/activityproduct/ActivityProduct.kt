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
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.Normalizer
import java.util.Locale


class ActivityProduct : AppCompatActivity() {
    // ===== Voice =====
    private var isListening = false
    private var restartPosted = false
    private var isUiModalOpen = false // bottomSheet/alert открыт -> не слушаем
    private val restartDelayMs = 1500L
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null

    private val voiceHandler = Handler(Looper.getMainLooper())


    private var lastVoiceTriggerAt = 0L
    private val voiceCooldownMs = 1200L
    private var voiceEnabled = true

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
            onIncomeClick = { openIncomeSheet() },
            onAddClick = { showAddCategoryDialog() }
        )
        rvCategories.layoutManager = GridLayoutManager(this, 2)
        rvCategories.adapter = categoriesAdapter

        loadCategories()
        setupVoice()
    }
    override fun onResume() {
        super.onResume()
        voiceEnabled = true
        scheduleRestartVoice(1200L) // не 800, чуть больше пауза = меньше “пиликанья”
    }

    override fun onPause() {
        super.onPause()
        stopVoiceListening()
    }

    private fun setupVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Распознавание речи недоступно", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false) // ВАЖНО: partial выключаем
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false

                // эти ошибки встречаются постоянно — не надо “долбить” сервис
                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 3000L
                    SpeechRecognizer.ERROR_NO_MATCH -> 2000L
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 2000L
                    else -> restartDelayMs
                }
                scheduleRestartVoice(delay)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                handleVoiceResults(results)

                // после успешного распознавания даём паузу, чтобы не было “кликов”
                scheduleRestartVoice(1800L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // ничего (иначе будет дёргаться часто)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    private fun startVoiceListening() {
        if (!voiceEnabled) return
        if (isUiModalOpen) return
        if (isListening) return
        Log.d("VOICE", "START in ActivityProduct")

        val sr = speechRecognizer ?: return
        val intent = recognizerIntent ?: return

        try {
            // ВАЖНО: сначала cancel, иначе на некоторых девайсах будет двойной старт/бип
            sr.cancel()

            isListening = true
            sr.startListening(intent)
        } catch (_: Throwable) {
            isListening = false
            scheduleRestartVoice(2000L)
        }
    }


    private fun stopVoiceListening() {
        voiceHandler.removeCallbacksAndMessages(null)
        restartPosted = false

        try {
            isListening = false
            speechRecognizer?.cancel() // только cancel, stopListening иногда даёт лишний звук
        } catch (_: Throwable) {}
    }


    private fun scheduleRestartVoice(delayMs: Long = restartDelayMs) {
        if (!voiceEnabled) return
        if (restartPosted) return

        restartPosted = true
        voiceHandler.removeCallbacksAndMessages(null)

        voiceHandler.postDelayed({
            restartPosted = false
            if (!isFinishing && !isDestroyed) startVoiceListening()
        }, delayMs)
    }


    private fun normalizeRu(s: String): String {
        // нижний регистр, убираем ё->е, убираем знаки/лишние пробелы
        val low = s.lowercase(Locale.getDefault()).replace('ё', 'е')
        val noPunct = low.replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
        return noPunct.trim().replace(Regex("""\s+"""), " ")
    }

    private fun findCategoryByVoice(textNorm: String): CategoryDto? {
        if (categories.isEmpty()) return null

        // 1) точное совпадение
        categories.firstOrNull { normalizeRu(it.name) == textNorm }?.let { return it }

        // 2) если фраза длиннее (например "открой продукты") — ищем вхождение названия категории
        // сортируем по длине, чтобы "продукты" не перебило "продукты для дома" (если появится)
        val sorted = categories.sortedByDescending { it.name.length }
        for (c in sorted) {
            val n = normalizeRu(c.name)
            if (n.isNotBlank() && textNorm.contains(n)) return c
        }

        return null
    }
    private fun handleVoiceResults(bundle: Bundle?) {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val raw = list.firstOrNull().orEmpty()
        val text = normalizeRu(raw)

        if (text.isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastVoiceTriggerAt < voiceCooldownMs) return

        // Команды
        when {
            text.contains("доход") -> {
                lastVoiceTriggerAt = now
                openIncomeSheet()
                return
            }
            text.contains("добавить") && text.contains("категор") -> {
                lastVoiceTriggerAt = now
                showAddCategoryDialog()
                return
            }
            text == "назад" || text.contains("назад") -> {
                lastVoiceTriggerAt = now
                finish()
                return
            }
        }

        // Категория по названию
        val cat = findCategoryByVoice(text)
        if (cat != null) {
            lastVoiceTriggerAt = now
            openEntriesSheet(cat)
            return
        }

        // если не нашли — ничего не делаем
    }

    private fun openIncomeSheet() {
        isUiModalOpen = true
        stopVoiceListening()
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.bottom_sheet_entries)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvSheetTitle)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvEntries)

        tvTitle?.text = "Доход"
        if (rv == null) {
            dialog.setOnDismissListener {
                isUiModalOpen = false
                scheduleRestartVoice(1200L)
            }
            dialog.show()
            return
        }

        val day = intent.getStringExtra("date") ?: ""
        if (day.isBlank()) { Toast.makeText(this, "Нет даты", Toast.LENGTH_SHORT).show(); dialog.show(); return }

        val entries = mutableListOf<Entry>()
        rv.layoutManager = LinearLayoutManager(this)

        // снимок исходных значений по id (чтобы понять, что изменилось)
        val originalById = mutableMapOf<Int, Pair<String, String>>() // id -> (title, amountStr)

        lateinit var adapter: EntriesAdapter
        adapter = EntriesAdapter(entries) {
            val last = entries.lastOrNull()
            val lastFilled = last != null && last.name.isNotBlank() && last.value.isNotBlank()
            if (lastFilled) {
                entries.add(Entry("", "", null))
                adapter.notifyItemInserted(entries.size - 1)
            }
        }
        rv.adapter = adapter

        // LOAD
        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { ApiClient.api.getIncome(bearer(), day) }
                if (!res.ok) {
                    Toast.makeText(this@ActivityProduct, "Ошибка загрузки: ${res.error}", Toast.LENGTH_SHORT).show()
                } else {
                    entries.clear()
                    originalById.clear()

                    res.items.sortedBy { it.date }.forEach { inc ->
                        val e = Entry(inc.title, inc.amount.toString(), inc.id)
                        entries.add(e)
                        originalById[inc.id] = Pair(inc.title, inc.amount.toString())
                    }

                    // пустая строка
                    entries.add(Entry("", "", null))
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityProduct, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
                if (entries.isEmpty()) { entries.add(Entry("", "", null)); adapter.notifyDataSetChanged() }
            }
        }

        // SAVE on dismiss
        dialog.setOnDismissListener {
            isUiModalOpen = false
            scheduleRestartVoice(800L)
            val filled = entries.filter { it.name.isNotBlank() && it.value.isNotBlank() }

            // 1) NEW -> POST batch
            val newItems = filled.filter { it.id == null }.mapNotNull { row ->
                val amount = row.value.replace(",", ".").toDoubleOrNull() ?: return@mapNotNull null
                IncomeItemIn(
                    title = row.name.trim(),
                    amount = amount,
                    date = buildDateTimeForDay(day)
                )
            }

            // 2) UPDATED -> PUT per id
            val updates = filled.filter { it.id != null }.mapNotNull { row ->
                val id = row.id ?: return@mapNotNull null
                val old = originalById[id] ?: return@mapNotNull null

                val newTitle = row.name.trim()
                val newAmountStr = row.value.trim()
                if (newTitle == old.first && newAmountStr == old.second) return@mapNotNull null

                val newAmount = newAmountStr.replace(",", ".").toDoubleOrNull() ?: return@mapNotNull null

                Triple(id, newTitle, newAmount)
            }

            if (newItems.isEmpty() && updates.isEmpty()) return@setOnDismissListener

            uiScope.launch {
                try {
                    // сначала добавим новые
                    if (newItems.isNotEmpty()) {
                        val r = withContext(Dispatchers.IO) {
                            ApiClient.api.createIncome(bearer(), IncomeCreateRequest(newItems))
                        }
                        if (!r.ok) {
                            Toast.makeText(this@ActivityProduct, "Ошибка дохода: ${r.error}", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }

                    // потом обновим изменённые
                    for ((id, title, amount) in updates) {
                        val r = withContext(Dispatchers.IO) {
                            ApiClient.api.updateIncome(
                                bearer(),
                                id,
                                IncomeUpdateRequest(title = title, amount = amount)
                            )
                        }
                        if (!r.ok) {
                            Toast.makeText(this@ActivityProduct, "Ошибка обновления дохода id=$id: ${r.error}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Toast.makeText(this@ActivityProduct, "Доход сохранён", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ActivityProduct, "Сеть/сервер (доход): ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
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
        isUiModalOpen = true
        stopVoiceListening()

        val d =  AlertDialog.Builder(this)
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
            .create()
        d.setOnDismissListener {
            isUiModalOpen = false
            scheduleRestartVoice(800L)
        }
        d.show()
    }


    private fun openEntriesSheet(category: CategoryDto) {
        isUiModalOpen = true
        stopVoiceListening()

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
            isUiModalOpen = false
            scheduleRestartVoice(800L)

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
        stopVoiceListening()
        super.onDestroy()
        uiScope.cancel()

        voiceEnabled = false
        voiceHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

}
