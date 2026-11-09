package com.hongtian.seefox

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SoftwareNewsActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_software_news)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        rv = findViewById(R.id.rvSoftware)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SoftwareAdapter(SoftwareNewsData.hotArticles) { article ->
            startActivity(Intent(this, SoftwareDetailActivity::class.java).apply {
                putExtra("article_id", article.id)
            })
        }
    }

    inner class SoftwareAdapter(
        val data: List<SoftwareArticle>,
        val onClick: (SoftwareArticle) -> Unit
    ) : RecyclerView.Adapter<SoftwareAdapter.SoftwareVH>() {

        inner class SoftwareVH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgThumb)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val meta: TextView = v.findViewById(R.id.tvMeta)
            val excerpt: TextView = v.findViewById(R.id.tvExcerpt)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoftwareVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_software_news, parent, false)
            return SoftwareVH(v)
        }

        override fun onBindViewHolder(h: SoftwareVH, pos: Int) {
            val item = data[pos]
            h.title.text = item.title
            h.meta.text = "${item.category} · ${item.date}"
            h.excerpt.text = item.excerpt
            Glide.with(this@SoftwareNewsActivity)
                .load("https://picsum.photos/seed/${item.coverSeed}/400/200")
                .centerCrop()
                .into(h.img)
            h.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = data.size
    }
}
