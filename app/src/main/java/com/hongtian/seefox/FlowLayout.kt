package com.hongtian.seefox

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 简易流式布局：子 View 按行排列，超出宽度自动换行，用于展示搜索历史/热门搜索的标签
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val hSpace = (8 * resources.displayMetrics.density).toInt()
    private val vSpace = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val available = widthSize - paddingLeft - paddingRight
        var lineWidth = 0
        var totalHeight = paddingTop + paddingBottom
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            if (lineWidth + cw > available && lineWidth > 0) {
                totalHeight += lineHeight + vSpace
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += if (lineWidth == 0) cw else cw + hSpace
            if (ch > lineHeight) lineHeight = ch
        }
        totalHeight += lineHeight
        setMeasuredDimension(widthSize, totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            if (x - paddingLeft + cw > width && x > paddingLeft) {
                x = paddingLeft
                y += lineHeight + vSpace
                lineHeight = 0
            }
            child.layout(x, y, x + cw, y + ch)
            x += cw + hSpace
            if (ch > lineHeight) lineHeight = ch
        }
    }
}
