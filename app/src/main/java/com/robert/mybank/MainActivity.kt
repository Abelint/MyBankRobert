package com.robert.mybank

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CalendarView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robert.mybank.activityproduct.ActivityProduct

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var progressPercent = 0 // 0..100

    private lateinit var ringsChart: RingsChartView

    private val tick = object : Runnable {
        override fun run() {
            // шаг +2% каждую секунду, максимум 100
            progressPercent = (progressPercent + 2).coerceAtMost(100)

            val p = progressPercent / 100f

            // Если хочешь чтобы кольца заполнялись одинаково:
            ringsChart.animateTo(p, p, p, durationMs = 1000L)

            // Если хочешь разные значения — можно так, например:
            // ringsChart.animateTo(p, (p * 0.8f).coerceAtMost(1f), (p * 0.6f).coerceAtMost(1f))

            if (progressPercent < 100) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val calendarView = findViewById<CalendarView>(R.id.calendarView)


        ringsChart = findViewById<RingsChartView>(R.id.ringsChart)
        // стартуем с 0%
        ringsChart.setProgressInstant(0f, 0f, 0f)
        progressPercent = 0
        handler.postDelayed(tick, 1000L)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // month: 0..11
            val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)

            val intent = Intent(this, ActivityProduct::class.java)
            intent.putExtra("date", dateStr)
            startActivity(intent)
        }

        startActivity( Intent(this, ActivityAuth::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
    }
}