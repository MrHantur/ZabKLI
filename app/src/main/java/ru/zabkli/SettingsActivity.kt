package ru.zabkli
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket

class SettingsActivity : AppCompatActivity() {
    private val SETTINGS_ZABKLI = "settings_zabkli"

    private var currentIdOfQuestionInSurvey = 0
    private val answersInSurvey = ArrayList<Int>()
    private var isSurveyDataSent: Boolean = false
    private var isChangedFunction: Boolean = false
    private var isDarkMode: Boolean = false

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settingsZabKLI = getSharedPreferences(SETTINGS_ZABKLI, MODE_PRIVATE)
        isChangedFunction = settingsZabKLI.getBoolean("changedFunction", true)
        isDarkMode = settingsZabKLI.getBoolean("darkMode", false)

        findViewById<SwitchCompat>(R.id.changedFunctionSwitch).isChecked = isChangedFunction
        findViewById<SwitchCompat>(R.id.darkModeSwitch).isChecked = isDarkMode

        supportActionBar?.hide()

        // Клик по блоку аккаунта
        findViewById<LinearLayout>(R.id.accountSection).setOnClickListener {
            if (AuthManager.isLoggedIn(this)) showLoggedInDialog()
            else showAuthDialog()
        }

        // Первичное обновление UI аккаунта
        updateAccountSection()
    }

    override fun onResume() {
        super.onResume()
        // При каждом возврате на экран обновляем роль с сервера
        if (AuthManager.isLoggedIn(this)) {
            lifecycleScope.launch {
                AuthManager.refreshProfile(this@SettingsActivity)
                withContext(Dispatchers.Main) { updateAccountSection() }
            }
        }
    }

    private fun updateAccountSection() {
        val avatar   = findViewById<ImageView>(R.id.avatar)
        val nameView = findViewById<TextView>(R.id.name)
        val roleView = findViewById<TextView>(R.id.role)

        val userType = AuthManager.getRole(this)
        updateUserView(userType, avatar, roleView)

        nameView.text = if (AuthManager.isLoggedIn(this))
            AuthManager.getDisplayName(this)
        else
            getString(R.string.settings_anonymous)
    }

    private fun showAuthDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auth, null)

        val tabLogin    = view.findViewById<TextView>(R.id.tabLogin)
        val tabRegister = view.findViewById<TextView>(R.id.tabRegister)
        val panelLogin  = view.findViewById<View>(R.id.panelLogin)
        val panelReg    = view.findViewById<View>(R.id.panelRegister)

        // Поля логина
        val loginUsername = view.findViewById<EditText>(R.id.loginUsername)
        val loginPassword = view.findViewById<EditText>(R.id.loginPassword)

        // Поля регистрации
        val regUsername  = view.findViewById<EditText>(R.id.regUsername)
        val regPassword  = view.findViewById<EditText>(R.id.regPassword)
        val regFirstName = view.findViewById<EditText>(R.id.regFirstName)
        val regLastName  = view.findViewById<EditText>(R.id.regLastName)

        val errorText = view.findViewById<TextView>(R.id.authError)
        val progress  = view.findViewById<ProgressBar>(R.id.authProgress)

        var isLoginTab = true

        fun switchTab(login: Boolean) {
            isLoginTab = login
            panelLogin.visibility  = if (login) View.VISIBLE else View.GONE
            panelReg.visibility    = if (login) View.GONE    else View.VISIBLE
            tabLogin.alpha    = if (login) 1f else 0.5f
            tabRegister.alpha = if (login) 0.5f else 1f
            errorText.visibility = View.GONE
        }

        tabLogin.setOnClickListener    { switchTab(true) }
        tabRegister.setOnClickListener { switchTab(false) }
        switchTab(true)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Продолжить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                errorText.visibility = View.GONE
                progress.visibility  = View.VISIBLE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                lifecycleScope.launch {
                    val error = if (isLoginTab) {
                        val u = loginUsername.text.toString().trim()
                        val p = loginPassword.text.toString()
                        if (u.isEmpty() || p.isEmpty()) "Заполните все поля"
                        else AuthManager.login(this@SettingsActivity, u, p)
                    } else {
                        val u  = regUsername.text.toString().trim()
                        val p  = regPassword.text.toString()
                        val fn = regFirstName.text.toString().trim().ifBlank { null }
                        val ln = regLastName.text.toString().trim().ifBlank { null }
                        if (u.isEmpty() || p.isEmpty()) "Заполните обязательные поля"
                        else AuthManager.register(this@SettingsActivity, u, p, fn, ln)
                    }

                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true

                        if (error == null) {
                            updateAccountSection()
                            dialog.dismiss()
                            Toast.makeText(
                                this@SettingsActivity,
                                "Вы вошли как ${AuthManager.getDisplayName(this@SettingsActivity)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            errorText.text = error
                            errorText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showLoggedInDialog() {
        val userInfo = AuthManager.getUserInfo(this)

        // Создаём кастомный view для диалога с подробной информацией
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_account_info, null)

        // Заполняем данные
        view.findViewById<TextView>(R.id.accountDisplayName).text = AuthManager.getDisplayName(this)
        view.findViewById<TextView>(R.id.accountUsername).text = "Логин: ${userInfo.username ?: "Не указан"}"
        view.findViewById<TextView>(R.id.accountUserId).text = "ID: ${userInfo.id ?: "Не указан"}"
        view.findViewById<TextView>(R.id.accountFirstName).text = "Имя: ${userInfo.firstName ?: "Не указано"}"
        view.findViewById<TextView>(R.id.accountLastName).text = "Фамилия: ${userInfo.lastName ?: "Не указана"}"
        view.findViewById<TextView>(R.id.accountRole).text = "Роль: ${userInfo.role.displayName()}"
        view.findViewById<TextView>(R.id.accountKarma).text = "Карма: ${userInfo.karma}"

        // Обновляем аватар в диалоге
        val dialogAvatar = view.findViewById<ImageView>(R.id.dialogAvatar)
        updateUserView(userInfo.role, dialogAvatar, view.findViewById(R.id.dialogRoleText))

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Выйти") { _, _ ->
                AuthManager.logout(this)
                updateAccountSection()
                Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Обновить", { _, _ ->
                lifecycleScope.launch {
                    AuthManager.refreshProfile(this@SettingsActivity)
                    withContext(Dispatchers.Main) {
                        showLoggedInDialog() // Пересоздаём диалог с обновлёнными данными
                    }
                }
            })
            .show()
    }

    enum class UserType {
        UNKNOWN, CONTRIBUTOR, EDITOR, ADMIN, VIEWER;

        fun displayName(): String = when (this) {
            UNKNOWN     -> "Ученик"
            VIEWER      -> "Ученик (авторизован)"
            CONTRIBUTOR -> "Помощник"
            EDITOR      -> "Редактор"
            ADMIN       -> "Администратор"
        }
    }

    fun updateUserView(type: UserType, avatar: ImageView, roleText: TextView) {
        when (type) {
            UserType.UNKNOWN -> {
                avatar.setImageResource(R.drawable.profile_unknown_user)
                avatar.background = ContextCompat.getDrawable(avatar.context, R.drawable.circle_user_unknown)
            }
            UserType.CONTRIBUTOR -> {
                avatar.setImageResource(R.drawable.profile_contributor_user)
                avatar.background = ContextCompat.getDrawable(avatar.context, R.drawable.circle_user_contributor)
            }
            UserType.EDITOR -> {
                avatar.setImageResource(R.drawable.profile_editor_user)
                avatar.background = ContextCompat.getDrawable(avatar.context, R.drawable.circle_user_editor)
            }
            UserType.ADMIN -> {
                avatar.setImageResource(R.drawable.profile_admin_user)
                avatar.background = ContextCompat.getDrawable(avatar.context, R.drawable.circle_user_admin)
            }
            UserType.VIEWER -> {
                avatar.setImageResource(R.drawable.profile_viewer_user)
                avatar.background = ContextCompat.getDrawable(avatar.context, R.drawable.circle_user_viewer)
            }
        }
        roleText.text = type.displayName()
    }

    fun backButton(view: View) {
        startActivity(Intent(this, MainActivity::class.java))
    }

    fun darkMode(view: View) {
        val settingsZabKLI = getSharedPreferences(SETTINGS_ZABKLI, MODE_PRIVATE)
        isDarkMode = !isDarkMode
        settingsZabKLI.edit().putBoolean("darkMode", isDarkMode).apply()
        findViewById<SwitchCompat>(R.id.darkModeSwitch).isChecked = isDarkMode
        if (isDarkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    fun changedFunction(view: View) {
        val settingsZabKLI = getSharedPreferences(SETTINGS_ZABKLI, MODE_PRIVATE)
        isChangedFunction = !isChangedFunction
        settingsZabKLI.edit().putBoolean("changedFunction", isChangedFunction).apply()
        findViewById<SwitchCompat>(R.id.changedFunctionSwitch).isChecked = isChangedFunction
    }

    fun survey(view: View) {
        if ((currentIdOfQuestionInSurvey < 3) and (!isSurveyDataSent)) {
            val questions = listOf(
                "Как Вы оцените дизайн приложения?",
                "Как Вы оцените функционал приложения?",
                "Как Вы оцените удовлетворённость приложением в целом?"
            )
            val ratingdialog = AlertDialog.Builder(this)
            ratingdialog.setMessage(questions[currentIdOfQuestionInSurvey])
            val linearlayout: View = layoutInflater.inflate(R.layout.ratingdialog, null)
            ratingdialog.setView(linearlayout)
            val rating = linearlayout.findViewById<RatingBar>(R.id.ratingbar)
            ratingdialog
                .setPositiveButton("Готово") { dialog, _ ->
                    if (rating.rating.toInt() != 0) {
                        answersInSurvey.add(rating.rating.toInt())
                        currentIdOfQuestionInSurvey += 1
                        dialog.dismiss()
                        survey(view)
                    } else {
                        Toast.makeText(applicationContext,
                            "Нажмите на звёздочку, чтобы дать оценку!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Выйти") { dialog, _ -> dialog.cancel() }
            ratingdialog.create()
            ratingdialog.show()
        } else {
            Toast.makeText(applicationContext, "Анкетирование пройдено!", Toast.LENGTH_SHORT).show()
            if (!isSurveyDataSent) {
                var surveyResults = "4$"
                surveyResults += answersInSurvey[0].toString() + "$" +
                        answersInSurvey[1].toString() + "$" +
                        answersInSurvey[2].toString() + "$"
                lifecycleScope.launch { sendSurveyData(surveyResults) }
                isSurveyDataSent = true
            }
        }
    }

    private suspend fun sendSurveyData(surveyResults: String) = withContext(Dispatchers.IO) {
        try {
            val client = Socket("185.177.216.236", 1717)
            PrintWriter(client.getOutputStream(), true).println(surveyResults)
        } catch (excep: ConnectException) {
            runOnUiThread {
                Toast.makeText(applicationContext,
                    "Не удалось получить ответ от сервера", Toast.LENGTH_LONG).show()
            }
        }
    }
}