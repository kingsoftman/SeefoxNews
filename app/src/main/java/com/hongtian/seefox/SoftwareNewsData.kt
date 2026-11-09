package com.hongtian.seefox

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SoftwareArticle(
    val id: Int,
    val title: String,
    val category: String,
    val date: String,
    val excerpt: String,
    val coverSeed: String,
    val cover: String = "",
    val htmlContent: String
)

object SoftwareNewsData {
    private const val HOT_DATA_URL = "https://kingsoftman.github.io/soft-news/articles.json"
    private const val LATEST_DATA_URL = "https://seesun.eu.org/news.json"
    private const val PREFS_NAME = "news_cache"
    private const val KEY_HOT_CACHE = "hot_articles_json"
    private const val KEY_LATEST_CACHE = "latest_articles_json"
    private const val KEY_ARTICLE_DETAIL_CACHE = "article_detail_"

    private lateinit var appContext: Context

    @Volatile
    var hotArticles: List<SoftwareArticle> = emptyList()
        private set

    @Volatile
    var latestArticles: List<SoftwareArticle> = emptyList()
        private set

    private val articleMap = mutableMapOf<Int, SoftwareArticle>()

    // 初始化，在SplashActivity调用
    fun init(context: Context) {
        appContext = context.applicationContext
        // 冷启动先读本地缓存，立刻赋值，不等待网络
        hotArticles = parseArticlesFromPrefs(KEY_HOT_CACHE)
        latestArticles = parseArticlesFromPrefs(KEY_LATEST_CACHE)
        (hotArticles + latestArticles).forEach {
            articleMap[it.id] = it
        }
    }

    private fun parseArticlesFromPrefs(key: String): List<SoftwareArticle> {
        return try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cached = prefs.getString(key, "") ?: ""
            if (cached.isEmpty()) return emptyList()
            val jsonObj = JSONObject(cached)
            val articlesArray = jsonObj.getJSONArray("articles")
            val list = mutableListOf<SoftwareArticle>()
            for (i in 0 until articlesArray.length()) {
                val item = articlesArray.getJSONObject(i)
                list.add(
                    SoftwareArticle(
                        id = item.getInt("id"),
                        title = item.getString("title"),
                        category = item.optString("category", "资讯"),
                        date = item.optString("date", ""),
                        excerpt = item.optString("excerpt", ""),
                        coverSeed = item.optString("coverSeed", "default"),
                        cover = item.optString("cover", ""),
                        htmlContent = item.optString("htmlContent", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveArticlesToPrefs(key: String, jsonText: String) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, jsonText).apply()
    }

    fun loadHotArticles(callback: (() -> Unit)? = null) {
        Thread {
            try {
                val url = URL(HOT_DATA_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                // 让系统自动处理gzip，不手动解压，避免重复解压拖慢速度
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val jsonObj = JSONObject(jsonText)
                val articlesArray = jsonObj.getJSONArray("articles")
                val list = mutableListOf<SoftwareArticle>()
                for (i in 0 until articlesArray.length()) {
                    val item = articlesArray.getJSONObject(i)
                    val article = SoftwareArticle(
                        id = item.getInt("id"),
                        title = item.getString("title"),
                        category = item.optString("category", "资讯"),
                        date = item.optString("date", ""),
                        excerpt = item.optString("excerpt", ""),
                        coverSeed = item.optString("coverSeed", "default"),
                        cover = item.optString("cover", ""),
                        htmlContent = item.getString("htmlContent")
                    )
                    list.add(article)
                    articleMap[article.id] = article
                }
                hotArticles = list
                saveArticlesToPrefs(KEY_HOT_CACHE, jsonText)

                android.os.Handler(android.os.Looper.getMainLooper()).post { callback?.invoke() }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback?.invoke() }
            }
        }.start()
    }

    fun loadLatestArticles(callback: (() -> Unit)? = null) {
        Thread {
            try {
                val url = URL(LATEST_DATA_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                // 让系统自动处理gzip，不手动解压，避免重复解压拖慢速度
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val jsonObj = JSONObject(jsonText)
                val articlesArray = jsonObj.getJSONArray("articles")
                val list = mutableListOf<SoftwareArticle>()
                for (i in 0 until articlesArray.length()) {
                    val item = articlesArray.getJSONObject(i)
                    val article = SoftwareArticle(
                        id = 1000 + item.getInt("id"),
                        title = item.getString("title"),
                        category = item.optString("category", "资讯"),
                        date = item.optString("date", ""),
                        excerpt = item.optString("excerpt", ""),
                        coverSeed = item.optString("coverSeed", "default"),
                        cover = item.optString("cover", ""),
                        htmlContent = item.optString("htmlContent", "")
                    )
                    list.add(article)
                    articleMap[article.id] = article
                }
                latestArticles = list
                saveArticlesToPrefs(KEY_LATEST_CACHE, jsonText)

                android.os.Handler(android.os.Looper.getMainLooper()).post { callback?.invoke() }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback?.invoke() }
            }
        }.start()
    }

    fun getArticleById(id: Int): SoftwareArticle? {
        return articleMap[id]
    }

    // 记录文章点击次数，热点条自动按点击数排序
    fun recordArticleClick(articleId: Int) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("click_count_$articleId", 0)
        prefs.edit().putInt("click_count_$articleId", currentCount + 1).apply()
    }

    // 获取点击次数最高的前5篇文章
    fun getTopClickedArticles(): List<SoftwareArticle> {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (hotArticles + latestArticles)
            .sortedByDescending { prefs.getInt("click_count_${it.id}", 0) }
            .take(5)
    }

    // 按分类过滤文章，直接用首页已经加载好的全量数据本地过滤，秒开不用重复请求网络
    fun loadArticlesByCat(catName: String, callback: (List<SoftwareArticle>) -> Unit) {
        // 严格和你网站目录1:1对应
        val matchMap = mapOf(
            "android" to listOf("安卓刷机"),
            "phone" to listOf("手机数码"),
            "app" to listOf("应用推荐"),
            "tutorial" to listOf("搞机教程"),
            "resource" to listOf("资源下载")
        )
        val allowedCats = matchMap[catName] ?: listOf(catName)
        // 直接从已经加载好的全量最新文章里本地过滤，不用重新请求网络，立刻返回结果
        val filtered = latestArticles.filter { it.category in allowedCats }
        android.os.Handler(android.os.Looper.getMainLooper()).post { callback(filtered) }
    }
}
