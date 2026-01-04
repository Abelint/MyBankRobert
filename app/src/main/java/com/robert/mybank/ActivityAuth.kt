package com.robert.mybank


import android.os.Bundle
import android.text.InputFilter
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ActivityAuth : AppCompatActivity() {

    private lateinit var btnModeLogin: Button
    private lateinit var btnModeRegister: Button

    private lateinit var loginBlock: LinearLayout
    private lateinit var registerBlock: LinearLayout
    private lateinit var codeBlock: LinearLayout

    private lateinit var etLoginEmail: EditText
    private lateinit var etLoginPassword: EditText
    private lateinit var btnLogin: Button

    private lateinit var etRegName: EditText
    private lateinit var etRegEmail: EditText
    private lateinit var etRegPassword: EditText
    private lateinit var btnRegister: Button

    private lateinit var etCode: EditText
    private lateinit var btnConfirmCode: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // mode buttons
        btnModeLogin = findViewById(R.id.btnModeLogin)
        btnModeRegister = findViewById(R.id.btnModeRegister)

        // blocks
        loginBlock = findViewById(R.id.loginBlock)
        registerBlock = findViewById(R.id.registerBlock)
        codeBlock = findViewById(R.id.codeBlock)

        // login views
        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)

        // register views
        etRegName = findViewById(R.id.etRegName)
        etRegEmail = findViewById(R.id.etRegEmail)
        etRegPassword = findViewById(R.id.etRegPassword)
        btnRegister = findViewById(R.id.btnRegister)

        // code views
        etCode = findViewById(R.id.etCode)
        btnConfirmCode = findViewById(R.id.btnConfirmCode)

        // ограничим ввод кода ровно 4 символами
        etCode.filters = arrayOf(InputFilter.LengthFilter(4))

        // стартовый режим
        showLogin()

        btnModeLogin.setOnClickListener { showLogin() }
        btnModeRegister.setOnClickListener { showRegister() }

        btnLogin.setOnClickListener {

            finish()

            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPassword.text.toString()

            if (!isValidEmail(email)) {
                etLoginEmail.error = "Некорректная почта"
                return@setOnClickListener
            }
            if (pass.length < 6) {
                etLoginPassword.error = "Пароль минимум 6 символов"
                return@setOnClickListener
            }

            // TODO: тут будет реальная авторизация (Firebase/Auth/Backend)
            Toast.makeText(this, "Вход выполнен (демо)", Toast.LENGTH_SHORT).show()
        }

        btnRegister.setOnClickListener {
            val name = etRegName.text.toString().trim()
            val email = etRegEmail.text.toString().trim()
            val pass = etRegPassword.text.toString()

            if (name.length < 2) {
                etRegName.error = "Введите имя"
                return@setOnClickListener
            }
            if (!isValidEmail(email)) {
                etRegEmail.error = "Некорректная почта"
                return@setOnClickListener
            }
            if (pass.length < 6) {
                etRegPassword.error = "Пароль минимум 6 символов"
                return@setOnClickListener
            }

            // TODO: отправить регистрацию + отправить код на почту/смс
            // После успешной отправки кода показываем поле ввода 4-значного кода:
            codeBlock.visibility = View.VISIBLE
            Toast.makeText(this, "Код отправлен (демо). Введите 4 цифры.", Toast.LENGTH_SHORT).show()
        }

        btnConfirmCode.setOnClickListener {
            val code = etCode.text.toString().trim()

            if (code.length != 4) {
                etCode.error = "Введите 4 цифры"
                return@setOnClickListener
            }

            // TODO: проверить код на сервере
            Toast.makeText(this, "Код подтвержден (демо)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogin() {
        loginBlock.visibility = View.VISIBLE
        registerBlock.visibility = View.GONE
        codeBlock.visibility = View.GONE
    }

    private fun showRegister() {
        loginBlock.visibility = View.GONE
        registerBlock.visibility = View.VISIBLE
        codeBlock.visibility = View.GONE
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}