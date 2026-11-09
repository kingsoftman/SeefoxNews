package com.hongtian.seefox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        // 启动时先初始化本地缓存，冷启动直接读缓存不白屏
        SoftwareNewsData.init(this)
        // 开屏阶段就提前后台拉取两份新闻数据，等跳首页的时候数据已经拉完了
        SoftwareNewsData.loadHotArticles()
        SoftwareNewsData.loadLatestArticles()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1500)
    }
}
