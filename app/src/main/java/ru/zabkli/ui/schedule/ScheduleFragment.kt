package ru.zabkli.ui.schedule

import android.content.SharedPreferences
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import ru.zabkli.R
import ru.zabkli.databinding.FragmentScheduleBinding
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Calendar

class ScheduleFragment : Fragment() {
    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScheduleAdapter
    private lateinit var prefs: SharedPreferences

    // Имена классов, индекс соответствует userUsingClassId из SharedPreferences
    private val classNames = arrayOf(
        "5 класс I гр.", "6 класс I гр.", "7 класс I гр.", "8 класс I гр.",
        "5 класс II гр.", "6 класс II гр.", "7 класс II гр.", "8 класс II гр.",
        "9 А  I гр.", "9 Б  I гр.", "10 А  I гр.", "10 Б  I гр.",
        "11 А  I гр.", "11 Б  I гр.", "9 А  II гр.", "9 Б  II гр.",
        "10 А  II гр.", "10 Б  II гр.", "11 А  II гр.", "11 Б  II гр."
    )

    // Дни недели: 0 = понедельник (API), отображаем пн–сб
    private val weekDayNames = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"
    )

    // Текущий API-weekday (0=пн … 5=сб), инициализируется по системному времени
    private var currentWeekday: Int = todayApiWeekday()

    private val mainHandler = Handler(Looper.getMainLooper())

    // Кэшированные данные
    private var cachedSchedule: List<Schedule> = emptyList()
    private var isUsingCache: Boolean = false
    private var isInitialLoad: Boolean = true

    companion object {
        private const val BASE_URL = "http://10.0.2.2:1717"
        private const val SETTINGS_ZABKLI = "settings_zabkli"
        private const val CACHE_SCHEDULE = "cache_schedule"
        private const val CACHE_TIMESTAMP = "cache_timestamp"
        private const val CACHE_CLASS_ID = "cache_class_id"
        private const val TIMEOUT_MS = 8_000
        private const val CACHE_VALID_DURATION_MS = 24 * 60 * 60 * 1000L // 24 часа

        fun todayApiWeekday(): Int = when (Calendar.getInstance()[Calendar.DAY_OF_WEEK]) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0 // воскресенье → показываем понедельник
        }
    }

    data class UserInfo(
        val username: String,
        val first_name: String? = null,
        val last_name: String? = null
    )

    data class Schedule(
        val id: Int,
        val className: String,
        val weekday: Int,
        val lessonNum: Int,
        val subject: String,
        val teacher: String?,
        val room: String?,
        val timeStart: String?,
        val timeEnd: String?,
        val status: String = "active",  // "active" или "cancelled"
        val created_by: UserInfo,
        val approved_by: UserInfo?
    )

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)

        prefs = requireActivity().getSharedPreferences(SETTINGS_ZABKLI, AppCompatActivity.MODE_PRIVATE)

        setupRecyclerView()
        setupNavButtons()
        loadCurrentDay()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Проверяем актуальность кэша при возврате на экран
        if (isUsingCache && isCacheExpired()) {
            showCacheExpiredMessage()
        }
    }

    // ─────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            emptyList(),
            onItemClick = { schedule ->
                showScheduleDetails(schedule)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun showScheduleDetails(schedule: Schedule) {
        val view = layoutInflater.inflate(R.layout.dialog_schedule_detail, null)

        val textSubject = view.findViewById<TextView>(R.id.detailSubject)
        val textLessonNum = view.findViewById<TextView>(R.id.detailLessonNum)
        val textTime = view.findViewById<TextView>(R.id.detailTime)
        val textTeacher = view.findViewById<TextView>(R.id.detailTeacher)
        val textRoom = view.findViewById<TextView>(R.id.detailRoom)
        val textStatus = view.findViewById<TextView>(R.id.detailStatus)
        val textAddedBy = view.findViewById<TextView>(R.id.detailAddedBy)
        val textApprovedBy = view.findViewById<TextView>(R.id.detailApprovedBy)

        // Заполняем данные
        textSubject.text = schedule.subject

        // Номер урока
        textLessonNum.text = "Урок № ${schedule.lessonNum}"

        // Время
        if (!schedule.timeStart.isNullOrEmpty() && !schedule.timeEnd.isNullOrEmpty()) {
            textTime.text = "Время: ${schedule.timeStart} – ${schedule.timeEnd}"
            textTime.visibility = View.VISIBLE
        } else {
            textTime.visibility = View.GONE
        }

        // Учитель
        if (!schedule.teacher.isNullOrEmpty()) {
            textTeacher.text = "Учитель: ${schedule.teacher}"
            textTeacher.visibility = View.VISIBLE
        } else {
            textTeacher.visibility = View.GONE
        }

        // Кабинет
        if (!schedule.room.isNullOrEmpty()) {
            textRoom.text = "Кабинет: ${schedule.room}"
            textRoom.visibility = View.VISIBLE
        } else {
            textRoom.visibility = View.GONE
        }

        // Статус (отмена)
        if (schedule.status == "cancelled") {
            textStatus.text = "Статус: ОТМЕНЁН"
            textStatus.visibility = View.VISIBLE
        } else {
            textStatus.visibility = View.GONE
        }

        if (schedule.created_by.username != "admin") {
            // Кто добавил
            val creatorName = buildString {
                if (!schedule.created_by.first_name.isNullOrEmpty()) append(schedule.created_by.first_name)
                if (!schedule.created_by.last_name.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append(schedule.created_by.last_name)
                }
                if (isEmpty()) append(schedule.created_by.username)
            }
            textAddedBy.text = "Добавил: $creatorName"
            textAddedBy.visibility = View.VISIBLE
        } else {
            textAddedBy.visibility = View.GONE
        }

        if (schedule.approved_by != null) {
            // Кто утвердил
            val approverName = buildString {
                if (!schedule.approved_by.first_name.isNullOrEmpty()) append(schedule.approved_by.first_name)
                if (!schedule.approved_by.last_name.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append(schedule.approved_by.last_name)
                }
                if (isEmpty()) append(schedule.approved_by.username)
            }
            textApprovedBy.text = "Утвердил: $approverName"
            textApprovedBy.visibility = View.VISIBLE
        } else {
            textApprovedBy.visibility = View.GONE
        }

        // Показываем диалог
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton("Закрыть") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupNavButtons() {
        binding.backButton.setOnClickListener {
            currentWeekday = if (currentWeekday <= 0) 5 else currentWeekday - 1
            loadCurrentDay()
        }
        binding.forwardButton.setOnClickListener {
            currentWeekday = if (currentWeekday >= 5) 0 else currentWeekday + 1
            loadCurrentDay()
        }
    }

    // ─────────────────────────────────────────────
    // Кэширование
    // ─────────────────────────────────────────────

    private fun saveScheduleToCache(lessons: List<Schedule>, classId: Int) {
        try {
            val jsonArray = JSONArray()
            lessons.forEach { lesson ->
                val obj = JSONObject().apply {
                    put("id", lesson.id)
                    put("class_name", lesson.className)
                    put("weekday", lesson.weekday)
                    put("lesson_num", lesson.lessonNum)
                    put("subject", lesson.subject)
                    put("teacher", lesson.teacher)
                    put("room", lesson.room)
                    put("time_start", lesson.timeStart)
                    put("time_end", lesson.timeEnd)
                    put("status", lesson.status)

                    // created_by
                    val createdByObj = JSONObject().apply {
                        put("username", lesson.created_by.username)
                        put("first_name", lesson.created_by.first_name ?: "")
                        put("last_name", lesson.created_by.last_name ?: "")
                    }
                    put("created_by", createdByObj)

                    // approved_by
                    if (lesson.approved_by != null) {
                        val approvedByObj = JSONObject().apply {
                            put("username", lesson.approved_by.username)
                            put("first_name", lesson.approved_by.first_name ?: "")
                            put("last_name", lesson.approved_by.last_name ?: "")
                        }
                        put("approved_by", approvedByObj)
                    } else {
                        put("approved_by", JSONObject.NULL)
                    }
                }
                jsonArray.put(obj)
            }

            prefs.edit().apply {
                putString(CACHE_SCHEDULE, jsonArray.toString())
                putLong(CACHE_TIMESTAMP, System.currentTimeMillis())
                putInt(CACHE_CLASS_ID, classId)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCachedSchedule(): Boolean {
        val cachedJson = prefs.getString(CACHE_SCHEDULE, null)
        val cachedClassId = prefs.getInt(CACHE_CLASS_ID, -1)
        val currentClassId = prefs.getInt("userUsingClassId", 5)

        if (cachedJson != null && cachedClassId == currentClassId) {
            try {
                val lessons = parseScheduleFromJson(cachedJson)
                if (lessons.isNotEmpty()) {
                    cachedSchedule = lessons
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun parseScheduleFromJson(jsonString: String): List<Schedule> {
        val lessons = mutableListOf<Schedule>()
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            // Парсинг created_by и approved_by (как выше в fetchSchedule)
            val createdByObj = obj.optJSONObject("created_by")
            val createdBy = if (createdByObj != null) {
                UserInfo(
                    username = createdByObj.getString("username"),
                    first_name = createdByObj.optString("first_name").takeIf { it.isNotEmpty() },
                    last_name = createdByObj.optString("last_name").takeIf { it.isNotEmpty() }
                )
            } else UserInfo(username = "unknown")

            val approvedByObj = obj.optJSONObject("approved_by")
            val approvedBy = if (approvedByObj != null) {
                UserInfo(
                    username = approvedByObj.getString("username"),
                    first_name = approvedByObj.optString("first_name").takeIf { it.isNotEmpty() },
                    last_name = approvedByObj.optString("last_name").takeIf { it.isNotEmpty() }
                )
            } else null

            val lesson = Schedule(
                id = obj.optInt("id"),
                className = obj.optString("class_name", ""),
                weekday = obj.optInt("weekday"),
                lessonNum = obj.optInt("lesson_num"),
                subject = obj.optString("subject", ""),
                teacher = if (obj.has("teacher") && !obj.isNull("teacher")) obj.getString("teacher") else null,
                room = if (obj.has("room") && !obj.isNull("room")) obj.getString("room") else null,
                timeStart = if (obj.has("time_start") && !obj.isNull("time_start")) obj.getString("time_start") else null,
                timeEnd = if (obj.has("time_end") && !obj.isNull("time_end")) obj.getString("time_end") else null,
                status = if (obj.has("status") && !obj.isNull("status")) obj.getString("status") else "active",
                created_by = createdBy,
                approved_by = approvedBy
            )
            lessons.add(lesson)
        }
        return lessons
    }

    // ─────────────────────────────────────────────
    // Встроенные данные (res/raw/schedule.json)
    // ─────────────────────────────────────────────

    private fun loadBuiltinSchedule(): Boolean {
        return try {
            val inputStream = resources.openRawResource(R.raw.schedule)
            val jsonString = inputStream.bufferedReader().readText()
            inputStream.close()
            val lessons = parseScheduleFromJson(jsonString)
            if (lessons.isNotEmpty()) {
                cachedSchedule = lessons
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun isCacheExpired(): Boolean {
        val timestamp = prefs.getLong(CACHE_TIMESTAMP, 0)
        return System.currentTimeMillis() - timestamp > CACHE_VALID_DURATION_MS
    }

    private fun getCacheTimestampText(): String {
        val timestamp = prefs.getLong(CACHE_TIMESTAMP, 0)
        if (timestamp == 0L) return ""

        val dateFormat = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }

    private fun showCacheInfo() {
        val cacheTime = getCacheTimestampText()
        binding.textCacheInfo.visibility = View.VISIBLE

        if (isCacheExpired()) {
            binding.textCacheInfo.text = "Данные от $cacheTime (устарели)"
            binding.textCacheInfo.setTextColor(requireContext().getColor(R.color.error_color))
        } else {
            binding.textCacheInfo.text = "Данные от $cacheTime (офлайн)"
            binding.textCacheInfo.setTextColor(requireContext().getColor(R.color.warning_color))
        }
    }

    private fun showCacheExpiredMessage() {
        binding.textCacheInfo.visibility = View.VISIBLE
        binding.textCacheInfo.text = "Загруженные данные устарели (>24ч). Подключитесь к интернету для обновления"
        binding.textCacheInfo.setTextColor(requireContext().getColor(R.color.error_color))
    }

    private fun hideCacheInfo() {
        binding.textCacheInfo.visibility = View.GONE
    }

    // ─────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────

    private fun loadCurrentDay() {
        _binding?.textDay?.text = weekDayNames[currentWeekday]

        val classId = prefs.getInt("userUsingClassId", 5)
        val className = classNames.getOrElse(classId) { "10A" }

        hideAllErrors()
        hideCacheInfo()
        showLoading(true)

        Thread {
            val result = fetchSchedule(currentWeekday, className)

            // При первом заходе — параллельно грузим следующий день
            if (isInitialLoad) {
                isInitialLoad = false
                val nextWeekday = if (currentWeekday >= 5) 0 else currentWeekday + 1
                Thread {
                    fetchSchedule(nextWeekday, className).let { nextResult ->
                        if (nextResult is ScheduleResult.Success) {
                            // Объединяем с основным кэшем (если он уже сохранён)
                            val existing = cachedSchedule.toMutableList()
                            val nextDayIds = nextResult.allLessons.map { it.id }.toSet()
                            val merged = existing.filter { it.id !in nextDayIds } + nextResult.allLessons
                            cachedSchedule = merged.sortedBy { it.lessonNum }
                        }
                    }
                }.start()
            }

            if (!isAdded) return@Thread

            mainHandler.post {
                if (!isAdded || _binding == null) return@post

                showLoading(false)
                when (result) {
                    is ScheduleResult.Success -> {
                        // Сохраняем в кэш все уроки
                        saveScheduleToCache(result.allLessons, classId)
                        cachedSchedule = result.allLessons
                        isUsingCache = false
                        hideCacheInfo()

                        val dayLessons = result.allLessons
                            .filter { it.weekday == currentWeekday && it.className == className }
                            .sortedBy { it.lessonNum }
                        if (dayLessons.isEmpty()) {
                            showError(binding.errorNoLessons)
                        } else {
                            adapter.updateData(dayLessons)
                            updateNextLesson(dayLessons)
                        }
                    }
                    is ScheduleResult.NoInternet, is ScheduleResult.ServerError -> {
                        // 1. Кэш → 2. Встроенные данные из res/raw/schedule.json
                        val loaded = loadCachedSchedule() || loadBuiltinSchedule()

                        if (loaded) {
                            val fromCache = prefs.getString(CACHE_SCHEDULE, null) != null
                            if (fromCache) {
                                isUsingCache = true
                                showCacheInfo()
                            } else {
                                binding.textCacheInfo.visibility = View.VISIBLE
                                binding.textCacheInfo.text = "Показаны встроенные данные расписания"
                                binding.textCacheInfo.setTextColor(requireContext().getColor(R.color.warning_color))
                            }

                            val dayLessons = cachedSchedule
                                .filter { it.weekday == currentWeekday && it.className == className }
                                .sortedBy { it.lessonNum }
                            if (dayLessons.isEmpty()) {
                                showError(binding.errorNoLessons)
                            } else {
                                adapter.updateData(dayLessons)
                                updateNextLesson(dayLessons)
                            }
                        } else {
                            if (result is ScheduleResult.NoInternet) {
                                showError(binding.errorNoInternet)
                            } else {
                                showError(binding.errorServer)
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun updateNextLesson(lessons: List<Schedule>) {
        val binding = _binding ?: return

        val todayWeekday = todayApiWeekday()

        if (currentWeekday != todayWeekday) {
            binding.nextLessonCard.root.visibility = View.GONE
            binding.labelNextLesson.visibility = View.GONE
            return
        }

        val calendar = Calendar.getInstance()
        val currentTimeMinutes = calendar[Calendar.HOUR_OF_DAY] * 60 + calendar[Calendar.MINUTE]

        // Ищем следующий урок (игнорируем отменённые)
        val nextLesson = lessons
            .filter { it.timeStart != null && it.status != "cancelled" }
            .firstOrNull { parseMinutes(it.timeStart!!) > currentTimeMinutes }

        if (nextLesson != null) {
            binding.labelNextLesson.visibility = View.VISIBLE
            binding.nextLessonCard.root.visibility = View.VISIBLE

            // Обращаемся к элементам внутри включенного макета:
            val itemBinding = binding.nextLessonCard

            itemBinding.lessonNum.text = nextLesson.lessonNum.toString()
            itemBinding.subjectName.text = nextLesson.subject
            itemBinding.lessonTime.text = "${nextLesson.timeStart} – ${nextLesson.timeEnd}"

            // Настраиваем тег учителя
            if (!nextLesson.teacher.isNullOrBlank()) {
                itemBinding.teacherName.visibility = View.VISIBLE
                itemBinding.teacherName.text = nextLesson.teacher
            } else {
                itemBinding.teacherName.visibility = View.GONE
            }

            // Настраиваем тег кабинета
            if (!nextLesson.room.isNullOrBlank()) {
                itemBinding.roomNumber.visibility = View.VISIBLE
                itemBinding.roomNumber.text = "Каб. ${nextLesson.room}"
            } else {
                itemBinding.roomNumber.visibility = View.GONE
            }

            // Прячем статус отмены и сбрасываем стили (ведь отменённые мы уже отфильтровали)
            itemBinding.lessonStatus.visibility = View.GONE
            itemBinding.subjectName.paintFlags = itemBinding.subjectName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            itemBinding.subjectName.setTextColor(requireContext().getColor(android.R.color.black))

        } else {
            binding.labelNextLesson.visibility = View.GONE
            binding.nextLessonCard.root.visibility = View.GONE
        }
    }

    private fun parseMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }

    // ─────────────────────────────────────────────
    // Network
    // ─────────────────────────────────────────────

    private fun fetchSchedule(weekday: Int, className: String): ScheduleResult {
        return try {
            // Запрашиваем все дни недели для кэширования
            val allLessons = mutableListOf<Schedule>()

            for (day in 0..5) {
                val url = URL("$BASE_URL/public/schedule/$day?class_name=$className")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return ScheduleResult.ServerError
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(body)
                val success = json.optBoolean("success", false)
                if (!success) {
                    return ScheduleResult.ServerError
                }

                val dataArray = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)

                    // 🔹 Парсим created_by
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

                    // 🔹 Парсим approved_by
                    val approvedByObj = obj.optJSONObject("approved_by")
                    val approvedBy = if (approvedByObj != null) {
                        UserInfo(
                            username = approvedByObj.getString("username"),
                            first_name = approvedByObj.optString("first_name").takeIf { it.isNotEmpty() },
                            last_name = approvedByObj.optString("last_name").takeIf { it.isNotEmpty() }
                        )
                    } else null

                    // 🔹 Создаём объект Schedule с новыми полями
                    val lesson = Schedule(
                        id = obj.optInt("id"),
                        className = obj.optString("class_name", ""),
                        weekday = obj.optInt("weekday"),
                        lessonNum = obj.optInt("lesson_num"),
                        subject = obj.optString("subject", ""),
                        teacher = if (obj.has("teacher") && !obj.isNull("teacher")) obj.getString("teacher") else null,
                        room = if (obj.has("room") && !obj.isNull("room")) obj.getString("room") else null,
                        timeStart = if (obj.has("time_start") && !obj.isNull("time_start")) obj.getString("time_start") else null,
                        timeEnd = if (obj.has("time_end") && !obj.isNull("time_end")) obj.getString("time_end") else null,
                        status = if (obj.has("status") && !obj.isNull("status")) obj.getString("status") else "active",
                        created_by = createdBy,      // ← передаём
                        approved_by = approvedBy     // ← передаём
                    )
                    allLessons.add(lesson)
                }
            }

            ScheduleResult.Success(allLessons.sortedBy { it.lessonNum })
        } catch (e: UnknownHostException) {
            ScheduleResult.NoInternet
        } catch (e: SocketTimeoutException) {
            ScheduleResult.NoInternet
        } catch (e: Exception) {
            ScheduleResult.ServerError
        }
    }

    // ─────────────────────────────────────────────
    // UI Helpers
    // ─────────────────────────────────────────────

    private fun showLoading(show: Boolean) {
        _binding?.let { binding ->
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        }
    }

    private fun hideAllErrors() {
        _binding?.let { binding ->
            binding.errorNoInternet.visibility = View.GONE
            binding.errorServer.visibility = View.GONE
            binding.errorNoLessons.visibility = View.GONE
        }
    }

    private fun showError(errorView: View) {
        _binding?.recyclerView?.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────
    // Result sealed class
    // ─────────────────────────────────────────────

    sealed class ScheduleResult {
        data class Success(val allLessons: List<Schedule>) : ScheduleResult()
        object NoInternet : ScheduleResult()
        object ServerError : ScheduleResult()
    }

    // ─────────────────────────────────────────────
    // Внутренний адаптер
    // ─────────────────────────────────────────────

    private class ScheduleAdapter(
        private var lessons: List<Schedule>,
        private val onItemClick: (Schedule) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val lessonCard: MaterialCardView = view.findViewById(R.id.lessonCard)
            val lessonNum: TextView = view.findViewById(R.id.lessonNum)
            val subjectName: TextView = view.findViewById(R.id.subjectName)
            val lessonTime: TextView = view.findViewById(R.id.lessonTime)
            val teacherName: TextView = view.findViewById(R.id.teacherName)
            val roomNumber: TextView = view.findViewById(R.id.roomNumber)
            val lessonStatus: TextView = view.findViewById(R.id.lessonStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_schedule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lesson = lessons[position]
            val isCancelled = lesson.status == "cancelled"

            holder.lessonNum.text = lesson.lessonNum.toString()
            holder.subjectName.text = lesson.subject

            // Зачёркивание текста при отмене
            if (isCancelled) {
                holder.subjectName.paintFlags = holder.subjectName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.subjectName.setTextColor(holder.itemView.context.getColor(R.color.error_color))
            } else {
                holder.subjectName.paintFlags = holder.subjectName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.subjectName.setTextColor(holder.itemView.context.getColor(android.R.color.black))
            }

            holder.lessonTime.text = when {
                lesson.timeStart != null && lesson.timeEnd != null ->
                    "${lesson.timeStart} – ${lesson.timeEnd}"
                else -> ""
            }

            // Показываем статус "ОТМЕНЁН"
            if (isCancelled) {
                holder.lessonStatus.visibility = View.VISIBLE
                holder.lessonCard.strokeColor = holder.itemView.context.getColor(R.color.error_color)
            } else {
                holder.lessonStatus.visibility = View.GONE
                holder.lessonCard.strokeColor = holder.itemView.context.getColor(R.color.default_stroke)
            }

            if (!lesson.teacher.isNullOrBlank()) {
                holder.teacherName.visibility = View.VISIBLE
                holder.teacherName.text = lesson.teacher
            } else {
                holder.teacherName.visibility = View.GONE
            }

            if (!lesson.room.isNullOrBlank()) {
                holder.roomNumber.visibility = View.VISIBLE
                holder.roomNumber.text = "Каб. ${lesson.room}"
            } else {
                holder.roomNumber.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onItemClick(lesson)
            }
        }

        override fun getItemCount(): Int = lessons.size

        fun updateData(newLessons: List<Schedule>) {
            lessons = newLessons
            notifyDataSetChanged()
        }
    }
}