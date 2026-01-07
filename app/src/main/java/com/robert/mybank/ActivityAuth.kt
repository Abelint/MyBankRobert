package com.robert.mybank
import com.robert.mybank.server.*
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    // запоминаем email/пароль для шага confirm -> auto login
    private var pendingEmail: String? = null
    private var pendingPass: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        onBackPressedDispatcher.addCallback(this) {
            // если пользователь НЕ авторизован — закрываем приложение
            if (TokenStore.token(this@ActivityAuth).isNullOrBlank()) {
                finishAffinity()
            } else {
                // если вдруг авторизован — можно закрыть как обычно
                finish()
            }
        }

        btnModeLogin = findViewById(R.id.btnModeLogin)
        btnModeRegister = findViewById(R.id.btnModeRegister)

        loginBlock = findViewById(R.id.loginBlock)
        registerBlock = findViewById(R.id.registerBlock)
        codeBlock = findViewById(R.id.codeBlock)

        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)

        etRegName = findViewById(R.id.etRegName)
        etRegEmail = findViewById(R.id.etRegEmail)
        etRegPassword = findViewById(R.id.etRegPassword)
        btnRegister = findViewById(R.id.btnRegister)

        etCode = findViewById(R.id.etCode)
        btnConfirmCode = findViewById(R.id.btnConfirmCode)

        etCode.filters = arrayOf(InputFilter.LengthFilter(4))

        // Если уже есть токен — можно сразу пустить в приложение
        TokenStore.token(this)?.let {
            goNext()
            return
        }

        showLogin()

        btnModeLogin.setOnClickListener { showLogin() }
        btnModeRegister.setOnClickListener { showRegister() }

        btnLogin.setOnClickListener { doLogin() }
        btnRegister.setOnClickListener { doRegister() }
        btnConfirmCode.setOnClickListener { doConfirm() }
    }

    private fun doLogin() {
        val email = etLoginEmail.text.toString().trim()
        val pass = etLoginPassword.text.toString()

        if (!isValidEmail(email)) {
            etLoginEmail.error = "Некорректная почта"
            return
        }
        if (pass.length < 6) {
            etLoginPassword.error = "Пароль минимум 6 символов"
            return
        }

        setLoading(true)

        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.api.login(LoginRequest(email, pass))
                }
                if (res.ok && !res.token.isNullOrBlank()) {
                    TokenStore.save(this@ActivityAuth, res.token!!, email)
                    Toast.makeText(this@ActivityAuth, "Вход выполнен", Toast.LENGTH_SHORT).show()
                    goNext()
                } else {
                    Toast.makeText(this@ActivityAuth, "Ошибка: ${res.error ?: "login_failed"}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityAuth, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun doRegister() {
        val name = etRegName.text.toString().trim()
        val email = etRegEmail.text.toString().trim()
        val pass = etRegPassword.text.toString()

        if (name.length < 2) {
            etRegName.error = "Введите имя"
            return
        }
        if (!isValidEmail(email)) {
            etRegEmail.error = "Некорректная почта"
            return
        }
        if (pass.length < 6) {
            etRegPassword.error = "Пароль минимум 6 символов"
            return
        }

        setLoading(true)

        uiScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.api.register(RegisterRequest(email, pass, name))
                }
                if (res.ok) {
                    pendingEmail = email
                    pendingPass = pass

                    codeBlock.visibility = View.VISIBLE
                    Toast.makeText(this@ActivityAuth, "Код отправлен на почту. Введите 4 цифры.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@ActivityAuth, "Ошибка: ${res.error ?: "register_failed"}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityAuth, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun doConfirm() {
        val code = etCode.text.toString().trim()
        if (code.length != 4) {
            etCode.error = "Введите 4 цифры"
            return
        }

        val email = pendingEmail ?: etRegEmail.text.toString().trim()
        val pass = pendingPass ?: etRegPassword.text.toString()

        if (!isValidEmail(email)) {
            Toast.makeText(this, "Нет корректного email для подтверждения", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        uiScope.launch {
            try {
                val confirmRes = withContext(Dispatchers.IO) {
                    ApiClient.api.confirm(ConfirmRequest(email, code))
                }

                if (!confirmRes.ok) {
                    Toast.makeText(this@ActivityAuth, "Ошибка: ${confirmRes.error ?: "bad_code"}", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // подтверждено -> логинимся автоматически
                val loginRes = withContext(Dispatchers.IO) {
                    ApiClient.api.login(LoginRequest(email, pass))
                }

                if (loginRes.ok && !loginRes.token.isNullOrBlank()) {
                    TokenStore.save(this@ActivityAuth, loginRes.token!!, email)
                    Toast.makeText(this@ActivityAuth, "Аккаунт подтвержден и выполнен вход", Toast.LENGTH_SHORT).show()
                    goNext()
                } else {
                    Toast.makeText(this@ActivityAuth, "Подтверждено, но вход не выполнен: ${loginRes.error}", Toast.LENGTH_LONG).show()
                    showLogin()
                    etLoginEmail.setText(email)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivityAuth, "Сеть/сервер: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun goNext() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnRegister.isEnabled = !loading
        btnConfirmCode.isEnabled = !loading
        btnModeLogin.isEnabled = !loading
        btnModeRegister.isEnabled = !loading
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
