package ru.zabkli

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settingsZabKLI = getSharedPreferences(
            SETTINGS_ZABKLI,
            MODE_PRIVATE
        )

        isChangedFunction = settingsZabKLI.getBoolean("changedFunction", true)
        isDarkMode = settingsZabKLI.getBoolean("darkMode", false)

        findViewById<SwitchCompat>(R.id.changedFunctionSwitch).setChecked(isChangedFunction)
        findViewById<SwitchCompat>(R.id.darkModeSwitch).setChecked(isDarkMode)

        supportActionBar?.hide()
    }

    fun backButton(view: View){
        startActivity(Intent(this, MainActivity::class.java))
    }

    fun darkMode(view: View){
        val settingsZabKLI = getSharedPreferences(
            SETTINGS_ZABKLI,
            MODE_PRIVATE
        )

        isDarkMode = !isDarkMode
        settingsZabKLI.edit().putBoolean("darkMode", isDarkMode).apply()
        findViewById<SwitchCompat>(R.id.darkModeSwitch).setChecked(isDarkMode)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun changedFunction(view: View){
        val settingsZabKLI = getSharedPreferences(
            SETTINGS_ZABKLI,
            MODE_PRIVATE
        )

        isChangedFunction = !isChangedFunction
        settingsZabKLI.edit().putBoolean("changedFunction", isChangedFunction).apply()
        findViewById<SwitchCompat>(R.id.changedFunctionSwitch).setChecked(isChangedFunction)
    }

    fun survey(view: View) {
        if ((currentIdOfQuestionInSurvey < 3) and (!isSurveyDataSent)) {
            val questions = listOf(
                "Как Вы оцените дизайн приложения?",
                "Как Вы оцените функционал приложения?",
                "Как Вы оцените удовлетворённость приложением в целом?"
            )

            val ratingdialog: AlertDialog.Builder = AlertDialog.Builder(this)

            ratingdialog.setMessage(questions[currentIdOfQuestionInSurvey])

            val linearlayout: View = layoutInflater.inflate(R.layout.ratingdialog, null)
            ratingdialog.setView(linearlayout)

            val rating = linearlayout.findViewById<View>(R.id.ratingbar) as RatingBar

            ratingdialog.setPositiveButton("Готово"
            ) { dialog, _ ->
                if (rating.rating.toInt() != 0) {
                    answersInSurvey.add(rating.rating.toInt())
                    currentIdOfQuestionInSurvey += 1
                    dialog.dismiss()
                    survey(view)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Нажмите на звёздочку, чтобы дать оценку!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
                .setNegativeButton("Выйти"
                ) { dialog, _ -> dialog.cancel() }

            ratingdialog.create()
            ratingdialog.show()
        } else {
            Toast.makeText(applicationContext, "Анкетирование пройдено!", Toast.LENGTH_SHORT).show()
            if (!isSurveyDataSent){
                var surveyResults = "4$"
                surveyResults = surveyResults +
                        answersInSurvey[0].toString() + "$" +
                        answersInSurvey[1].toString() + "$" +
                        answersInSurvey[2].toString() + "$"
                lifecycleScope.launch {
                    sendSurveyData(surveyResults)
                }
                isSurveyDataSent = true
            }
        }
    }

    private suspend fun sendSurveyData(surveyResults: String){
        return withContext(Dispatchers.IO) {
            try {
                val client = Socket("185.177.216.236", 1717)
                val output = PrintWriter(client.getOutputStream(), true)

                output.println(surveyResults)
            } catch (excep: ConnectException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Не удалось получить ответ от сервера", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}