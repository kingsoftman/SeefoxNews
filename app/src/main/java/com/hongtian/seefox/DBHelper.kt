package com.hongtian.seefox

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context) : SQLiteOpenHelper(context, "seefox.db", null, 1) {

    companion object {
        @Volatile
        private var instance: DBHelper? = null

        fun getInstance(context: Context): DBHelper {
            return instance ?: synchronized(this) {
                instance ?: DBHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 用户表
        db.execSQL("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT,
                avatar TEXT,
                created_at INTEGER
            )
        """)

        // 设置表
        db.execSQL("""
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """)

        // 评论表
        db.execSQL("""
            CREATE TABLE comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                news_title TEXT,
                content TEXT,
                username TEXT,
                created_at INTEGER
            )
        """)

        // 新闻表
        db.execSQL("""
            CREATE TABLE news (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                content TEXT,
                source TEXT,
                image_url TEXT,
                category TEXT,
                created_at INTEGER
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS settings")
        db.execSQL("DROP TABLE IF EXISTS comments")
        db.execSQL("DROP TABLE IF EXISTS news")
        onCreate(db)
    }

    // ========== 评论操作 ==========
    fun addComment(newsTitle: String, content: String, username: String = "我"): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("news_title", newsTitle)
            put("content", content)
            put("username", username)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("comments", null, values)
    }

    fun getComments(newsTitle: String): List<Map<String, String>> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM comments WHERE news_title = ? ORDER BY created_at DESC",
            arrayOf(newsTitle)
        )
        val list = mutableListOf<Map<String, String>>()
        while (cursor.moveToNext()) {
            list.add(mapOf(
                "content" to cursor.getString(cursor.getColumnIndexOrThrow("content")),
                "username" to cursor.getString(cursor.getColumnIndexOrThrow("username")),
                "created_at" to cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
            ))
        }
        cursor.close()
        return list
    }

    // ========== 新闻操作 ==========
    fun addNews(title: String, content: String, source: String, imageUrl: String, category: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("content", content)
            put("source", source)
            put("image_url", imageUrl)
            put("category", category)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("news", null, values)
    }

    // ========== 设置操作 ==========
    fun setSetting(key: String, value: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSetting(key: String, default: String = ""): String {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key))
        return if (cursor.moveToFirst()) {
            cursor.getString(0)
        } else {
            default
        }.also { cursor.close() }
    }
}
