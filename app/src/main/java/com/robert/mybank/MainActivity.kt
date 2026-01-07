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

class MainActivity : AppCompatActivity() {
    private lateinit var tvLegendOuter: TextView // расход (красный)
    private lateinit var tvLegendMiddle: TextView // доход (зелёный)
    private lateinit var tvLegendInner: TextView // накопление (синий)
    private lateinit var ringsChart: RingsChartView
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

        val calendarView = findViewById<CalendarView>(R.id.calendarView)
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

        // первичная загрузка (текущий месяц)
        refreshRingsForMonth(currentMonthParam())
    }

    override fun onResume() {
        super.onResume()
        // когда вернулись из ActivityProduct (доходы/расходы могли поменяться)
        refreshRingsForMonth(currentMonthParam())
    }

    override fun onDestroy() {
        super.onDestroy()
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

            dialog.show()
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
}
