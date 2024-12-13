package ru.zabkli.ui.news

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

    private var newsTitles: List<String> = listOf()
    private var newsDescriptions: List<String> = listOf()
    private var newsSources: List<String> = listOf()
    private var page = 0

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

        binding.backButtonNews.setOnClickListener {
            backButton()
        }
        binding.forwardButtonNews.setOnClickListener {
            forwardButton()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun backButton(){
        if (page<1){
            page = 1
        }
        page -= 1
        lifecycleScope.launch {
            getNews(page)
        }

        Thread.sleep(100)
    }

    private fun forwardButton(){
        page += 1
        lifecycleScope.launch {
            getNews(page)
        }
    }

    private suspend fun getNews(page: Int){
        return withContext(Dispatchers.IO) {
            try {
                val client = Socket("212.67.12.199", 1717)
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.inputStream))

                output.println("5$$page$")
                val gotString = input.readLine()
                client.close()

                val gotDataNews: MutableList<String> = gotString.split("§").toMutableList()
                val realPage: Int = gotDataNews[0].toInt()
                newsTitles = gotDataNews[1].split("$")
                newsSources = gotDataNews[3].split("$")

                gotDataNews[2] = gotDataNews[2].replace("\\-", "\n")
                newsDescriptions = gotDataNews[2].split("$")

                val recyclerView: RecyclerView = binding.recyclerViewNews
                val showingPageString: String
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