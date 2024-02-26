package ru.zabkli.ui.olympiads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zabkli.databinding.FragmentOlympiadsBinding
import ru.zabkli.ui.news.NewsData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket

class OlympiadsFragment : Fragment() {

    val weekOlympiadsNames: ArrayList<String> = ArrayList()
    val dynamicOlympiadsNames: ArrayList<String> = ArrayList()
    val weekOlympiadsDescription: ArrayList<String> = ArrayList()
    val dynamicOlympiadsDescription: ArrayList<String> = ArrayList()

    private var _binding: FragmentOlympiadsBinding? = null
  // This property is only valid between onCreateView and
  // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOlympiadsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        lifecycleScope.launch {
            getOlympiad()
        }

        println("Olympiads Fragment")

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    suspend fun getOlympiad() {
        return withContext(Dispatchers.IO) {
            try {
                val client = Socket("185.177.216.236", 20)
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.inputStream))

                println("Client sending 3$")
                output.println("3$")
                val gotString = input.readLine()
                println("Client receiving [${gotString}]")
                client.close()

                val olympiadStringData: List<String> = gotString.split("§")
                val weekOlympiadDataStrings: List<String> = olympiadStringData[0].split("%")
                val weekOlympiadDataDescription: List<String> = olympiadStringData[2].split("%")
                for (currentWeekDay in 0..6) {
                    if (weekOlympiadDataStrings[currentWeekDay] != "") {
                        val currentDayOlympiads: List<String> =
                            weekOlympiadDataStrings[currentWeekDay].split("$")
                        val currentDayDescriptions: List<String> =
                            weekOlympiadDataDescription[currentWeekDay].split("$")
                        for (currentOlympiadWeekDayId in 0..<(currentDayOlympiads.size - 1)) {
                            weekOlympiadsNames.add(currentDayOlympiads[currentOlympiadWeekDayId])
                            weekOlympiadsDescription.add(currentDayDescriptions[currentOlympiadWeekDayId])
                        }
                    }
                }

                if (olympiadStringData[1] != "None") {
                    val dynamicOlympiadsStringData: List<String> = olympiadStringData[1].split("$")
                    val dynamicOlympiadsStringDescription: List<String> = olympiadStringData[3].split("$")
                    for (currentDynamicOlympiad in dynamicOlympiadsStringData.indices) {
                        dynamicOlympiadsNames.add(dynamicOlympiadsStringData[currentDynamicOlympiad])
                        dynamicOlympiadsDescription.add(dynamicOlympiadsStringDescription[currentDynamicOlympiad])
                    }
                }

                //Можно удалить, если реализовать функцию разделению олимпиад по типам
                weekOlympiadsNames.addAll(dynamicOlympiadsNames)
                weekOlympiadsDescription.addAll(dynamicOlympiadsDescription)

                val recyclerView: RecyclerView = binding.recyclerView

                MainScope().launch {
                    recyclerView.layoutManager = LinearLayoutManager(context)
                    recyclerView.adapter = OlympiadsData(weekOlympiadsNames, weekOlympiadsDescription)
                }
            } catch (excep: ConnectException) {
                Toast.makeText(activity, "Не удалось получить ответ от сервера. ConnectionError", Toast.LENGTH_LONG).show()
            }
        }
    }
}

