package ru.zabkli.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.zabkli.R

data class NewsData(val titles: List<String>, val newsTexts: List<String>, val sources: List<String>):
    RecyclerView.Adapter<NewsData.MyViewHolder>()
    {
        class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val newsTitle: TextView = itemView.findViewById(R.id.newsTitle)
            val newsDescription: TextView = itemView.findViewById(R.id.newsTextDescription)
            val newsSource: TextView = itemView.findViewById(R.id.newsSource)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            val itemView =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_news, parent, false)
            return MyViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.newsTitle.text = titles[position]
            holder.newsDescription.text = newsTexts[position]
            holder.newsSource.text = sources[position]
        }

        override fun getItemCount(): Int {
            return titles.size
        }
    }
