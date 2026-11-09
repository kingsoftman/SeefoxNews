package com.hongtian.seefox

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class CommentEditActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment_edit)

        findViewById<TextView>(R.id.backBtn)?.setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.sendBtn)?.setOnClickListener {
            val content = findViewById<EditText>(R.id.commentInput).text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "评论内容不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 保存到 SQLite 数据库
            val newsTitle = intent.getStringExtra("news_title") ?: "新闻标题"
            val db = DBHelper.getInstance(this)
            db.addComment(newsTitle, content)
            Toast.makeText(this, "评论发送成功，已保存到数据库", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
