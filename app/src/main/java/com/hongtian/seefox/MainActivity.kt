package com.hongtian.seefox

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private lateinit var tabs: List<LinearLayout>
    private lateinit var tabIcons: List<ImageView>
    private lateinit var tabTexts: List<TextView>
    private val fragmentTags = listOf("home", "fun", "features", "msg", "me")
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // 深浅色完全跟随系统
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermission()

        // 开 edge-to-edge，让横屏时内容延伸到屏幕边缘
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        tabs = listOf(
            findViewById(R.id.tab_home),
            findViewById(R.id.tab_fun),
            findViewById(R.id.tab_features),
            findViewById(R.id.tab_msg),
            findViewById(R.id.tab_me)
        )
        tabIcons = tabs.map { it.getChildAt(0) as ImageView }
        tabTexts = tabs.map { it.getChildAt(1) as TextView }

        // 确保所有Fragment都已添加（用hide/show方式，不销毁）
        if (savedInstanceState == null) {
            val ft = supportFragmentManager.beginTransaction()
            ft.add(R.id.container, HomeFragment(), fragmentTags[0])
            ft.add(R.id.container, FunFragment(), fragmentTags[1])
            ft.add(R.id.container, FeaturesFragment(), fragmentTags[2])
            ft.add(R.id.container, MessagesFragment(), fragmentTags[3])
            ft.add(R.id.container, MeFragment(), fragmentTags[4])
            ft.commitNow()
        }

        fun select(index: Int) = selectTab(index)

        tabs.forEachIndexed { i, tab ->
            tab.setOnClickListener { select(i) }
        }

        if (savedInstanceState == null) {
            select(0)
        } else {
            // 恢复上次选中的Tab
            val savedIndex = savedInstanceState.getInt("current_tab", 0)
            select(savedIndex)
        }
    }

    // 供其它页面调用：切换到指定底部导航页（0 首页 1 发现 2 功能 3 消息 4 我的）
    fun selectTab(index: Int) {
        currentIndex = index
        val unselectedColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        tabs.forEachIndexed { i, tab ->
            val selected = i == index
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else android.R.color.transparent
            )
            val iconColor = if (selected) 0xFFFFFFFF.toInt() else unselectedColor
            tabIcons[i].setColorFilter(iconColor)
            tabTexts[i].setTextColor(iconColor)
            tabTexts[i].visibility = if (selected) View.VISIBLE else View.GONE
        }
        // hide/show方式切换，不销毁Fragment
        val ft = supportFragmentManager.beginTransaction()
        fragmentTags.forEachIndexed { i, tag ->
            val frag = supportFragmentManager.findFragmentByTag(tag)
            if (frag != null) {
                if (i == index) ft.show(frag) else ft.hide(frag)
            }
        }
        ft.commitNow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_tab", currentIndex)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentByTag(fragmentTags[currentIndex])
        if (currentFragment is FunFragment && currentFragment.onBackPressed()) {
            return
        }
        super.onBackPressed()
    }

    // Android 13+ 需要用户授权才能发通知，首次进入申请一次
    private val notifyPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !SeefoxApp.hasNotificationPermission(this)) {
            notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
