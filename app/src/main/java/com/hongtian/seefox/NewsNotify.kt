package com.hongtian.seefox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.net.HttpURLConnection
import java.net.URL

/**
 * 新文章通知：系统状态栏的图文大图通知
 */
object NewsNotify {

    const val CHANNEL_ID = "seefox_new_articles"
    private const val CHANNEL_NAME = "新文章提醒"
    private const val NOTIFY_ID = 1001

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "网站发布新文章时提醒"
                    enableVibration(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 发出新文章通知
     * @param extraCount 除首条外还有几篇新文章
     */
    fun show(ctx: Context, article: NewsCheckWorker.SimpleArticle, extraCount: Int) {
        if (!SeefoxApp.hasNotificationPermission(ctx)) return
        ensureChannel(ctx)

        val bigBitmap = downloadBitmap(article.cover)

        val intent = Intent(ctx, SoftwareDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("title", article.title)
            putExtra("category", article.category)
            putExtra("date", article.date)
            putExtra("html", article.html)
        }
        val pi = PendingIntent.getActivity(
            ctx, article.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (extraCount > 0) {
            "${article.category} · ${article.excerpt}\n还有 ${extraCount} 篇新内容，点击查看"
        } else {
            "${article.category} · ${article.excerpt}"
        }

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(article.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(Notification.DEFAULT_ALL)

        if (bigBitmap != null) {
            builder.setLargeIcon(bigBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bigBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(article.title)
                    .setSummaryText(body)
            )
        }

        try {
            val nm = NotificationManagerCompat.from(ctx)
            nm.notify(NOTIFY_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    // 下载封面并压到合适尺寸，避免通知里的大图过大
    private fun downloadBitmap(url: String): Bitmap? {
        if (url.isEmpty()) return null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            conn.doInput = true
            val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            raw?.let {
                val maxW = 1000
                if (it.width > maxW) {
                    val h = it.height * maxW / it.width
                    Bitmap.createScaledBitmap(it, maxW, h, true)
                } else it
            }
        } catch (_: Exception) {
            null
        }
    }
}
