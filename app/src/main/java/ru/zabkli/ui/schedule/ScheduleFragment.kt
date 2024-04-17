package ru.zabkli.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zabkli.databinding.FragmentScheduleBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import java.util.Calendar


class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null

    private val SETTINGS_ZABKLI = "settings_zabkli"

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    var calendar = Calendar.getInstance()
    var day = calendar[Calendar.DAY_OF_WEEK]
    var currentTime = calendar[Calendar.HOUR_OF_DAY] * 60 + calendar[Calendar.MINUTE]

    val listIdsForClassChange = arrayOf("5a", "6a", "7a", "8a", "9a", "9b", "10a", "10b", "11a", "11b")

    val timeOfLessons = listOf(
        listOf<String>("8:00-8:50", "9:00-9:40", "10:00-10:40", "11:00-11:40", "11:50-12:30", "13:00-13:40", "14:10-14:50", "15:00-15:40"),
        listOf<String>("8:00-8:40", "8:50-9:30", "9:50-10:30", "10:50-11:30", "11:40-12:20", "12:50-13:30", "14:00-14:40", "14:50-15:30"),
        listOf<String>("8:00-8:40", "8:50-9:30", "9:45-10:25", "10:40-11:20", "11:30-12:10", "12:30-13:10", "13:30-14:10", "14:20-15:00")
    )

    val intTimeOfLessons = listOf(
        listOf<Int>(480, 540, 600, 660, 710, 780, 850, 900),
        listOf<Int>(480, 530, 590, 650, 700, 770, 850, 890),
        listOf<Int>(480, 530, 585, 640, 690, 750, 820, 860)
    )

    val allLessonsDefault = listOf("Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Математика\$История\$Русский язык\$Литература\$Физкультура\$ОДНКНР\$Финансовый навигатор\$Математика\$Занимательная математика\$Русский язык\$Литература\$Русское слово\$Веселые старты\$Нет урока\$Нет урока\$Математика\$Русский язык\$История\$Родной язык\$Физкультура\$Нет урока\$Нет урока\$Нет урока\$Английский язык (1 группа)\$Математика\$Индивидуальный проект\$Русский язык\$Литература\$Занимательная математика\$Биология\$Английский язык (2 группа)\$Русский язык\$Технология\$Технология\$Физкультура\$Математика\$Английский язык (1 группа)\$Английский язык (1 группа)\$Каллиграфия (1 группа)\$Информатика / Китайский язык\$Китайский язык / Информатика\$ИЗО\$География\$Музыка\$Английский язык (2 группа)\$Английский язык (2 группа)\$Каллиграфия (2 группа)",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Математика\$Русский язык\$Русский язык\$Русское слово\$Английский язык / Китайский язык\$Китайский язык / Английский язык\$Нет урока\$Русский язык\$Русский язык\$Математика\$Обществознание\$Литература\$Веселые старты\$Английский язык / Каллиграфия\$Каллиграфия / Английский язык\$История\$Русский язык\$Русский язык\$Литература\$Математика\$Биология\$Физкультура\$Музыка\$Родной русский язык\$Литература\$Физкультура\$История\$Математика\$Занимательная математика\$Финансовый навигатор\$Нет урока\$Математика\$Физкультура\$Индивидуальный проект\$Технология\$Технология\$Занимательная математика\$Нет урока\$Нет урока\$География\$ОДНКНР\$Информатика / Английский язык\$Английский язык / Информатика\$ИЗО\$Нет урока\$Нет урока\$Нет урока",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Алгебра\$Русский язык\$Английский язык / Китайский язык\$Китайский язык / Английский язык\$Обществознание\$Занимательная математика\$Физкультура\$Финансовый навигатор\$Индивидуальный проект\$Алгебра\$Физика\$Живая физика\$Литература\$Русское слово\$Нет урока\$Русский язык\$Английский язык / Каллиграфия\$Алгебра\$Биология\$История\$Каллиграфия / Английский язык\$Занимательная математика\$Физкультура\$Геометрия\$Геометрия\$Русский язык\$Биология\$Физика\$Живая физика\$Литература\$Нет урока\$Геометрия\$Русский язык\$Физкультура\$История\$Родной русский язык\$Технология\$Технология\$Информатика (2 группа)\$Музыка\$География\$География\$ИЗО\$Информатика / Английский язык\$Английский язык / Информатика\$Черчение\$Информатика (1 группа)",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Русский язык\$Химия\$Занимательная химия\$Обществознание\$Литература\$Физкультура\$Трудные случаи орфографии\$Финансовый навигатор\$Английский язык / Информатика\$Алгебра\$Алгебра\$Занимательная математика\$Информатика / Английский язык\$Индивидуальный проект\$Нет урока\$Английский язык / Китайский язык\$Алгебра\$Биология\$История\$Русский язык\$Литература\$Китайский язык / Английский язык\$Живая физика\$Физика\$Физика\$Геометрия\$Геометрия\$Английский язык / Китайский язык\$Китайский язык / Английский язык\$Живая физика\$Нет урока\$Технология\$Черчение\$Русский язык\$Русское слово\$История\$Родной русский язык\$Физкультура\$Занимательная математика\$Геометрия\$Физкультура\$Музыка\$Химия\$Биология\$География\$ОБЖ\$География",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Физкультура\$Алгебра\$Алгебра\$Русский язык\$Русский язык\$Занимательная химия\$Физика\$Алгебра\$Английский язык\$Информатика\$Физкультура\$Обществознание\$История\$Русский язык\$Занимательная математика\$Занимательная математика\$География\$География\$Английский язык\$Биология\$Родной русский язык\$Русское слово\$Гомеостаз (Химбио)\$Литература\$Литература\$Геометрия\$Инфознайка\$Английский язык\$Физика\$Химия\$Финансовый навигатор\$Литература\$Геометрия\$Геометрия\$МХК\$Физика\$История\$История\$Нет урока\$Литература Забайкалья\$Химия\$Биология\$Физкультура\$Китайский язык\$ОБЖ\$Профориентация\$Нет урока",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Китайский язык / Английский язык\$Русский язык\$Русский язык\$Алгебра\$Алгебра\$Физика\$Занимательная химия\$Информатика / Английский язык\$Алгебра\$Обществозание\$Русский язык\$Физкультура\$Занимательная математика\$Английский язык / Информатика\$Финансовый навигатор\$Физика\$Занимательная математика\$Английский язык / Китайский язык\$География\$География\$История\$Биология\$Гомеостаз (Химбио)\$Инфознайка / Английский язык\$Английский язык / Инфознайка\$Химия\$Геометрия\$Физкультура\$Литература\$Русское слово\$Физика\$История\$История\$МХК\$Геометрия\$Геометрия\$Литература\$Литература\$Родной язык\$Литература Забайкалья\$ОБЖ\$Химия\$Биология\$Физкультура\$Профориентация\$Нет урока\$Нет урока",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Русский язык\$Математика / Физика\$Математика / Физика\$Физика / Математика\$Физика / Математика\$Литература\$Литература\$Информатика\$Информатика\$История\$История\$Русский язык\$Литература\$Физкультура\$Родной язык в реке времени\$География\$Химия\$Математика / Физика\$Математика / Английский язык\$Английский язык / Математика\$Физика / Математика\$Информатика / Физика\$Информатика (2 группа)\$Английский язык / Физика (эк)\$Английский язык / Физика (эк)\$Физика (эк) / Английский язык\$Физика (эк) / Английский язык\$Обществознание\$Обществознание\$Индивидуальный проект\$Физкультура\$Математика (1 группа)\$Математика / Физика\$Физика / Математика\$Физика / Математика\$Физкультура\$Математика / Информатика\$Информатика / Математика\$ОБЖ\$Нет урока\$Биология\$ПРМЗ / Информатика\$ПРМЗ / Информатика\$Информатика / ПРМЗ\$Информатика / ПРМЗ\$Нет урока\$Нет урока",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Химия / Английский язык\$Обществознание\$Обществознание\$История\$История\$Математика / Биология\$Математика / Биология\$Математика / Химия\$Математика / Химия\$Физкультура\$Английский язык / Математика\$Английский язык / Математика\$Информатика / Химия\$Химия (2 группа)\$Химия (2 группа)\$Литература\$Литература\$Английский язык / Биология\$Физика / Биология\$Физика / Математика\$Информатика / Математика\$География\$Родной язык\$Физкультура\$Индивидуальный проект\$Математика / Физика\$Математика / Физика\$Физика (эк) / Математика\$Физика (эк) / Математика\$Информатика (2 группа)\$Биология (2 группа)\$Информатика / ЧГМА\$Математика / ЧГМА\$Математика / ЧГМА\$Математика / ЧГМА\$Информатика / ЧГМА\$Физика / ЧГМА\$Физика / ЧГМА\$Физика / ЧГМА\$Биология / Английский язык\$Информатика / Английский язык\$Физкультура\$ОБЖ\$Русский язык\$Русский язык\$Литература\$Информатика (1 группа)",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Информатика\$Математика / Физика\$Математика / Физика\$Физика / Математика\$Физика / Математика\$Физика (эк)\$Физика (эк)\$Русский язык\$Литература\$Литература\$Математика / Информатика\$Математика / Английский язык\$Английский язык / Математика\$Информатика / Математика\$Индивидуальный проект\$Математика (1 группа)\$Математика / Информатика\$Информатика / Математика\$Информатика / Математика\$Химия\$География\$История\$История\$Обществознание\$Обществознание\$Астрономия\$Русский язык\$Родной язык\$Физкультура\$Физика / Математика\$Математика / Физика\$Физика (2 группа)\$Информатика / Физика\$Физика / Информатика\$Физика / Информатика\$Литература\$Физкультура\$Нет урока\$Нет урока\$Математика / Английский язык\$Математика / Английский язык\$Английский язык / Математика\$Английский язык / Математика\$Биология\$Физкультура\$Информатика\$ОБЖ",
        "Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Разговор о важном\$Астрономия\$Биология / Английский язык\$Биология / Английский язык\$Химия / Биология\$Химия / Биология\$История\$История\$Физика\$Физика\$Английский язык / Химия\$Английский язык / Химия\$Информатика / Химия\$Обществознание\$Обществознание\$Индивидуальный проект\$Биология (1 группа)\$Английский язык / Биология\$Русский язык\$Русский язык\$Литература\$Физкультура\$ОБЖ\$География\$Математика\$Математика\$Биология / Английский язык\$Биология / Информатика\$Химия / Биология\$Биология (2 группа)\$Физкультура\$Нет урока\$ЧГМА\$ЧГМА\$ЧГМА\$ЧГМА\$ЧГМА\$ЧГМА\$Математика\$Математика\$Физкультура\$Русский язык\$Литература\$Родной язык\$Математика\$Математика\$Нет урока\$Нет урока"
    )

    var lessons = mutableListOf(
        mutableListOf("Разговор о важном","Разговор о важном","Разговор о важном","Разговор о важном","Разговор о важном","Разговор о важном","Разговор о важном","Разговор о важном"),
        mutableListOf("Разговор о важном","Китайский язык / Английский язык","Русский язык","Русский язык","Алгебра","Алгебра","Физика","Занимательная химия"),
        mutableListOf("Информатика / Английский язык","Алгебра","Обществознание","Русский язык","Физкультура","Занимательная математика","Английский язык / Информатика","Финансовый навигатор"),
        mutableListOf("Физика","Занимательная математика","Английский язык / Китайский язык","География","География","История","Биология","Гомеостаз (Химбио)"),
        mutableListOf("Инфознайка / Английский язык","Английский язык / Инфознайка","Химия","Геометрия","Физкультура","Литература","Русское слово","Физика"),
        mutableListOf("История","История","МХК","Геометрия","Геометрия","Литература","Литература","Родной язык"),
        mutableListOf("Литература Забайкалья","ОБЖ","Химия","Биология","Физкультура","Профориентация","Нет урока","Нет урока")
    )

    val textVerWeekDay = listOf<String>("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

    var week_day: Int = day

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        val root: View = binding.root

        updater()

        _binding!!.backButton.setOnClickListener{
            week_day--
            DayCycle()
        }

        _binding!!.forwardButton.setOnClickListener{
            week_day++
            DayCycle()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updater(){
        val settingsZabKLI = activity?.getSharedPreferences(
            SETTINGS_ZABKLI,
            AppCompatActivity.MODE_PRIVATE
        )

        val whatClassUserUse = settingsZabKLI?.getInt("userUsingClassId", 5)
        val isChangedFunction = settingsZabKLI?.getBoolean("changedFunction", true)

        if (isChangedFunction == true) {
            lifecycleScope.launch {
                getLessons(listIdsForClassChange[whatClassUserUse!!])
            }
        }

        else {
            val lessonsDataString: List<String> = allLessonsDefault[whatClassUserUse!!].split("$")

            for (currentDayLessonsId in 0..6) {
                val changingArray: MutableList<String> =
                    mutableListOf("", "", "", "", "", "", "", "")
                for (currentLessonOnDayId in 0..7) {
                    changingArray[currentLessonOnDayId] =
                        lessonsDataString[currentDayLessonsId * 8 + currentLessonOnDayId]
                }
                lessons[currentDayLessonsId] = changingArray
            }

            NextLesson()

            DayCycle()
        }
    }

    fun NextLesson(){
        val firstLessons = listOf(
            listOf("8:00-8:50", lessons[0][0]),
            listOf("8:00-8:50", lessons[1][0]),
            listOf("8:00-8:40", lessons[2][0]),
            listOf("8:00-8:40", lessons[3][0]),
            listOf("8:00-8:40", lessons[4][0]),
            listOf("8:00-8:40", lessons[5][0]),
            listOf("8:00-8:40", lessons[6][0])
        )

        val idForTimeOfLessons = listOf<Int>(0,0,1,1,2,1,2)
        for (potentialNextLessonId in 0..7){
            if (currentTime < intTimeOfLessons[idForTimeOfLessons[week_day-1]][potentialNextLessonId]){
                MainScope().launch {
                    binding.textNextLesson.text = lessons[week_day - 1][potentialNextLessonId]
                    binding.timeNextLesson.text =
                        timeOfLessons[idForTimeOfLessons[week_day - 1]][potentialNextLessonId]
                }
                break
            }
        }
        if (currentTime >= intTimeOfLessons[idForTimeOfLessons[week_day-1]][7]){
            MainScope().launch {
                binding.timeNextLesson.text = firstLessons[(week_day - 1) % 7][0]
                binding.textNextLesson.text = firstLessons[(week_day - 1) % 7][1]
            }
            week_day = day + 1
        }
    }

    fun DayCycle(){
        if (week_day<2){
            week_day = 7
        } else if (week_day>7) {
            week_day = 2
        }

        MainScope().launch {
            binding.textDay.text = textVerWeekDay[week_day - 1]

            binding.textLesson1.text = lessons[week_day - 1][0]
            binding.textLesson2.text = lessons[week_day - 1][1]
            binding.textLesson3.text = lessons[week_day - 1][2]
            binding.textLesson4.text = lessons[week_day - 1][3]
            binding.textLesson5.text = lessons[week_day - 1][4]
            binding.textLesson6.text = lessons[week_day - 1][5]
            binding.textLesson7.text = lessons[week_day - 1][6]
            binding.textLesson8.text = lessons[week_day - 1][7]

            if ((week_day == 2) or (week_day == 1)) {
                binding.timeLesson1.text = timeOfLessons[0][0]
                binding.timeLesson2.text = timeOfLessons[0][1]
                binding.timeLesson3.text = timeOfLessons[0][2]
                binding.timeLesson4.text = timeOfLessons[0][3]
                binding.timeLesson5.text = timeOfLessons[0][4]
                binding.timeLesson6.text = timeOfLessons[0][5]
                binding.timeLesson7.text = timeOfLessons[0][6]
                binding.timeLesson8.text = timeOfLessons[0][7]
            } else if ((week_day == 7) or (week_day == 5)) {
                binding.timeLesson1.text = timeOfLessons[2][0]
                binding.timeLesson2.text = timeOfLessons[2][1]
                binding.timeLesson3.text = timeOfLessons[2][2]
                binding.timeLesson4.text = timeOfLessons[2][3]
                binding.timeLesson5.text = timeOfLessons[2][4]
                binding.timeLesson6.text = timeOfLessons[2][5]
                binding.timeLesson7.text = timeOfLessons[2][6]
                binding.timeLesson8.text = timeOfLessons[2][7]
            } else {
                binding.timeLesson1.text = timeOfLessons[1][0]
                binding.timeLesson2.text = timeOfLessons[1][1]
                binding.timeLesson3.text = timeOfLessons[1][2]
                binding.timeLesson4.text = timeOfLessons[1][3]
                binding.timeLesson5.text = timeOfLessons[1][4]
                binding.timeLesson6.text = timeOfLessons[1][5]
                binding.timeLesson7.text = timeOfLessons[1][6]
                binding.timeLesson8.text = timeOfLessons[1][7]
            }
        }
    }

    suspend fun getLessons(userUsingClassString: String) {
        return withContext(Dispatchers.IO) {
            try {
                val client = Socket("185.177.216.236", 20)
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.inputStream))

                output.println("2$" + userUsingClassString + "$")
                val gotString = input.readLine()
                client.close()

                if (gotString!=""){
                    val lessonsAllDataString: List<String> = gotString.split("§")
                    val lessonsDataString: List<String> = lessonsAllDataString[0].split("$")
                    val changedLessonsIds: List<String> = lessonsAllDataString[1].split("$")

                    for (currentDayLessonsId in 0..6) {
                        val changingArray: MutableList<String> = mutableListOf("", "", "", "", "", "", "", "")
                        for (currentLessonOnDayId in 0..7) {
                            if (changedLessonsIds[currentDayLessonsId * 8 + currentLessonOnDayId] == "1") {
                                changingArray[currentLessonOnDayId] = "(ИЗМЕНЕНО)" + lessonsDataString[currentDayLessonsId * 8 + currentLessonOnDayId]
                            } else {
                                changingArray[currentLessonOnDayId] = lessonsDataString[currentDayLessonsId * 8 + currentLessonOnDayId]
                            }
                        }
                        lessons[currentDayLessonsId] = changingArray
                    }

                    DayCycle()
                    NextLesson()
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Получен пустой ответ от сервера",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (excep: ConnectException) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Не удалось получить ответ от сервера",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

