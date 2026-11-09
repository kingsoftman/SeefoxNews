package com.hongtian.seefox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class CategoryFragment : Fragment() {

    companion object {
        private const val ARG_CATEGORY = "category"
        fun newInstance(category: String): CategoryFragment {
            val args = Bundle()
            args.putString(ARG_CATEGORY, category)
            return CategoryFragment().apply { arguments = args }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val category = arguments?.getString(ARG_CATEGORY) ?: "推荐"
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 100)
        }

        val title = TextView(requireContext()).apply {
            text = "$category 频道"
            textSize = 20f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)

        val sampleTitles = when (category) {
            "官方ROM" -> listOf("小米15官方固件下载", "三星S25官方ROM升级", "OPPO Find X8官方固件", "vivo X200系统更新", "一加13氧OS正式版")
            "第三方ROM" -> listOf("LineageOS 22适配清单", "PixelExperience 15下载", "crDroid Android 16发布", "Evolution X最新版", "Havoc OS刷机教程")
            "Root教程" -> listOf("Magisk 27.0安装教程", "KernelSU最新版使用", "LSPosed框架配置", "EdXposed模块推荐", "Root后必装Xposed模块")
            "刷机工具" -> listOf("SP Flash Tool使用指南", "Odin三星线刷教程", "Fastboot命令大全", "ADB工具包下载", "TWRP Recovery安装")
            "救砖教程" -> listOf("联发科手机救砖方法", "高通9008模式救砖", "小米变砖恢复官方", "三星刷机失败修复", "HTC Unbrick工具")
            else -> listOf("LineageOS 22正式发布", "Magisk 27.0支持Android 16", "小米Bootloader解锁新规", "三星Odin更新", "SP Flash Tool适配天玑9400")
        }

        sampleTitles.forEach { t ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundResource(R.drawable.bg_card_rounded)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 16
                layoutParams = lp
            }
            val tv = TextView(requireContext()).apply {
                text = t
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val meta = TextView(requireContext()).apply {
                text = "$category · 2小时前"
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                setPadding(0, 8, 0, 0)
            }
            card.addView(tv)
            card.addView(meta)
            container.addView(card)
        }

        return container
    }
}
