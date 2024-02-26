package ru.zabkli.ui.news

import android.R.attr.text
import android.R.attr.value
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zabkli.databinding.FragmentNewsBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket


class NewsFragment : Fragment() {

private var _binding: FragmentNewsBinding? = null

    var newsTitles: List<String> = listOf()
    var newsDescriptions: List<String> = listOf()
    var newsSources: List<String> = listOf()
    var page = 0

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        lifecycleScope.launch {
            getNews(page)
        }

        binding.backButtonNews.setOnClickListener(View.OnClickListener {
            backButton()
        })
        binding.forwardButtonNews.setOnClickListener(View.OnClickListener {
            forwardButton()
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun backButton(){
        if (page-1<0){
            page = 1
        }
        page -= 1
        lifecycleScope.launch {
            getNews(page)
        }

        Thread.sleep(100)
    }

    fun forwardButton(){
        page += 1
        lifecycleScope.launch {
            getNews(page)
        }
    }

    suspend fun getNews(page: Int){
        return withContext(Dispatchers.IO) {
            try {
                val client = Socket("185.177.216.236", 20)
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.inputStream))

                output.println("5$"+page.toString()+"$")
                val gotString = input.readLine()
                client.close()

                val gotDataNews: MutableList<String> = gotString.split("§").toMutableList()
                val realPage: Int = gotDataNews[0].toInt()
                newsTitles = gotDataNews[1].split("$")
                newsSources = gotDataNews[3].split("$")

                gotDataNews[2] = gotDataNews[2].replace("\\-", "\n")
                newsDescriptions = gotDataNews[2].split("$")

                val recyclerView: RecyclerView = binding.recyclerViewNews
                var showingPageString = ""
                if (page>realPage){
                    showingPageString = (realPage+1).toString() + "-я страница"
                    this@NewsFragment.page = realPage
                } else {
                    showingPageString = (page + 1).toString() + "-я страница"
                }

                MainScope().launch {
                    recyclerView.layoutManager = LinearLayoutManager(context)
                    recyclerView.adapter = NewsData(newsTitles, newsDescriptions, newsSources)
                    binding.textPageNumber.text = showingPageString
                }
            } catch (excep: ConnectException) {
                Toast.makeText(activity, "Не удалось получить ответ от сервера. ConnectionError", Toast.LENGTH_LONG).show()
            }
        }
    }
}