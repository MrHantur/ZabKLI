package ru.zabkli

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zabkli.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private val SETTINGS_ZABKLI = "settings_zabkli"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val listItemsForClassChange = arrayOf(
        "5 класс I гр.", "5 класс II гр.",
        "6 класс I гр.", "6 класс II гр.",
        "7 класс I гр.", "7 класс II гр.",
        "8 класс I гр.", "8 класс II гр.",
        "9 А  I гр.", "9 Б  I гр.", "9 А  II гр.", "9 Б  II гр.",
        "10 А  I гр.", "10 Б  I гр.", "10 А  II гр.", "10 Б  II гр.",
        "11 А  I гр.", "11 Б  I гр.", "11 А  II гр.", "11 Б  II гр."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsZabKLI = getSharedPreferences(
            SETTINGS_ZABKLI,
            MODE_PRIVATE
        )

        super.onCreate(savedInstanceState)

        if (AuthManager.isLoggedIn(this)) {
            lifecycleScope.launch {
                AuthManager.refreshProfile(this@MainActivity)
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val navView: BottomNavigationView = binding.appBarMain.navigationView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_schedule, R.id.navigation_olympiads, R.id.navigation_news
            ),
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val isDarkMode: Boolean = settingsZabKLI.getBoolean("darkMode", false)
        val whatClassUserUse = settingsZabKLI.getInt("userUsingClassId", 5)

        if (isDarkMode){
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding.appBarMain.textClass.text = listItemsForClassChange[whatClassUserUse]

        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    fun settingsButton(view: View){
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun editClassButton(view: View){
        val settingsZabKLI = getSharedPreferences(
            SETTINGS_ZABKLI,
            MODE_PRIVATE
        )

        val mBuilder = AlertDialog.Builder(this@MainActivity)
        mBuilder.setTitle("Выберите свой класс")
        mBuilder.setSingleChoiceItems(listItemsForClassChange, -1) { dialogInterface, i ->
            binding.appBarMain.textClass.text = listItemsForClassChange[i]
            val e: SharedPreferences.Editor = settingsZabKLI.edit()
            e.putInt("userUsingClassId", i)
            e.apply()
            dialogInterface.dismiss()
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
        mBuilder.setNeutralButton("Закрыть") { dialog, _ ->
            dialog.cancel()
        }

        val mDialog = mBuilder.create()
        mDialog.show()
    }
}