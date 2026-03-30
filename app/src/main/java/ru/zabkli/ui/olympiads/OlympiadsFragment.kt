    package ru.zabkli.ui.olympiads
    import android.content.SharedPreferences
    import android.os.Bundle
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.TextView
    import androidx.appcompat.app.AppCompatActivity
    import androidx.fragment.app.Fragment
    import androidx.lifecycle.lifecycleScope
    import androidx.recyclerview.widget.LinearLayoutManager
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import org.json.JSONArray
    import org.json.JSONObject
    import ru.zabkli.R
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
    
        private lateinit var prefs: SharedPreferences
    
        // Кэшированные данные
        private var cachedOlympiads: List<Olympiad> = emptyList()
        private var isUsingCache: Boolean = false
    
        private var subjects = listOf("Все предметы")
        private var currentSubjectIndex = 0
        private var currentSortType = SortType.DATE // Сортировка по дате по умолчанию
        private var isSortAscending = false // По умолчанию новые олимпиады сверху
        private val baseUrl = "http://10.0.2.2:1717"
    
        private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val outputDateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
    
        companion object {
            private const val SETTINGS_ZABKLI = "settings_zabkli"
            private const val CACHE_OLYMPIADS = "cache_olympiads"
            private const val CACHE_TIMESTAMP = "cache_olympiads_timestamp"
            private const val TIMEOUT_MS = 10_000
            private const val CACHE_VALID_DURATION_MS = 24 * 60 * 60 * 1000L // 24 часа
            private const val SORT_TYPE_KEY = "sort_type"
            private const val SORT_ORDER_KEY = "sort_order"
        }
    
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            _binding = FragmentOlympiadsBinding.inflate(inflater, container, false)
    
            prefs = requireActivity().getSharedPreferences(SETTINGS_ZABKLI, AppCompatActivity.MODE_PRIVATE)
    
            loadSortSettings()
    
            adapter = OlympiadsAdapter(
                onItemClick = { olympiad ->
                    showOlympiadDetails(olympiad)
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
    
        private fun loadSortSettings() {
            val sortTypeOrdinal = prefs.getInt(SORT_TYPE_KEY, SortType.DATE.ordinal)
            currentSortType = SortType.values().getOrNull(sortTypeOrdinal) ?: SortType.DATE
            isSortAscending = prefs.getBoolean(SORT_ORDER_KEY, false)
        }
    
        private fun saveSortSettings() {
            prefs.edit().apply {
                putInt(SORT_TYPE_KEY, currentSortType.ordinal)
                putBoolean(SORT_ORDER_KEY, isSortAscending)
                apply()
            }
        }
    
        override fun onResume() {
            super.onResume()
            // Проверяем актуальность кэша при возврате на экран
            if (isUsingCache && isCacheExpired()) {
                showCacheExpiredMessage()
            }
        }
    
        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }
    
        private fun showOlympiadDetails(olympiad: Olympiad) {
            val view = layoutInflater.inflate(R.layout.dialog_olympiad_detail, null)
    
            val textName = view.findViewById<TextView>(R.id.detailName)
            val textSubject = view.findViewById<TextView>(R.id.detailSubject)
            val textDescription = view.findViewById<TextView>(R.id.detailDescription)
            val textDate = view.findViewById<TextView>(R.id.detailDate)
            val textClasses = view.findViewById<TextView>(R.id.detailClasses)
            val textAddedBy = view.findViewById<TextView>(R.id.detailAddedBy)
            val textApprovedBy = view.findViewById<TextView>(R.id.detailApprovedBy)
            val buttonLink =
                view.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonLink)
    
            // Заполняем данные
            textName.text = olympiad.name
            textSubject.text = olympiad.subject
    
            // Описание
            if (!olympiad.description.isNullOrEmpty()) {
                textDescription.text = olympiad.description
                textDescription.visibility = View.VISIBLE
            } else {
                textDescription.visibility = View.GONE
            }
    
            // Даты
            val startDate = formatDateString(olympiad.date_start)
            val endDate = formatDateString(olympiad.date_end)
            textDate.text =
                if (olympiad.date_end.isNullOrEmpty() || olympiad.date_start == olympiad.date_end) {
                    "Дата: $startDate"
                } else {
                    "Даты: $startDate – $endDate"
                }
    
            // Классы
            if (!olympiad.classes.isNullOrEmpty()) {
                textClasses.text = "Классы: ${olympiad.classes}"
                textClasses.visibility = View.VISIBLE
            } else {
                textClasses.visibility = View.GONE
            }
    
            // Кнопка ссылки
            if (!olympiad.link.isNullOrEmpty()) {
                buttonLink.visibility = View.VISIBLE
                buttonLink.setOnClickListener {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(olympiad.link)
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Не удалось открыть ссылку",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                buttonLink.visibility = View.GONE
            }

            if (olympiad.created_by.username != "admin") {
                // Кто добавил
                val creatorName = buildString {
                    if (!olympiad.created_by.first_name.isNullOrEmpty()) append(olympiad.created_by.first_name)
                    if (!olympiad.created_by.last_name.isNullOrEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append(olympiad.created_by.last_name)
                    }
                    if (isEmpty()) append(olympiad.created_by.username)
                }
                textAddedBy.text = "Добавил: $creatorName"
                textAddedBy.visibility = View.VISIBLE
            } else {
                textAddedBy.visibility = View.GONE
            }

            if (olympiad.approved_by != null) {
                // Кто утвердил
                val approverName = buildString {
                    if (!olympiad.approved_by.first_name.isNullOrEmpty()) append(olympiad.approved_by.first_name)
                    if (!olympiad.approved_by.last_name.isNullOrEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append(olympiad.approved_by.last_name)
                    }
                    if (isEmpty()) append(olympiad.approved_by.username)
                }
                textApprovedBy.text = "Утвердил: $approverName"
                textApprovedBy.visibility = View.VISIBLE
            } else {
                textApprovedBy.visibility = View.GONE
            }
    
            // Создаем и показываем MaterialAlertDialog
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setPositiveButton("Закрыть") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    
        // ─────────────────────────────────────────────
        // Кэширование
        // ─────────────────────────────────────────────
    
        private fun saveOlympiadsToCache(olympiads: List<Olympiad>) {
            try {
                val jsonArray = JSONArray()
                olympiads.forEach { olympiad ->
                    val obj = JSONObject().apply {
                        put("id", olympiad.id)
                        put("name", olympiad.name)
                        put("description", olympiad.description)
                        put("subject", olympiad.subject)
                        put("date_start", olympiad.date_start)
                        put("date_end", olympiad.date_end)
                        put("time", olympiad.time)
                        put("classes", olympiad.classes)
                        put("stage", olympiad.stage)
                        put("level", olympiad.level)
                        put("link", olympiad.link)
                        put("proposal_id", olympiad.proposal_id)
    
                        // created_by
                        val createdByObj = JSONObject().apply {
                            put("username", olympiad.created_by.username)
                            put("first_name", olympiad.created_by.first_name ?: "")
                            put("last_name", olympiad.created_by.last_name ?: "")
                        }
                        put("created_by", createdByObj)
    
                        // approved_by
                        if (olympiad.approved_by != null) {
                            val approvedByObj = JSONObject().apply {
                                put("username", olympiad.approved_by.username)
                                put("first_name", olympiad.approved_by.first_name ?: "")
                                put("last_name", olympiad.approved_by.last_name ?: "")
                            }
                            put("approved_by", approvedByObj)
                        } else {
                            put("approved_by", JSONObject.NULL)
                        }
                    }
                    jsonArray.put(obj)
                }
    
                prefs.edit().apply {
                    putString(CACHE_OLYMPIADS, jsonArray.toString())
                    putLong(CACHE_TIMESTAMP, System.currentTimeMillis())
                    apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    
        private fun loadCachedOlympiads(): Boolean {
            val cachedJson = prefs.getString(CACHE_OLYMPIADS, null)
            if (cachedJson != null) {
                try {
                    val olympiads = parseOlympiadsFromJson(cachedJson)
                    if (olympiads.isNotEmpty()) {
                        cachedOlympiads = olympiads
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return false
        }
    
        private fun parseOlympiadsFromJson(jsonString: String): List<Olympiad> {
            val olympiads = mutableListOf<Olympiad>()
            val jsonArray = JSONArray(jsonString)
    
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
    
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
    
                val olympiad = Olympiad(
                    id = obj.optInt("id"),
                    name = obj.optString("name", ""),
                    description = obj.optString("description").takeIf { it.isNotEmpty() },
                    subject = obj.optString("subject", ""),
                    date_start = obj.optString("date_start", ""),
                    date_end = obj.optString("date_end").takeIf { it.isNotEmpty() },
                    time = obj.optString("time").takeIf { it.isNotEmpty() },
                    classes = obj.optString("classes").takeIf { it.isNotEmpty() },
                    stage = obj.optString("stage").takeIf { it.isNotEmpty() },
                    level = if (obj.has("level") && !obj.isNull("level")) obj.optInt("level") else 1,
                    link = obj.optString("link").takeIf { it.isNotEmpty() && it != "null" },
                    created_by = createdBy,
                    approved_by = approvedBy,
                    proposal_id = if (obj.has("proposal_id") && !obj.isNull("proposal_id")) obj.optInt("proposal_id") else null
                )
                olympiads.add(olympiad)
            }
    
            return olympiads
        }
    
        private fun isCacheExpired(): Boolean {
            val timestamp = prefs.getLong(CACHE_TIMESTAMP, 0)
            return System.currentTimeMillis() - timestamp > CACHE_VALID_DURATION_MS
        }
    
        private fun getCacheTimestampText(): String {
            val timestamp = prefs.getLong(CACHE_TIMESTAMP, 0)
            if (timestamp == 0L) return ""
    
            val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            return dateFormat.format(java.util.Date(timestamp))
        }
    
        private fun showCacheInfo() {
            if (!isAdded || _binding == null) return
    
            val cacheTime = getCacheTimestampText()
            _binding?.textCacheInfo?.visibility = View.VISIBLE
    
            if (isCacheExpired()) {
                _binding?.textCacheInfo?.text = "Данные от $cacheTime (устарели)"
                _binding?.textCacheInfo?.setTextColor(requireContext().getColor(R.color.error_color))
            } else {
                _binding?.textCacheInfo?.text = "Данные от $cacheTime (офлайн)"
                _binding?.textCacheInfo?.setTextColor(requireContext().getColor(R.color.warning_color))
            }
        }
    
        private fun showCacheExpiredMessage() {
            if (!isAdded || _binding == null) return
    
            _binding?.textCacheInfo?.visibility = View.VISIBLE
            _binding?.textCacheInfo?.text = "Загруженные данные устарели (>24ч). Подключитесь к интернету для обновления"
            _binding?.textCacheInfo?.setTextColor(requireContext().getColor(R.color.error_color))
        }
    
        private fun hideCacheInfo() {
            if (!isAdded || _binding == null) return
            _binding?.textCacheInfo?.visibility = View.GONE
        }
    
        // ─────────────────────────────────────────────
        // Остальные методы
        // ─────────────────────────────────────────────
    
        private fun setupSubjectButton() {
            binding.buttonSubject.setOnClickListener {
                showSubjectDialog()
            }
            updateSubjectButtonText()
        }
    
        private fun setupSortButton() {
            binding.buttonSort.setOnClickListener {
                showSortDialog()
            }
            updateSortButtonText()
        }
    
        private fun showSortDialog() {
            val sortOptions = SortType.values().map { it.title }.toTypedArray()
            val currentIndex = SortType.values().indexOf(currentSortType)
    
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("Тип сортировки")
            builder.setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortType = SortType.values()[which]
                saveSortSettings() // Сохраняем настройки
                updateSortButtonText()
                filterOlympiadsLocally()
                dialog.dismiss()
            }
    
            builder.setNeutralButton(if (isSortAscending) "↑ По возрастанию" else "↓ По убыванию") { dialog, _ ->
                isSortAscending = !isSortAscending
                saveSortSettings() // Сохраняем настройки
                updateSortButtonText()
                filterOlympiadsLocally()
                dialog.dismiss()
                showSortDialog()
            }
    
            builder.show()
        }
    
        private fun updateSortButtonText() {
            val orderSymbol = if (isSortAscending) "↑" else "↓"
            binding.buttonSort.text = "$orderSymbol ${currentSortType.title}"
        }
    
        private fun filterOlympiadsLocally() {
            if (!isAdded || _binding == null) return
    
            val selectedSubject = subjects.getOrNull(currentSubjectIndex) ?: "Все предметы"
    
            // Сначала фильтруем по предмету
            var filteredList = if (selectedSubject == "Все предметы") {
                allOlympiads
            } else {
                allOlympiads.filter { it.subject == selectedSubject }
            }
    
            // Затем применяем сортировку
            filteredList = sortOlympiads(filteredList)
    
            olympiadsList = filteredList
            showOlympiads(olympiadsList)
        }
    
        private fun sortOlympiads(olympiads: List<Olympiad>): List<Olympiad> {
            return when (currentSortType) {
                SortType.DATE -> {
                    olympiads.sortedWith(compareBy({ parseDateForSort(it.date_start) }, { it.name }))
                        .let { if (isSortAscending) it else it.reversed() }
                }
                SortType.NAME -> {
                    olympiads.sortedBy { it.name.lowercase() }
                        .let { if (isSortAscending) it else it.reversed() }
                }
                SortType.SUBJECT -> {
                    olympiads.sortedWith(compareBy({ it.subject.lowercase() }, { it.name }))
                        .let { if (isSortAscending) it else it.reversed() }
                }
                SortType.CLASSES -> {
                    olympiads.sortedWith(compareBy({ parseClassForSort(it.classes) }, { it.name }))
                        .let { if (isSortAscending) it else it.reversed() }
                }
            }
        }
    
        private fun parseDateForSort(dateString: String?): Long {
            if (dateString.isNullOrBlank()) return 0L
            return try {
                inputDateFormat.parse(dateString)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    
        private fun parseClassForSort(classes: String?): Int {
            if (classes.isNullOrBlank()) return 0
            return try {
                classes.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
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
            hideCacheInfo()
            showLoading()
    
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        fetchOlympiads()
                    }
    
                    // FIX: Проверяем, что фрагмент всё ещё активен перед доступом к binding
                    if (!isAdded || _binding == null) return@launch
    
                    when (response) {
                        is Result.Success -> {
                            allOlympiads = response.data
                            saveOlympiadsToCache(response.data)
                            cachedOlympiads = response.data
                            isUsingCache = false
                            hideCacheInfo()
                            updateSubjectsFromData()
                            filterOlympiadsLocally()
                        }
                        is Result.Error -> {
                            // Пытаемся загрузить из кэша
                            if (loadCachedOlympiads()) {
                                isUsingCache = true
                                allOlympiads = cachedOlympiads
                                showCacheInfo()
                                updateSubjectsFromData()
                                filterOlympiadsLocally()
                            } else {
                                showError(response.type, response.errorMessage)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // FIX: Проверяем, что фрагмент всё ещё активен
                    if (!isAdded || _binding == null) return@launch
    
                    // Пытаемся загрузить из кэша при ошибке
                    if (loadCachedOlympiads()) {
                        isUsingCache = true
                        allOlympiads = cachedOlympiads
                        showCacheInfo()
                        updateSubjectsFromData()
                        filterOlympiadsLocally()
                    } else {
                        showError(ErrorType.UNKNOWN, e.toString())
                    }
                }
            }
        }
    
        private fun updateSubjectsFromData() {
            if (!isAdded || _binding == null) return
    
            val uniqueSubjects = allOlympiads
                .mapNotNull { it.subject.takeIf { s -> s.isNotEmpty() } }
                .distinct()
                .sorted()
    
            subjects = listOf("Все предметы") + uniqueSubjects
    
            if (currentSubjectIndex >= subjects.size) {
                currentSubjectIndex = 0
            }
            updateSubjectButtonText()
        }
    
        private suspend fun fetchOlympiads(): Result<List<Olympiad>> = withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/public/olympiads")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
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
                                id = obj.optInt("id"),
                                name = obj.optString("name", "").takeIf { it != "null" } ?: "",
                                description = obj.optString("description").takeIf { it.isNotEmpty() && it != "null" },
                                subject = obj.optString("subject", "").takeIf { it != "null" } ?: "",
                                date_start = obj.optString("date_start", "").takeIf { it != "null" } ?: "",
                                date_end = obj.optString("date_end").takeIf { it.isNotEmpty() && it != "null" },
                                time = obj.optString("time").takeIf { it.isNotEmpty() && it != "null" },
                                classes = obj.optString("classes").takeIf { it.isNotEmpty() && it != "null" },
                                stage = obj.optString("stage").takeIf { it.isNotEmpty() && it != "null" },
                                level = if (obj.has("level") && !obj.isNull("level")) obj.optInt("level") else 1,
                                link = obj.optString("link").takeIf { it.isNotEmpty() && it != "null" },
                                created_by = createdBy,
                                approved_by = approvedBy,
                                proposal_id = if (obj.has("proposal_id") && !obj.isNull("proposal_id")) obj.optInt("proposal_id") else null
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
            if (!isAdded || _binding == null) return
            hideAllErrors()
            hideCacheInfo()
            _binding?.recyclerView?.visibility = View.GONE
            _binding?.progressBar?.visibility = View.VISIBLE
        }
    
        private fun showOlympiads(olympiads: List<Olympiad>) {
            if (!isAdded || _binding == null) return
            hideAllErrors()
            hideCacheInfo()
            _binding?.progressBar?.visibility = View.GONE
            olympiadsList = olympiads
    
            if (olympiads.isEmpty()) {
                _binding?.recyclerView?.visibility = View.GONE
                _binding?.errorNoOlymp?.visibility = View.VISIBLE
            } else {
                _binding?.errorNoOlymp?.visibility = View.GONE
                _binding?.recyclerView?.visibility = View.VISIBLE
                adapter.submitList(olympiads)
            }
        }
    
        private fun showError(errorType: ErrorType, errorMessage: String? = null) {
            // FIX: Проверяем, что фрагмент всё ещё активен перед доступом к binding
            if (!isAdded || _binding == null) return
    
            hideAllErrors()
            hideCacheInfo()
            _binding?.recyclerView?.visibility = View.GONE
            _binding?.errorNoOlymp?.visibility = View.GONE
    
            when (errorType) {
                ErrorType.NO_INTERNET -> _binding?.errorNoInternet?.visibility = View.VISIBLE
                ErrorType.SERVER -> _binding?.errorServer?.visibility = View.VISIBLE
                ErrorType.UNKNOWN -> {
                    _binding?.errorUnknown?.visibility = View.VISIBLE
                    if (errorMessage != null) {
                        _binding?.textForReport?.text = errorMessage
                    }
                }
            }
        }
    
        private fun hideAllErrors() {
            if (!isAdded || _binding == null) return
            _binding?.errorNoInternet?.visibility = View.GONE
            _binding?.errorServer?.visibility = View.GONE
            _binding?.errorNoOlymp?.visibility = View.GONE
            _binding?.errorUnknown?.visibility = View.GONE
        }
    
        enum class ErrorType {
            NO_INTERNET,
            SERVER,
            UNKNOWN
        }
        enum class SortType(val title: String) {
            DATE("По дате"),
            NAME("По названию"),
            SUBJECT("По предмету"),
            CLASSES("По классам")
        }
    
        sealed class Result<out T> {
            data class Success<out T>(val data: T) : Result<T>()
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
            private val dateFormatter: (String?) -> String
        ) : androidx.recyclerview.widget.RecyclerView.Adapter<OlympiadsAdapter.ViewHolder>() {
    
            class ViewHolder(
                private val binding: ItemOlympiadBinding,
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