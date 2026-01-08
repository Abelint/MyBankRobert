package com.robert.mybank

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CalendarView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robert.mybank.activityproduct.ActivityProduct
import com.robert.mybank.server.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import android.widget.TextView
import java.math.BigDecimal
import java.math.RoundingMode
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private var voiceEnabled = true

    private var resumeStartRunnable: Runnable? = null
    private var restartRunnable: Runnable? = null

    private val voiceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var restartPosted = false
    private val restartDelayMs = 1500L
    private var isTargetsDialogVisible = false
    private val REQ_AUDIO = 101

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null

    // чтобы не открывать диалог 10 раз подряд
    private var lastVoiceTriggerAt = 0L
    private val voiceCooldownMs = 2000L

    private var isListening = false

    private lateinit var tvLegendOuter: TextView // расход (красный)
    private lateinit var tvLegendMiddle: TextView // доход (зелёный)
    private lateinit var tvLegendInner: TextView // накопление (синий)
    private lateinit var ringsChart: RingsChartView
    private lateinit var calendarView: CalendarView

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // выбранная дата календаря, если была
    private var selectedDateStr: String? = null // "YYYY-MM-DD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // если нет токена — сразу в авторизацию и закрываем MainActivity
        val token = TokenStore.token(this)
        if (token.isNullOrBlank()) {
            startActivity(Intent(this, ActivityAuth::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        tvLegendOuter = findViewById(R.id.tvLegendOuter)
        tvLegendMiddle = findViewById(R.id.tvLegendMiddle)
        tvLegendInner = findViewById(R.id.tvLegendInner)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ringsChart = findViewById(R.id.ringsChart)
        ringsChart.setProgressInstant(0f, 0f, 0f)

        ringsChart.setOnClickListener {
            showTargetsDialog()
        }

        calendarView = findViewById<CalendarView>(R.id.calendarView)
        selectedDateStr = todayStr()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            selectedDateStr = dateStr

            // открываем записи за день
            val intent = Intent(this, ActivityProduct::class.java)
            intent.putExtra("date", dateStr)
            startActivity(intent)

            // обновим диаграмму под месяц выбранного дня
            refreshRingsForMonth(monthParamFromDate(dateStr))
        }

        initVoiceControl()
        ensureAudioPermissionAndStart()


        // первичная загрузка (текущий месяц)
        refreshRingsForMonth(currentMonthParam())
    }

    override fun onResume() {
        super.onResume()
        refreshRingsForMonth(currentMonthParam())

        voiceEnabled = true

        resumeStartRunnable?.let { voiceHandler.removeCallbacks(it) }
        val r = Runnable { ensureAudioPermissionAndStart() }
        resumeStartRunnable = r
        voiceHandler.postDelayed(r, 1200L)
    }


    override fun onPause() {
        super.onPause()
        voiceEnabled = false

        resumeStartRunnable?.let { voiceHandler.removeCallbacks(it) }
        resumeStartRunnable = null

        restartRunnable?.let { voiceHandler.removeCallbacks(it) }
        restartRunnable = null

        voiceHandler.removeCallbacksAndMessages(null) // ВАЖНО: сносит и onError-postDelayed
        stopVoiceListening()
    }

    override fun onStop() {
        super.onStop()
        voiceEnabled = false

        resumeStartRunnable?.let { voiceHandler.removeCallbacks(it) }
        resumeStartRunnable = null

        restartRunnable?.let { voiceHandler.removeCallbacks(it) }
        restartRunnable = null

        voiceHandler.removeCallbacksAndMessages(null)
        stopVoiceListening()
    }


    override fun onDestroy() {
        super.onDestroy()
        stopVoiceListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        uiScope.cancel()
    }


    // =========================
    // Summary -> Rings
    // =========================

    private fun refreshRingsForMonth(month: String) {
        uiScope.launch {
            val res = try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.getTargetsSummary(bearer(), month) // month="MM.YYYY"
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }

            if (!res.ok) {
                Toast.makeText(this@MainActivity, "Ошибка summary: ${res.error}", Toast.LENGTH_SHORT).show()
                return@launch
            }

            applySummaryToRings(res)
        }
    }

    private fun applySummaryToRings(res: TargetsSummaryResponse) {
        val saving = res.items?.saving   // type=1 накопление
        val expense = res.items?.expense // type=2 расход
        val income = res.items?.income   // type=3 доход

        fun to01(pct: Double): Float = (pct.coerceIn(0.0, 100.0) / 100.0).toFloat()

        // кольца
        ringsChart.animateTo(
            outer = to01(expense?.percent ?: 0.0), // красный = расход
            middle = to01(income?.percent ?: 0.0), // зелёный = доход
            inner = to01(saving?.percent ?: 0.0),  // синий = накопление
            durationMs = 700L
        )

        // легенда "<current>/<target>"
        fun fmtMoney(v: Double): String {
            // показываем целые, если без копеек, иначе 2 знака
            val bd = BigDecimal(v).setScale(2, RoundingMode.HALF_UP)
            return if (bd.stripTrailingZeros().scale() <= 0) {
                bd.setScale(0, RoundingMode.HALF_UP).toPlainString()
            } else {
                bd.toPlainString()
            }
        }

        fun legendLine(prefix: String, item: SummaryItem?): String {
            return if (item == null) {
                "$prefix: —"
            } else {
                "${prefix}: ${fmtMoney(item.current_value)}/${fmtMoney(item.target_cost)}"
            }
        }

        // по цветам:
        // outer = красный = расход
        // middle = зелёный = доход
        // inner = синий = накопление
        tvLegendOuter.text = legendLine("Расход", expense)
        tvLegendMiddle.text = legendLine("Доход", income)
        tvLegendInner.text = legendLine("Накопление", saving)
    }


    // =========================
    // Dialog: 3 targets -> PUT /targets/current
    // =========================

    private fun showTargetsDialog() {
        val token = TokenStore.token(this)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Нет токена. Авторизуйтесь.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ActivityAuth::class.java))
            finish()
            return
        }

        uiScope.launch {
            val currentRes = try {
                withContext(Dispatchers.IO) { ApiClient.api.getTargetsCurrent(bearer()) }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }

            if (!currentRes.ok) {
                Toast.makeText(this@MainActivity, "Ошибка: ${currentRes.error}", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val view = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.dialog_targets, null, false)

            val etT1Name = view.findViewById<EditText>(R.id.etT1Name)
            val etT1Cost = view.findViewById<EditText>(R.id.etT1Cost)

            val etT2Name = view.findViewById<EditText>(R.id.etT2Name)
            val etT2Cost = view.findViewById<EditText>(R.id.etT2Cost)

            val etT3Name = view.findViewById<EditText>(R.id.etT3Name)
            val etT3Cost = view.findViewById<EditText>(R.id.etT3Cost)

            // prefill из /targets/current
            currentRes.items["1"]?.let { t ->
                etT1Name.setText(t.name)
                etT1Cost.setText(t.cost.toString())
            }
            currentRes.items["2"]?.let { t ->
                etT2Name.setText(t.name)
                etT2Cost.setText(t.cost.toString())
            }
            currentRes.items["3"]?.let { t ->
                etT3Name.setText(t.name)
                etT3Cost.setText(t.cost.toString())
            }

            val dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle("Цели")
                .setView(view)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null) // обработаем вручную
                .create()

            dialog.setOnShowListener {
                val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

                btnSave.setOnClickListener {
                    val items = mutableListOf<TargetCurrentItemIn>()

                    fun parseCost(et: EditText): Double? {
                        val s = et.text.toString().trim().replace(",", ".")
                        if (s.isBlank()) return null
                        return s.toDoubleOrNull()
                    }

                    fun addIfFilled(type: Int, etName: EditText, etCost: EditText): Boolean {
                        val name = etName.text.toString().trim()
                        val cost = parseCost(etCost)

                        // оба пустые — игнорируем (цель не обязательна)
                        if (name.isBlank() && cost == null) return true

                        // частично заполнено — ошибка
                        if (name.isBlank()) {
                            etName.error = "Введите название"
                            return false
                        }
                        if (cost == null || cost <= 0) {
                            etCost.error = "Введите сумму"
                            return false
                        }

                        items.add(TargetCurrentItemIn(type = type, name = name, cost = cost))
                        return true
                    }

                    if (!addIfFilled(1, etT1Name, etT1Cost)) return@setOnClickListener
                    if (!addIfFilled(2, etT2Name, etT2Cost)) return@setOnClickListener
                    if (!addIfFilled(3, etT3Name, etT3Cost)) return@setOnClickListener

                    if (items.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Заполните хотя бы одну цель", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    uiScope.launch {
                        try {
                            val putRes = withContext(Dispatchers.IO) {
                                ApiClient.api.putTargetsCurrent(
                                    bearer(),
                                    TargetsCurrentPutRequest(items = items)
                                )
                            }

                            if (!putRes.ok) {
                                Toast.makeText(this@MainActivity, "Ошибка: ${putRes.error}", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            Toast.makeText(this@MainActivity, "Цели сохранены", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()

                            // сразу обновляем диаграмму после сохранения целей
                            refreshRingsForMonth(currentMonthParam())

                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            isTargetsDialogVisible = true
            stopVoiceListening() // чтобы не слушал во время ввода
            dialog.show()
            dialog.setOnDismissListener {
                isTargetsDialogVisible = false
                handlerPostRestartListening()
            }
        }
    }

    // =========================
    // Helpers
    // =========================

    private fun bearer(): String {
        val token = TokenStore.token(this)
        return "Bearer ${token ?: ""}"
    }

    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun monthParamFromDate(dateStr: String): String {
        // "YYYY-MM-DD" -> "MM.YYYY"
        val parts = dateStr.split("-")
        val yyyy = parts.getOrNull(0) ?: "1970"
        val mm = parts.getOrNull(1) ?: "01"
        return "$mm.$yyyy"
    }

    private fun currentMonthParam(): String {
        // если пользователь выбрал дату — берём её месяц, иначе текущий
        val d = selectedDateStr
        return if (!d.isNullOrBlank()) monthParamFromDate(d)
        else {
            val cal = Calendar.getInstance()
            val mm = cal.get(Calendar.MONTH) + 1
            val yyyy = cal.get(Calendar.YEAR)
            String.format("%02d.%04d", mm, yyyy)
        }
    }

    private fun initVoiceControl() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // на некоторых девайсах/эмуляторах нет сервиса распознавания
            Toast.makeText(this, "Распознавание речи недоступно на устройстве", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
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

                // Частые ошибки:
                // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT — это "ничего не сказал", просто мягко рестартим
                // ERROR_RECOGNIZER_BUSY — не трогаем, дадим остыть
                // остальные — тоже мягко рестартим

                if (!voiceEnabled) return

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    handlerPostRestartListening(2500L)
                    return
                }
                handlerPostRestartListening(800L)

            }


            override fun onResults(results: Bundle?) {
                isListening = false
                handleSpeechResults(results)
                handlerPostRestartListening()
            }


            override fun onPartialResults(partialResults: Bundle?) {
                // можно реагировать уже на частичные результаты
               // handleSpeechResults(partialResults)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun handleSpeechResults(bundle: Bundle?) {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val text = (list.firstOrNull() ?: "").lowercase(Locale.getDefault())

        // 1) если в фразе есть “седьмое / 7 / двадцать первое” — выберем день на календаре
        val day = extractDayOfMonth(text)
        if (day != null) {
            selectDayOnCalendar(day) // визуально выделит день + обновит selectedDateStr + диаграмму
        }

        // 2) “цель” — открыть диалог целей (у тебя уже работает)
        if (text.contains("цель")) {
            val now = System.currentTimeMillis()
            if (now - lastVoiceTriggerAt >= voiceCooldownMs) {
                lastVoiceTriggerAt = now
                showTargetsDialog()
            }
            return
        }

        // 3) “записать” — открыть ActivityProduct на текущую выбранную дату календаря
        if (text.contains("записать")) {
            val now = System.currentTimeMillis()
            if (now - lastVoiceTriggerAt >= voiceCooldownMs) {
                lastVoiceTriggerAt = now
                openActivityProductForSelectedDate()
            }
            return
        }

        // (необязательно) быстрые команды
        if (text.contains("сегодня")) {
            val now = Calendar.getInstance()
            setCalendarToDate(now)
            return
        }
        if (text.contains("завтра")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 1)
            setCalendarToDate(cal)
            return
        }
        if (text.contains("вчера")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -1)
            setCalendarToDate(cal)
            return
        }
    }


    private fun ensureAudioPermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) {
            startVoiceListening()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_AUDIO
            )
        }
    }

    private fun startVoiceListening() {
        if (!voiceEnabled) return
        if (isTargetsDialogVisible) return
        if (isListening) return

        Log.d("VOICE", "START in MainActivity")

        val sr = speechRecognizer ?: return
        val intent = speechIntent ?: return

        try {
            isListening = true
            sr.startListening(intent)
        } catch (_: Throwable) {
            isListening = false
            handlerPostRestartListening()
        }
    }



    private fun stopVoiceListening() {
        Log.d("VOICE", "stopVoiceListening in MainActivity")
        try {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Throwable) {}
    }

    private fun handlerPostRestartListening(delayMs: Long = 400L) {
        if (!voiceEnabled) return

        restartRunnable?.let { voiceHandler.removeCallbacks(it) }

        val r = Runnable {
            if (!isFinishing && !isDestroyed) startVoiceListening()
        }
        restartRunnable = r
        voiceHandler.postDelayed(r, delayMs)
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_AUDIO) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (ok) {
                startVoiceListening()
            } else {
                Toast.makeText(this, "Микрофон не разрешён — голосовое управление выключено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openActivityProductForSelectedDate() {
        // берем то, что сейчас выбрано (или текущую дату)
        val dateStr = selectedDateStr ?: todayStr()

        val intent = Intent(this, ActivityProduct::class.java)
        intent.putExtra("date", dateStr)
        startActivity(intent)
    }

    private fun selectDayOnCalendar(day: Int) {
        // Берём текущую дату календаря (она всегда есть), меняем только день месяца
        val cal = Calendar.getInstance()
        cal.timeInMillis = calendarView.date

        // clamp: если сказали 31, а месяц короче — поставим последний день месяца
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val safeDay = day.coerceIn(1, maxDay)

        cal.set(Calendar.DAY_OF_MONTH, safeDay)

        // ВАЖНО: этот set визуально “выделяет” дату на CalendarView
        calendarView.date = cal.timeInMillis

        // обновим selectedDateStr
        val yyyy = cal.get(Calendar.YEAR)
        val mm = cal.get(Calendar.MONTH) + 1
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        val dateStr = String.format("%04d-%02d-%02d", yyyy, mm, dd)
        selectedDateStr = dateStr

        // и обновим диаграмму под месяц выбранного дня
        refreshRingsForMonth(monthParamFromDate(dateStr))
    }

    private fun setCalendarToDate(cal: Calendar) {
        calendarView.date = cal.timeInMillis

        val yyyy = cal.get(Calendar.YEAR)
        val mm = cal.get(Calendar.MONTH) + 1
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        val dateStr = String.format("%04d-%02d-%02d", yyyy, mm, dd)
        selectedDateStr = dateStr

        refreshRingsForMonth(monthParamFromDate(dateStr))
    }
    private fun extractDayOfMonth(text: String): Int? {
        // 1) сначала цифры: "7", "07", "31"
        val m = Regex("""\b(0?[1-9]|[12]\d|3[01])\b""").find(text)
        if (m != null) {
            val v = m.groupValues[1].toIntOrNull()
            if (v != null && v in 1..31) return v
        }

        // 2) слова (важно: длинные фразы — раньше, чтобы "тридцать первое" не схватилось как "первое")
        val map = listOf(
            "тридцать первое" to 31,
            "тридцать первое число" to 31,
            "тридцать" to 30, // иногда говорят "тридцатое", но распознавание может дать "тридцать"
            "тридцатое" to 30,

            "двадцать девятое" to 29,
            "двадцать восьмое" to 28,
            "двадцать седьмое" to 27,
            "двадцать шестое" to 26,
            "двадцать пятое" to 25,
            "двадцать четвертое" to 24,
            "двадцать третье" to 23,
            "двадцать второе" to 22,
            "двадцать первое" to 21,

            "двадцатое" to 20,
            "девятнадцатое" to 19,
            "восемнадцатое" to 18,
            "семнадцатое" to 17,
            "шестнадцатое" to 16,
            "пятнадцатое" to 15,
            "четырнадцатое" to 14,
            "тринадцатое" to 13,
            "двенадцатое" to 12,
            "одиннадцатое" to 11,
            "десятое" to 10,

            "девятое" to 9,
            "восьмое" to 8,
            "седьмое" to 7,
            "шестое" to 6,
            "пятое" to 5,
            "четвертое" to 4,
            "третье" to 3,
            "второе" to 2,
            "первое" to 1,

            // запасные варианты (иногда распознаёт без “-ое”)
            "один" to 1,
            "два" to 2,
            "три" to 3,
            "четыре" to 4,
            "пять" to 5,
            "шесть" to 6,
            "семь" to 7,
            "восемь" to 8,
            "девять" to 9,
            "десять" to 10
        ).sortedByDescending { it.first.length }

        for ((k, v) in map) {
            if (text.contains(k)) return v
        }

        return null
    }


}
