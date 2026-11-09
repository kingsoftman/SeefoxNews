package com.hongtian.seefox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomeFragment : Fragment() {

    private var hotHandler: Handler? = null
    private var hotRunnable: Runnable? = null
    private var isFragmentDestroyed = false
    // 搜索行折叠进度：0 完全展开，searchRowFullHeight 完全收起
    private var collapseOffset = 0
    private var searchRowFullHeight = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        return i.inflate(R.layout.fragment_home, c, false)
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val ctx = requireContext()

        // 整块内容避开系统状态栏
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val statusTop = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusTop, 0, 0)
            insets
        }

        // 记录搜索行完整高度，供折叠使用
        val searchRow = v.findViewById<View>(R.id.searchRow)
        searchRow.post { if (searchRowFullHeight == 0) searchRowFullHeight = searchRow.height }

        // 点击 Logo 跳到「我的」页面
        v.findViewById<View>(R.id.logoIv).setOnClickListener {
            (activity as? MainActivity)?.selectTab(4)
        }

        // 点击搜索框进入搜索页
        v.findViewById<View>(R.id.searchBar).setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        // ViewPager2
        val pager = v.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.homePager)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 6
            override fun createFragment(position: Int) = NewsListFragment.newInstance(position)
        }

        // 下拉刷新
        val swipe = v.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe?.setOnRefreshListener {
            val frag = childFragmentManager.findFragmentByTag("f${pager.currentItem}")
            if (frag is NewsListFragment) frag.refresh()
            swipe.isRefreshing = false
        }
        // 降低敏感度：下拉距离设为默认的2倍
        swipe?.setDistanceToTriggerSync((resources.displayMetrics.density * 96).toInt())

        // 左右拖动时禁用下拉刷新，拖完恢复
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                swipe?.isEnabled = (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE)
            }
        })

        // 标签栏联动
        val catIds = listOf(R.id.cat0, R.id.cat1, R.id.cat2, R.id.cat3, R.id.cat4, R.id.cat5)
        fun selectCat(idx: Int) {
            catIds.forEachIndexed { i, id ->
                val tv = v.findViewById<TextView>(id)
                if (i == idx) {
                    tv.setTextColor(0xFF7C4DFF.toInt())
                    tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                    tv.setBackgroundResource(R.drawable.bg_cat_active)
                } else {
                    tv.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
                    tv.setTypeface(tv.typeface, android.graphics.Typeface.NORMAL)
                    tv.background = null
                }
            }
            pager.currentItem = idx
        }
        catIds.forEachIndexed { i, id ->
            v.findViewById<TextView>(id).setOnClickListener { selectCat(i) }
        }
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectCat(position)
                // 切换标签页时搜索行恢复展开
                resetTopBar()
                // 标签栏自动滚动到当前位置
                v.findViewById<android.widget.HorizontalScrollView>(R.id.catScroll)?.post {
                    val tv = v.findViewById<TextView>(catIds[position])
                    v.findViewById<android.widget.HorizontalScrollView>(R.id.catScroll)?.smoothScrollTo(
                        (tv.left - tv.width), 0
                    )
                }
            }
        })
    }

    // 子列表滚动回调：dy > 0 表示内容上滑，搜索行收起；dy < 0 表示下拉，搜索行展开
    fun onListScroll(dy: Int) {
        if (isFragmentDestroyed) return
        val v = view ?: return
        val row = v.findViewById<View>(R.id.searchRow) ?: return
        if (searchRowFullHeight == 0) {
            searchRowFullHeight = row.height
            if (searchRowFullHeight == 0) return
        }
        collapseOffset = (collapseOffset + dy).coerceIn(0, searchRowFullHeight)
        applyCollapse(row)
    }

    // 列表回到最顶部时，搜索行完整展开
    fun resetTopBar() {
        if (isFragmentDestroyed) return
        val row = view?.findViewById<View>(R.id.searchRow) ?: return
        if (searchRowFullHeight == 0) searchRowFullHeight = row.height
        if (collapseOffset == 0) {
            applyCollapse(row)
            return
        }
        collapseOffset = 0
        applyCollapse(row)
    }

    private fun applyCollapse(row: View) {
        val target = searchRowFullHeight - collapseOffset
        val lp = row.layoutParams
        if (lp.height != target) {
            lp.height = target
            row.layoutParams = lp
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentDestroyed = true
        hotHandler?.let { h -> hotRunnable?.let { r -> h.removeCallbacks(r) } }
    }
}
