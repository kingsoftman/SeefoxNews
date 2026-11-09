package com.hongtian.seefox

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class NewsDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_detail)

        val title = intent.getStringExtra("title") ?: ""
        val content = intent.getStringExtra("content") ?: ""

        findViewById<TextView>(R.id.detailTitle).text = title
        findViewById<TextView>(R.id.detailMeta).text = "极安社区 · 2小时前"
        findViewById<TextView>(R.id.detailContent).text = content

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        // 点击评论输入框，跳转到评论编辑页
        findViewById<TextView>(R.id.commentInput)?.setOnClickListener {
            startActivity(Intent(this, CommentEditActivity::class.java).apply {
                putExtra("news_title", title)
            })
        }

        // 推荐图片
        Glide.with(this).load("https://picsum.photos/seed/rel${(1..99).random()}/200/150").override(200,150).centerCrop().into(findViewById(R.id.relImg1))
        Glide.with(this).load("https://picsum.photos/seed/rel${(1..99).random()}/200/150").override(200,150).centerCrop().into(findViewById(R.id.relImg2))
        Glide.with(this).load("https://picsum.photos/seed/rel${(1..99).random()}/200/150").override(200,150).centerCrop().into(findViewById(R.id.relImg3))

        // 随机推荐新闻
        val relPool = listOf(
            "LineageOS 22正式发布，基于Android 16" to "LineageOS 22正式发布，基于Android 16，支持更多设备，带来全新功能和性能优化。",
            "Magisk 27.0发布，支持Android 16" to "Magisk 27.0正式发布，完美支持Android 16系统，Root权限管理更强大。",
            "小米15官方固件下载教程" to "小米15官方固件下载和线刷教程，详细步骤带你完成系统升级。",
            "三星S25线刷官方ROM教程" to "三星S25线刷官方ROM完整教程，使用Odin工具，安全可靠。",
            "OPPO Find X8刷机包下载" to "OPPO Find X8官方刷机包下载和卡刷教程，轻松升级系统。",
            "vivo X200官方固件升级教程" to "vivo X200官方固件升级教程，使用官方Recovery完成系统更新。",
            "华为Mate 70 Pro救砖教程" to "华为Mate 70 Pro救砖教程，遇到开不了机别慌，按步骤操作轻松恢复。",
            "一加13刷入PixelExperience教程" to "一加13刷入PixelExperience第三方ROM教程，体验纯净安卓系统。"
        )
        val picks = relPool.shuffled().take(3)
        findViewById<TextView>(R.id.relTitle1).text = picks[0].first
        findViewById<TextView>(R.id.relTitle2).text = picks[1].first
        findViewById<TextView>(R.id.relTitle3).text = picks[2].first

        val relIds = listOf(R.id.rel1, R.id.rel2, R.id.rel3)
        relIds.forEachIndexed { i, id ->
            findViewById<android.view.View>(id)?.setOnClickListener {
                val t = picks[i].first
                val c = picks[i].second
                startActivity(Intent(this, NewsDetailActivity::class.java).apply {
                    putExtra("title", t); putExtra("content", c)
                })
            }
        }

        // 从数据库读取评论并显示
        val db = DBHelper.getInstance(this)
        val comments = db.getComments(title)
        val commentList = findViewById<android.widget.LinearLayout>(R.id.commentList)
        comments.forEach { c ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }
            val avatar = android.widget.TextView(this).apply {
                text = "👤"
                textSize = 18f
                setTextColor(android.graphics.Color.parseColor("#999999"))
                layoutParams = android.widget.LinearLayout.LayoutParams(36, 36).apply {
                    marginEnd = 12
                }
            }
            val textCol = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val username = android.widget.TextView(this).apply {
                text = c["username"] ?: "我"
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(androidx.core.content.ContextCompat.getColor(this@NewsDetailActivity, R.color.text_primary))
            }
            val content = android.widget.TextView(this).apply {
                text = c["content"] ?: ""
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@NewsDetailActivity, R.color.text_primary))
                setPadding(0, 4, 0, 0)
            }
            val time = android.widget.TextView(this).apply {
                text = "刚刚 · 回复"
                textSize = 11f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@NewsDetailActivity, R.color.text_secondary))
                setPadding(0, 6, 0, 0)
            }
            textCol.addView(username)
            textCol.addView(content)
            textCol.addView(time)
            row.addView(avatar)
            row.addView(textCol)
            commentList?.addView(row)
        }
    }
}
