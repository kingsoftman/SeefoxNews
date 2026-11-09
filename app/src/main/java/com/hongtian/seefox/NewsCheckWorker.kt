package com.hongtian.seefox

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 定时检查 seesun 站点是否有新文章，有则发状态栏图文通知
 */
class NewsCheckWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    data class SimpleArticle(
        val id: Int,
        val title: String,
        val category: String,
        val date: String,
        val excerpt: String,
        val cover: String,
        val html: String
    )

    companion object {
        private const val LATEST_URL = "https://seesun.eu.org/news.json"
        private const val PREFS = "seefox_prefs"
        private const val KEY_LAST_ID = "last_notified_article_id"
    }

    override fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val list = fetchArticles()
            android.util.Log.i("SeefoxWorker", "fetched=${list.size}")
            if (list.isEmpty()) return Result.success()

            val maxId = list.maxOf { it.id }
            val lastId = prefs.getInt(KEY_LAST_ID, -1)
            android.util.Log.i("SeefoxWorker", "maxId=$maxId lastId=$lastId")

            // 首次运行只记录基线，不打扰用户
            if (lastId <= 0) {
                prefs.edit().putInt(KEY_LAST_ID, maxId).apply()
                android.util.Log.i("SeefoxWorker", "baseline saved")
                return Result.success()
            }

            if (maxId > lastId) {
                val fresh = list.filter { it.id > lastId }.sortedByDescending { it.id }
                val top = fresh.first()
                // 只在有封面时用大图样式，没封面就走纯文本通知
                NewsNotify.show(applicationContext, top, fresh.size - 1)
                prefs.edit().putInt(KEY_LAST_ID, maxId).apply()
                android.util.Log.i("SeefoxWorker", "notified id=${top.id} title=${top.title}")
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SeefoxWorker", "error", e)
            Result.retry()
        }
    }

    private fun fetchArticles(): List<SimpleArticle> {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 8000
        }
        val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val arr = JSONObject(jsonText).getJSONArray("articles")
        val list = mutableListOf<SimpleArticle>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            list.add(
                SimpleArticle(
                    id = item.getInt("id"),
                    title = item.optString("title", ""),
                    category = item.optString("category", "资讯"),
                    date = item.optString("date", ""),
                    excerpt = item.optString("excerpt", ""),
                    cover = item.optString("cover", ""),
                    html = item.optString("htmlContent", "")
                )
            )
        }
        return list
    }
}
