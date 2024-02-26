package ru.zabkli.ui.olympiads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.zabkli.R

class OlympiadsData(private val names: List<String>, private val descriptions: List<String>) :
    RecyclerView.Adapter<OlympiadsData.MyViewHolder>() {

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val olympiadName: TextView = itemView.findViewById(R.id.olympiadName)
        val olympiadDescription: TextView = itemView.findViewById(R.id.olympiadDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_olympiad, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.olympiadName.text = names[position]
        holder.olympiadDescription.text = descriptions[position]
    }

    override fun getItemCount(): Int {
        return names.size
    }
}