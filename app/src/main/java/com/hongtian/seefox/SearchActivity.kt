package com.hongtian.seefox

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SearchActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var et: EditText
    private lateinit var panelScroll: View
    private lateinit var historyCard: View
    private lateinit var hotSearchCard: View
    private lateinit var hotRecommendSection: View
    private lateinit var flowHistory: FlowLayout
    private lateinit var flowHot: FlowLayout
    private lateinit var hotListLeft: LinearLayout
    private lateinit var hotListRight: LinearLayout
    private lateinit var btnExpandHistory: TextView

    private var results: MutableList<SoftwareArticle> = mutableListOf()
    private lateinit var adapter: SearchAdapter
    private var allArticles: List<SoftwareArticle> = emptyList()
    private var historyExpanded = false

    companion object {
        private const val PREFS = "seefox_prefs"
        private const val KEY_HISTORY = "search_history"
        private const val MAX_HISTORY = 10
        private const val HISTORY_COLLAPSED = 6
        private const val SEP = "||"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        rv = findViewById(R.id.rvSearch)
        tvEmpty = findViewById(R.id.tvEmpty)
        et = findViewById(R.id.etSearch)
        panelScroll = findViewById(R.id.panelScroll)
        historyCard = findViewById(R.id.historyCard)
        hotSearchCard = findViewById(R.id.hotSearchCard)
        hotRecommendSection = findViewById(R.id.hotRecommendSection)
        flowHistory = findViewById(R.id.flowHistory)
        flowHot = findViewById(R.id.flowHot)
        hotListLeft = findViewById(R.id.hotListLeft)
        hotListRight = findViewById(R.id.hotListRight)
        btnExpandHistory = findViewById(R.id.btnExpandHistory)

        rv.layoutManager = LinearLayoutManager(this)
        adapter = SearchAdapter(results)
        rv.adapter = adapter

        // 全量文章做本地搜索，不请求网络
        val seen = mutableSetOf<Int>()
        allArticles = (SoftwareNewsData.latestArticles + SoftwareNewsData.hotArticles)
            .filter { seen.add(it.id) }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                doSearch(s?.toString()?.trim() ?: "")
            }
        })
        et.setOnEditorActionListener { _, _, _ ->
            val kw = et.text.toString().trim()
            if (kw.isNotEmpty()) saveHistory(kw)
            false
        }

        findViewById<View>(R.id.btnClearHistory).setOnClickListener {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
            historyExpanded = false
            buildHistory()
        }

        btnExpandHistory.setOnClickListener {
            historyExpanded = !historyExpanded
            buildHistory()
        }

        findViewById<View>(R.id.btnRefreshHot).setOnClickListener { buildHotSearches(shuffle = true) }

        buildHistory()
        buildHotSearches()
        buildHotRecommends()
        et.requestFocus()
    }

    // ================= 搜索历史 =================
    private fun loadHistory(): MutableList<String> {
        val raw = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return mutableListOf()
        return raw.split(SEP).filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveHistory(keyword: String) {
        if (keyword.isBlank()) return
        val list = loadHistory()
        list.remove(keyword)
        list.add(0, keyword)
        while (list.size > MAX_HISTORY) list.removeAt(list.size - 1)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HISTORY, list.joinToString(SEP))
            .apply()
        buildHistory()
    }

    private fun buildHistory() {
        val list = loadHistory()
        flowHistory.removeAllViews()
        if (list.isEmpty()) {
            historyCard.visibility = View.GONE
            return
        }
        historyCard.visibility = View.VISIBLE
        val showList = if (historyExpanded) list else list.take(HISTORY_COLLAPSED)
        showList.forEach { kw ->
            flowHistory.addView(makeChip(kw) {
                jumpToSearch(kw)
            })
        }
        if (list.size > HISTORY_COLLAPSED) {
            btnExpandHistory.visibility = View.VISIBLE
            btnExpandHistory.text = if (historyExpanded) "收起  ⌃" else "展开更多搜索历史  ⌄"
        } else {
            btnExpandHistory.visibility = View.GONE
        }
    }

    // ================= 热门搜索：从文章实际内容统计高频词，保证点了一定有结果 =================
    private fun buildHotSearches(shuffle: Boolean = false) {
        val candidates = listOf(
            "Magisk", "KernelSU", "TWRP", "Bootloader", "Root", "解锁", "线刷", "变砖",
            "刷机", "GApps", "ADB", "Fastboot", "EDL", "快充", "二手", "文件管理",
            "输入法", "固件", "内核", "备份", "电池", "屏幕", "散热", "降级"
        )
        var scored = candidates.map { kw ->
            val count = allArticles.count {
                it.title.contains(kw, true) || it.excerpt.contains(kw, true) || it.category.contains(kw, true)
            }
            kw to count
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(10)

        if (shuffle && scored.size > 1) {
            // 刷新：在同权重区间内换一批，保持都是真实能搜到结果的关键词
            scored = scored.shuffled().take(10)
        }

        flowHot.removeAllViews()
        if (scored.isEmpty()) {
            hotSearchCard.visibility = View.GONE
            return
        }
        hotSearchCard.visibility = View.VISIBLE
        scored.forEach { (kw, _) ->
            flowHot.addView(makeChip(kw) {
                jumpToSearch(kw)
            })
        }
    }

    // ================= 热搜推荐榜：双列，点击最多的文章优先 =================
    private fun buildHotRecommends() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pool = allArticles.sortedByDescending { prefs.getInt("click_count_${it.id}", 0) }
        val recommend = if (pool.isNotEmpty()) pool.take(10) else SoftwareNewsData.getTopClickedArticles()
        if (recommend.isEmpty()) {
            hotRecommendSection.visibility = View.GONE
            return
        }
        hotRecommendSection.visibility = View.VISIBLE
        hotListLeft.removeAllViews()
        hotListRight.removeAllViews()

        val leftCount = if (recommend.size <= 5) recommend.size else (recommend.size + 1) / 2
        val inflater = LayoutInflater.from(this)
        recommend.forEachIndexed { index, a ->
            val container = if (index < leftCount) hotListLeft else hotListRight
            val item = inflater.inflate(R.layout.item_hot_search, container, false)
            val rank = item.findViewById<TextView>(R.id.tvRank)
            rank.text = "${index + 1}"
            val rankColor = when (index) {
                0 -> 0xFFFF4D4F.toInt()
                1 -> 0xFFFF9F1C.toInt()
                2 -> 0xFFFFC53D.toInt()
                else -> 0
            }
            if (rankColor != 0) {
                rank.setTextColor(0xFFFFFFFF.toInt())
                val bg = GradientDrawable()
                bg.cornerRadius = dp(4).toFloat()
                bg.setColor(rankColor)
                rank.background = bg
            } else {
                rank.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                rank.background = null
            }
            item.findViewById<TextView>(R.id.tvHotTitle).text = a.title

            val clicks = prefs.getInt("click_count_${a.id}", 0)
            val heat = item.findViewById<TextView>(R.id.tvHotHeat)
            if (clicks > 0) {
                heat.visibility = View.VISIBLE
                heat.text = "🔥 $clicks"
            } else {
                heat.visibility = View.GONE
            }

            item.findViewById<View>(R.id.hotItemRoot).setOnClickListener { openDetail(a) }
            container.addView(item)
        }
        // 每列最后一条隐藏分隔线
        listOf(hotListLeft, hotListRight).forEach { col ->
            if (col.childCount > 0) {
                (col.getChildAt(col.childCount - 1) as ViewGroup).getChildAt(1).visibility = View.GONE
            }
        }
    }

    // ================= 公共 =================
    private fun jumpToSearch(keyword: String) {
        et.setText(keyword)
        et.setSelection(keyword.length)
        doSearch(keyword)
        saveHistory(keyword)
    }

    private fun makeChip(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@SearchActivity, R.color.text_primary))
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.bg_chip)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun doSearch(key: String) {
        if (key.isEmpty()) {
            results.clear()
            adapter.notifyDataSetChanged()
            rv.visibility = View.GONE
            tvEmpty.visibility = View.GONE
            panelScroll.visibility = View.VISIBLE
            return
        }
        panelScroll.visibility = View.GONE
        val kw = key.lowercase()
        val matched = allArticles.filter {
            it.title.lowercase().contains(kw) ||
                it.excerpt.lowercase().contains(kw) ||
                it.category.lowercase().contains(kw)
        }.take(60)
        results.clear()
        results.addAll(matched)
        adapter.notifyDataSetChanged()
        rv.visibility = if (matched.isEmpty()) View.GONE else View.VISIBLE
        tvEmpty.visibility = if (matched.isEmpty()) View.VISIBLE else View.GONE
        if (matched.isEmpty()) tvEmpty.text = "没有找到与「$key」相关的文章"
    }

    private fun openDetail(a: SoftwareArticle) {
        startActivity(Intent(this, SoftwareDetailActivity::class.java).apply {
            putExtra("title", a.title)
            putExtra("category", a.category)
            putExtra("date", a.date)
            putExtra("html", a.htmlContent)
        })
    }

    inner class SearchAdapter(private val data: List<SoftwareArticle>) :
        RecyclerView.Adapter<SearchAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView = v.findViewById(R.id.imgThumb)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val meta: TextView = v.findViewById(R.id.tvMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
            v.findViewById<View>(R.id.btnClose).visibility = View.GONE
            v.setBackgroundColor(ContextCompat.getColor(parent.context, R.color.bg_card))
            return VH(v)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val a = data[pos]
            h.title.text = a.title
            h.meta.text = "${a.category} · ${a.date}"
            if (a.cover.isNotEmpty()) {
                Glide.with(h.iv).load(a.cover).into(h.iv)
            } else {
                h.iv.setImageBitmap(null)
            }
            h.itemView.setOnClickListener {
                saveHistory(et.text.toString().trim())
                openDetail(a)
            }
        }
    }
}
