package ru.zabkli

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import ru.zabkli.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private val SETTINGS_ZABKLI = "settings_zabkli"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    val listItemsForClassChange = arrayOf("5", "6", "7", "8", "9 А", "9 Б", "10 А", "10 Б", "11 А", "11 Б")

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsZabKLI = getSharedPreferences(
            SETTINGS_ZABKLI,
            MODE_PRIVATE
        )

        super.onCreate(savedInstanceState)

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

        getSupportActionBar()?.setDisplayShowTitleEnabled(false)
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
        mBuilder.setNeutralButton("Закрыть") { dialog, which ->
            dialog.cancel()
        }

        val mDialog = mBuilder.create()
        mDialog.show()
    }
}