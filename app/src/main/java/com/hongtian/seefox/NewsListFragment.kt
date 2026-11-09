package com.hongtian.seefox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class NewsItem(
    val title: String,
    val meta: String,
    val seed: String,
    val coverUrl: String = "",
    val isSoftware: Boolean = false,
    val softwareId: Int = 0,
    val article: SoftwareArticle? = null
)

class NewsListFragment : Fragment() {

    companion object {
        private const val ARG_CAT = "cat"
        private const val TYPE_BIG = 0
        private const val TYPE_HOT_NEWS = 1
        private const val TYPE_FUNC = 2
        private const val TYPE_HOT_TAG = 3
        private const val TYPE_BANNER = 4
        private const val TYPE_SECTION = 5
        private const val TYPE_NEWS = 6
        fun newInstance(cat: Int): NewsListFragment {
            return NewsListFragment().apply {
                arguments = Bundle().apply { putInt(ARG_CAT, cat) }
            }
        }
    }

    private lateinit var rv: RecyclerView
    private lateinit var adapter: NewsAdapter
    private val items = mutableListOf<Any>()
    private val catNames = listOf("推荐","安卓刷机","手机数码","应用推荐","搞机教程","资源下载")
    private var hotNewsHandler: Handler? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        return RecyclerView(requireContext())
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val cat = arguments?.getInt(ARG_CAT, 0) ?: 0
        rv = v as RecyclerView
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.setPadding(dp(16), dp(16), dp(16), dp(16))
        rv.clipToPadding = false
        // 强制设置整个列表的页面背景色，杜绝反复进出详情页出现白块
        rv.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bg_main))

        adapter = NewsAdapter(items) { item, pos ->
            if (item is NewsItem) {
                if (item.isSoftware) {
                    startActivity(Intent(requireContext(), SoftwareDetailActivity::class.java).apply {
                        putExtra("article_id", item.softwareId)
                    })
                } else {
                    startActivity(Intent(requireContext(), NewsDetailActivity::class.java).apply {
                        putExtra("title", item.title)
                        putExtra("content", "关于「${item.title}」的详细教程：\n\n这是一篇关于${catNames[cat]}的刷机教程，涵盖了从解锁Bootloader到刷入Recovery的完整步骤。")
                    })
                }
            }
        }
        rv.adapter = adapter
        loadData(cat)

        // 无限加载
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(r, dx, dy)
                // 内容上滑收起搜索行，下拉展开；只有当前可见页才联动
                if (isResumed) {
                    if (r.computeVerticalScrollOffset() <= 0) {
                        // 已经滚回最顶部，搜索行必须完整显示
                        (parentFragment as? HomeFragment)?.resetTopBar()
                    } else if (dy != 0) {
                        (parentFragment as? HomeFragment)?.onListScroll(dy)
                    }
                }
                val lm = r.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= adapter.itemCount - 3 && dy > 0) {
                    loadMore()
                }
            }
        })
    }

    fun refresh() {
        val cat = arguments?.getInt(ARG_CAT, 0) ?: 0
        loadData(cat)
    }

    fun canChildScrollUp(): Boolean {
        return if (::rv.isInitialized) rv.canScrollVertically(-1) else false
    }

    private var isLoadingMore = false
    private fun loadMore() {
        // 所有假硬编码新闻已删除，不再加载本地假数据
        isLoadingMore = false
    }

    private fun loadData(cat: Int) {
        items.clear()
        if (cat == 0) {
            // 推荐页：渲染完整双板块列表
            renderHotArticles()
            // 加载两个数据源预加载封面
            SoftwareNewsData.loadHotArticles {
                if (isAdded && !requireActivity().isFinishing) {
                    renderHotArticles()
                }
            }
            SoftwareNewsData.loadLatestArticles {
                if (isAdded && !requireActivity().isFinishing) {
                    renderHotArticles()
                }
            }
        } else {
            // 其他分类标签页：只拉对应分类的文章，不显示推荐页内容
            val catApiName = when(cat) {
                1 -> "android"
                2 -> "phone"
                3 -> "app"
                4 -> "tutorial"
                5 -> "resource"
                else -> ""
            }
            SoftwareNewsData.loadArticlesByCat(catApiName) { articleList ->
                if (!isAdded) return@loadArticlesByCat
                items.clear()
                articleList.forEach { article ->
                    items.add(NewsItem(
                        title = article.title,
                        meta = "${article.category} · ${article.date}",
                        seed = article.coverSeed,
                        coverUrl = article.cover,
                        isSoftware = true,
                        softwareId = article.id,
                        article = article
                    ))
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    // 渲染今日热点下面的资讯列表（读旧github.io数据源）
    private fun renderHotArticles() {
        // 直接重建整个推荐页列表，顺序完全固定，绝对不会闪退乱序
        val tempList = mutableListOf<Any>()
        tempList.add("big")
        tempList.add("hot_news")
        tempList.add("func")
        tempList.add("hot_tag")
        tempList.add("banner")
        tempList.add("section_software")
        // 先加今日热点文章
        if (SoftwareNewsData.hotArticles.isEmpty()) {
            for(i in 0 until 8) {
                tempList.add(NewsItem(title = "正在加载资讯...", meta = "", seed = "loading", isSoftware = false, softwareId = -1))
            }
        } else {
            SoftwareNewsData.hotArticles.forEach { article ->
                tempList.add(NewsItem(
                    title = article.title,
                    meta = "极安资讯 · ${article.category} · ${article.date}",
                    seed = article.coverSeed,
                    coverUrl = article.cover,
                    isSoftware = true,
                    softwareId = article.id,
                    article = article
                ))
            }
        }
        tempList.add("section_latest")
        // 再加最新发布文章
        if (SoftwareNewsData.latestArticles.isEmpty()) {
            for(i in 0 until 8) {
                tempList.add(NewsItem(title = "正在加载资讯...", meta = "", seed = "loading", isSoftware = false, softwareId = -1))
            }
        } else {
            SoftwareNewsData.latestArticles.forEach { article ->
                tempList.add(NewsItem(
                    title = article.title,
                    meta = "极安资讯 · ${article.category} · ${article.date}",
                    seed = article.coverSeed,
                    coverUrl = article.cover,
                    isSoftware = true,
                    softwareId = article.id,
                    article = article
                ))
            }
        }
        items.clear()
        items.addAll(tempList)
        // 每次刷新后强制重设整个列表背景，杜绝残留色差
        rv.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bg_main))
        adapter.notifyDataSetChanged()
    }


    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    inner class NewsAdapter(
        val data: List<Any>,
        val onClick: (Any, Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class NewsVH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgThumb)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val meta: TextView = v.findViewById(R.id.tvMeta)
            val close: TextView = v.findViewById(R.id.btnClose)
        }
        inner class BigVH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.bigImg)
            val title: TextView = v.findViewById(R.id.bigTitle)
        }
        inner class HotNewsVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.yaowenTitle)
        }
        inner class FuncVH(v: View) : RecyclerView.ViewHolder(v)
        inner class HotTagVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.hotTagTitle)
        }
        inner class BannerVH(v: View) : RecyclerView.ViewHolder(v) {
            val pager: androidx.viewpager2.widget.ViewPager2 = v.findViewById(R.id.bannerPager)
            val dots: LinearLayout = v.findViewById(R.id.dots)
        }
        inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.sectionTitle)
        }

        override fun getItemViewType(pos: Int): Int {
            return when(data[pos]) {
                "big" -> TYPE_BIG
                "hot_news" -> TYPE_HOT_NEWS
                "func" -> TYPE_FUNC
                "hot_tag" -> TYPE_HOT_TAG
                "banner" -> TYPE_BANNER
                "section_software" -> TYPE_SECTION
                "section_hot" -> TYPE_SECTION
                "section_latest" -> TYPE_SECTION
                else -> TYPE_NEWS
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return when(viewType) {
                TYPE_BIG -> BigVH(inf.inflate(R.layout.item_big_news, parent, false))
                TYPE_HOT_NEWS -> HotNewsVH(inf.inflate(R.layout.item_hot_news, parent, false))
                TYPE_FUNC -> FuncVH(inf.inflate(R.layout.item_func_bar, parent, false))
                TYPE_HOT_TAG -> HotTagVH(inf.inflate(R.layout.item_hot_tag, parent, false))
                TYPE_BANNER -> BannerVH(inf.inflate(R.layout.item_banner, parent, false))
                TYPE_SECTION -> SectionVH(inf.inflate(R.layout.item_section, parent, false))
                else -> NewsVH(inf.inflate(R.layout.item_news, parent, false))
            }
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            when(h) {
                is BigVH -> {
                    // 从最新发布接口取前5条自动轮播，用真实文章封面
                    val bannerList = SoftwareNewsData.latestArticles.take(5).filter { it.cover.isNotEmpty() }
                    if (bannerList.isNotEmpty()) {
                        // 把当前显示的文章存在ViewHolder里，点击的时候直接拿，绝对不会错
                        var currentBannerArticle = bannerList[0]
                        // 先加载第一条的图和标题
                        Glide.with(requireContext()).load(currentBannerArticle.cover).centerCrop().into(h.img)
                        h.title.text = currentBannerArticle.title
                        // 点击大图直接跳当前显示的文章详情
                        h.itemView.setOnClickListener {
                            val article = currentBannerArticle
                            startActivity(Intent(requireContext(), SoftwareDetailActivity::class.java).apply {
                                putExtra("article_id", article.id)
                                putExtra("title", article.title)
                                putExtra("category", article.category)
                                putExtra("date", article.date)
                                putExtra("html", article.htmlContent)
                            })
                        }
                        hotNewsHandler?.removeCallbacksAndMessages(null)
                        hotNewsHandler = Handler(Looper.getMainLooper())
                        val runnable = object : Runnable {
                            override fun run() {
                                if (!isAdded) return
                                val nextIdx = (bannerList.indexOf(currentBannerArticle) + 1) % bannerList.size
                                currentBannerArticle = bannerList[nextIdx]
                                Glide.with(requireContext()).load(currentBannerArticle.cover).centerCrop().into(h.img)
                                h.title.text = currentBannerArticle.title
                                hotNewsHandler?.postDelayed(this, 3000)
                            }
                        }
                        hotNewsHandler?.postDelayed(runnable, 3000)
                    } else {
                        Glide.with(requireContext()).load("https://picsum.photos/seed/bignews/600/300").into(h.img)
                        h.title.text = ""
                    }
                }
                is HotNewsVH -> {
                    // 自动显示点击次数最多的前5篇文章
                    val topArticles = SoftwareNewsData.getTopClickedArticles()
                    val list = if (topArticles.isEmpty()) listOf("• 欢迎使用极安刷机工具箱") else topArticles.map { "• ${it.title}" }
                    var idx = 0
                    // 点击滚动条直接打开对应文章详情
                    h.itemView.setOnClickListener {
                        if (topArticles.isNotEmpty()) {
                            val article = topArticles[idx]
                            startActivity(Intent(requireContext(), SoftwareDetailActivity::class.java).apply {
                                putExtra("article_id", article.id)
                                putExtra("title", article.title)
                                putExtra("category", article.category)
                                putExtra("date", article.date)
                                putExtra("html", article.htmlContent)
                            })
                        }
                    }
                    hotNewsHandler?.removeCallbacksAndMessages(null)
                    hotNewsHandler = Handler(Looper.getMainLooper())
                    val runnable = object : Runnable {
                        override fun run() {
                            if (!isAdded) return
                            idx = (idx + 1) % list.size
                            h.title.text = list[idx]
                            hotNewsHandler?.postDelayed(this, 3000)
                        }
                    }
                    h.title.text = list[0]
                    hotNewsHandler?.postDelayed(runnable, 3000)
                }
                is FuncVH -> {
                    val names = listOf("解锁","Root","线刷","卡刷","救砖")
                    val icons = listOf("⚡","🔓","🔌","📝","🔥")
                    for (i in 0 until 5) {
                        val item = h.itemView.findViewWithTag<TextView>("func$i")
                        item?.text = icons[i]
                        val label = h.itemView.findViewWithTag<TextView>("funcLabel$i")
                        label?.text = names[i]
                        // 功能按钮点击弹提示
                        h.itemView.setOnClickListener {
                            Toast.makeText(requireContext(), "${names[i]}功能即将上线，敬请期待", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is HotTagVH -> {
                    h.title.text = "🔥 点击查看热门资讯"
                    // 热榜条点击跳最新发布第一篇文章，直接传内容
                    h.itemView.setOnClickListener {
                        if (SoftwareNewsData.latestArticles.isNotEmpty()) {
                            val article = SoftwareNewsData.latestArticles[0]
                            startActivity(Intent(requireContext(), SoftwareDetailActivity::class.java).apply {
                                putExtra("article_id", article.id)
                                putExtra("title", article.title)
                                putExtra("category", article.category)
                                putExtra("date", article.date)
                                putExtra("html", article.htmlContent)
                            })
                        }
                    }
                }
                is BannerVH -> {
                    // 底部Banner只从有封面图的文章里随机选，不会出现空白
                    val bannerList = SoftwareNewsData.latestArticles.filter { it.cover.isNotEmpty() }.shuffled().take(3)
                    if (bannerList.isNotEmpty()) {
                        h.pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                                val iv = ImageView(requireContext())
                                iv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                return object : RecyclerView.ViewHolder(iv) {}
                            }
                            override fun onBindViewHolder(h2: RecyclerView.ViewHolder, p: Int) {
                                val article = bannerList[p]
                                Glide.with(requireContext()).load(article.cover).centerCrop().into(h2.itemView as ImageView)
                                // 点击Banner直接传对应文章内容
                                h2.itemView.setOnClickListener {
                                    startActivity(Intent(requireContext(), SoftwareDetailActivity::class.java).apply {
                                        putExtra("article_id", article.id)
                                        putExtra("title", article.title)
                                        putExtra("category", article.category)
                                        putExtra("date", article.date)
                                        putExtra("html", article.htmlContent)
                                    })
                                }
                            }
                            override fun getItemCount() = bannerList.size
                        }
                        h.dots.removeAllViews()
                        repeat(bannerList.size) {
                            val dot = View(requireContext())
                            dot.layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginStart = dp(4); marginEnd = dp(4) }
                            dot.setBackgroundResource(R.drawable.bg_dot)
                            h.dots.addView(dot)
                        }
                    } else {
                        val imgs = listOf("banner1","banner2","banner3")
                        h.pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                                val iv = ImageView(requireContext())
                                iv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                return object : RecyclerView.ViewHolder(iv) {}
                            }
                            override fun onBindViewHolder(h2: RecyclerView.ViewHolder, p: Int) {
                                Glide.with(requireContext()).load("https://picsum.photos/seed/${imgs[p]}/600/300").into(h2.itemView as ImageView)
                            }
                            override fun getItemCount() = imgs.size
                        }
                    }
                }
                is SectionVH -> {
                    h.title.text = when(data[pos]) {
                        "section_software" -> "软件专区"
                        "section_hot" -> "今日热点"
                        else -> "最新发布"
                    }
                    // 强制设置模块标题文字颜色，浅色模式下黑色清晰可见
                    h.title.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                }
                is NewsVH -> {
                    val item = data[pos] as NewsItem
                    // 每次绑定强制刷新卡片背景色，保留卡片底色同时杜绝复用色差
                    h.itemView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bg_card))
                    h.title.text = item.title
                    h.meta.text = item.meta
                    // 优先加载文章真实封面，无封面时用占位图
                    if (item.coverUrl.isNotEmpty()) {
                        Glide.with(requireContext()).load(item.coverUrl).centerCrop().into(h.img)
                    } else {
                        Glide.with(requireContext()).load("https://picsum.photos/seed/${item.seed}/180/180").into(h.img)
                    }
                    h.itemView.setOnClickListener {
                        if (item.article != null) {
                            // 直接传整个文章对象，绝对不会文不对题
                            val intent = Intent(requireContext(), SoftwareDetailActivity::class.java)
                            intent.putExtra("article_id", item.article!!.id)
                            intent.putExtra("title", item.article!!.title)
                            intent.putExtra("category", item.article!!.category)
                            intent.putExtra("date", item.article!!.date)
                            intent.putExtra("html", item.article!!.htmlContent)
                            startActivity(intent)
                        }
                    }
                    h.close.setOnClickListener {
                        val p = h.bindingAdapterPosition
                        if (p != RecyclerView.NO_POSITION) {
                            (data as MutableList).removeAt(p)
                            notifyItemRemoved(p)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = data.size

        // ViewHolder被回收时，强制重置卡片背景，杜绝复用残留色差
        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            if (holder is NewsVH) {
                holder.itemView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bg_card))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hotNewsHandler?.removeCallbacksAndMessages(null)
    }
}
