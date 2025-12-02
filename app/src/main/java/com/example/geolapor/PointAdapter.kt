package com.example.geolapor.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.geolapor.R
import com.example.geolapor.data.model.Report
import com.example.geolapor.databinding.ItemReportBinding
import android.net.Uri
import java.io.File

class PointAdapter(
    private var list: List<Report>,
    private val onClick: (Report) -> Unit
) : RecyclerView.Adapter<PointAdapter.VH>() {

    inner class VH(private val b: ItemReportBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: Report) {
            b.tvName.text = r.title
            b.tvReporter.text = r.reporterName ?: "-"
            b.tvCat.text = "${r.category} • ${r.subCategory}"
            b.tvDate.text = r.date
            b.tvDesc.text = r.description

            if (!r.photoPath.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(r.photoPath)
                    b.ivThumb.setImageURI(uri)
                } catch (e: Exception) {
                    b.ivThumb.setImageResource(R.drawable.ic_image_placeholder)
                }
            } else {
                b.ivThumb.setImageResource(R.drawable.ic_image_placeholder)
            }


            b.root.setOnClickListener { onClick(r) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(list[position])
    override fun getItemCount() = list.size

    fun update(newList: List<Report>) {
        list = newList
        notifyDataSetChanged()
    }
}
