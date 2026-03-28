package ru.zabkli.ui.olympiads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.zabkli.databinding.FragmentOlympiadsBinding
import ru.zabkli.databinding.ItemOlympiadBinding
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OlympiadsFragment : Fragment() {

    private var _binding: FragmentOlympiadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: OlympiadsAdapter
    private var allOlympiads: List<Olympiad> = emptyList()
    private var olympiadsList: List<Olympiad> = emptyList()

    private var subjects = listOf("Все предметы")
    private var currentSubjectIndex = 0
    private val baseUrl = "http://10.0.2.2:1717"

    private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputDateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOlympiadsBinding.inflate(inflater, container, false)

        adapter = OlympiadsAdapter(
            onItemClick = { olympiad ->
                // TODO: навигация на детальный экран
            },
            dateFormatter = ::formatDateString
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        setupSubjectButton()
        setupSortButton()
        loadAllOlympiads()

        return binding.root
    }

    private fun setupSubjectButton() {
        binding.buttonSubject.setOnClickListener {
            showSubjectDialog()
        }
        updateSubjectButtonText()
    }

    private fun setupSortButton() {
        binding.buttonSort.setOnClickListener {
            android.widget.Toast.makeText(
                requireContext(),
                "Сортировка в разработке",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showSubjectDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Выберите предмет")
        builder.setSingleChoiceItems(subjects.toTypedArray(), currentSubjectIndex) { dialog, which ->
            currentSubjectIndex = which
            updateSubjectButtonText()
            filterOlympiadsLocally()
            dialog.dismiss()
        }
        builder.show()
    }

    private fun updateSubjectButtonText() {
        val index = currentSubjectIndex.coerceIn(0 until subjects.size)
        binding.buttonSubject.text = subjects[index]
    }

    private fun loadAllOlympiads() {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            try {
                val response = withContext(Dispatchers.IO) {
                    fetchOlympiads()
                }
                when (response) {
                    is Result.Success -> {
                        allOlympiads = response.data
                        updateSubjectsFromData()
                        filterOlympiadsLocally()
                    }
                    is Result.Error -> showError(response.type)
                }
            } catch (e: Exception) {
                showError(ErrorType.UNKNOWN)
            }
        }
    }

    private fun updateSubjectsFromData() {
        val uniqueSubjects = allOlympiads
            .mapNotNull { it.subject.takeIf { s -> s.isNotEmpty() } }
            .distinct()
            .sorted()

        subjects = listOf("Все предметы") + uniqueSubjects

        if (currentSubjectIndex >= subjects.size) {
            currentSubjectIndex = 0
        }
        if (_binding != null) {
            updateSubjectButtonText()
        }
    }

    private fun filterOlympiadsLocally() {
        val selectedSubject = subjects.getOrNull(currentSubjectIndex) ?: "Все предметы"
        olympiadsList = if (selectedSubject == "Все предметы") {
            allOlympiads
        } else {
            allOlympiads.filter { it.subject == selectedSubject }
        }
        showOlympiads(olympiadsList)
    }

    // FIX 1: функция больше не возвращает Result напрямую из withContext —
    //        она просто suspend и возвращает Result. Логика исключений перенесена
    //        во внешний try/catch в loadAllOlympiads.
    //        Главное исправление: binding.textForReport НЕ трогаем здесь (фоновый поток).
    //        Текст ошибки сохраняем в переменную и выставляем на главном потоке.
    private suspend fun fetchOlympiads(): Result<List<Olympiad>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/public/olympiads")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            connection.disconnect()

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                val success = json.optBoolean("success", false)
                if (!success) return@withContext Result.Error(ErrorType.SERVER)

                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val olympiads = mutableListOf<Olympiad>()

                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)

                    val createdByObj = obj.optJSONObject("created_by")
                    val createdBy = if (createdByObj != null) {
                        UserInfo(
                            username = createdByObj.getString("username"),
                            first_name = createdByObj.optString("first_name").takeIf { it.isNotEmpty() },
                            last_name = createdByObj.optString("last_name").takeIf { it.isNotEmpty() }
                        )
                    } else {
                        UserInfo(username = "unknown")
                    }

                    val approvedByObj = obj.optJSONObject("approved_by")
                    val approvedBy = if (approvedByObj != null) {
                        UserInfo(
                            username = approvedByObj.getString("username"),
                            first_name = approvedByObj.optString("first_name").takeIf { it.isNotEmpty() },
                            last_name = approvedByObj.optString("last_name").takeIf { it.isNotEmpty() }
                        )
                    } else null

                    olympiads.add(
                        Olympiad(
                            id = obj.getInt("id"),
                            name = obj.getString("name"),
                            description = obj.optString("description").takeIf { it.isNotEmpty() },
                            subject = obj.getString("subject"),
                            date_start = obj.getString("date_start"),
                            date_end = obj.optString("date_end").takeIf { it.isNotEmpty() },
                            time = obj.optString("time").takeIf { it.isNotEmpty() },
                            classes = obj.getString("classes"),
                            stage = obj.optString("stage").takeIf { it.isNotEmpty() },
                            level = if (obj.has("level") && !obj.isNull("level")) obj.getInt("level") else 1,
                            link = obj.optString("link").takeIf { it.isNotEmpty() && it != "null" },
                            created_by = createdBy,
                            approved_by = approvedBy,
                            proposal_id = if (obj.has("proposal_id") && !obj.isNull("proposal_id")) obj.getInt("proposal_id") else null
                        )
                    )
                }
                Result.Success(olympiads)
            } else {
                Result.Error(ErrorType.SERVER)
            }
        } catch (e: ConnectException) {
            Result.Error(ErrorType.NO_INTERNET)
        } catch (e: SocketTimeoutException) {
            Result.Error(ErrorType.NO_INTERNET)
        } catch (e: Exception) {
            // FIX 1: сохраняем текст ошибки, чтобы выставить его на главном потоке
            Result.Error(ErrorType.UNKNOWN, e.toString())
        }
    }

    private fun formatDateString(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""

        return try {
            val date = inputDateFormat.parse(dateString) ?: return dateString
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val eventYear = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)

            if (eventYear == currentYear) {
                outputDateFormat.format(date)
            } else {
                SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            dateString
        }
    }

    private fun showLoading() {
        hideAllErrors()
        binding.recyclerView.visibility = View.GONE
    }

    private fun showOlympiads(olympiads: List<Olympiad>) {
        hideAllErrors()
        olympiadsList = olympiads

        if (olympiads.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.errorNoOlymp.visibility = View.VISIBLE
        } else {
            binding.errorNoOlymp.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.submitList(olympiads)
        }
    }

    private fun showError(errorType: ErrorType, errorMessage: String? = null) {
        hideAllErrors()
        binding.recyclerView.visibility = View.GONE
        binding.errorNoOlymp.visibility = View.GONE

        when (errorType) {
            ErrorType.NO_INTERNET -> binding.errorNoInternet.visibility = View.VISIBLE
            ErrorType.SERVER -> binding.errorServer.visibility = View.VISIBLE
            // FIX 1: выставляем текст ошибки здесь, на главном потоке
            ErrorType.UNKNOWN -> {
                binding.errorUnknown.visibility = View.VISIBLE
                if (errorMessage != null) {
                    binding.textForReport.text = errorMessage
                }
            }
        }
    }

    private fun hideAllErrors() {
        binding.errorNoInternet.visibility = View.GONE
        binding.errorServer.visibility = View.GONE
        binding.errorNoOlymp.visibility = View.GONE
        binding.errorUnknown.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class ErrorType { NO_INTERNET, SERVER, UNKNOWN }

    sealed class Result<out T> {
        data class Success<out T>(val data: T) : Result<T>()
        // FIX 1: добавлено поле errorMessage для передачи текста ошибки из IO-потока
        data class Error(val type: ErrorType, val errorMessage: String? = null) : Result<Nothing>()
    }

    data class UserInfo(
        val username: String,
        val first_name: String? = null,
        val last_name: String? = null
    )

    data class Olympiad(
        val id: Int,
        val name: String,
        val description: String?,
        val subject: String,
        val date_start: String,
        val date_end: String?,
        val time: String?,
        val classes: String?,
        val stage: String?,
        val level: Int?,
        val link: String?,
        val created_by: UserInfo,
        val approved_by: UserInfo?,
        val proposal_id: Int?
    )

    private class OlympiadsAdapter(
        private var olympiads: List<Olympiad> = emptyList(),
        private val onItemClick: (Olympiad) -> Unit,
        // FIX 2: тип изменён с (String) -> String на (String?) -> String,
        //        чтобы соответствовать formatDateString и принимать nullable date_end
        private val dateFormatter: (String?) -> String
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<OlympiadsAdapter.ViewHolder>() {

        class ViewHolder(
            private val binding: ItemOlympiadBinding,
            // FIX 2: тип изменён с (String) -> String на (String?) -> String
            private val dateFormatter: (String?) -> String
        ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

            fun bind(olympiad: Olympiad, onClick: (Olympiad) -> Unit) {
                binding.olympiadName.text = olympiad.name
                binding.tagSubject.text = olympiad.subject

                if (olympiad.classes == null) {
                    binding.tagClasses.visibility = View.GONE
                } else {
                    val classRange = olympiad.classes.split("-").map { it.trim() }
                    binding.tagClasses.text =
                        if (classRange.size == 2 && classRange[0] == classRange[1]) {
                            "${classRange[0]} класс"
                        } else {
                            "${olympiad.classes} классы"
                        }
                }

                val startDate = dateFormatter(olympiad.date_start)
                binding.tagDate.text = if (olympiad.date_end.isNullOrEmpty() || olympiad.date_start == olympiad.date_end) {
                    startDate
                } else {
                    // FIX 2: теперь тип dateFormatter допускает String?, ошибки компиляции нет
                    val endDate = dateFormatter(olympiad.date_end)
                    "$startDate–$endDate"
                }

                if (!olympiad.time.isNullOrEmpty()) {
                    binding.tagTime.visibility = View.VISIBLE
                    binding.tagTime.text = olympiad.time
                } else {
                    binding.tagTime.visibility = View.GONE
                }

                binding.root.setOnClickListener { onClick(olympiad) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemOlympiadBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding, dateFormatter)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(olympiads[position], onItemClick)
        }

        override fun getItemCount(): Int = olympiads.size

        fun submitList(newOlympiads: List<Olympiad>) {
            olympiads = newOlympiads
            notifyDataSetChanged()
        }
    }
}