package com.hongtian.seefox

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SeefoxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 创建通知渠道（幂等）
        NewsNotify.ensureChannel(this)
        scheduleNewsCheck(this)
    }

    private fun scheduleNewsCheck(ctx: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1) 每次打开 APP 顺带检查一次（替换旧的，立即执行）
            val oneTime = OneTimeWorkRequestBuilder<NewsCheckWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("seefox_news_check_once", ExistingWorkPolicy.REPLACE, oneTime)

            // 2) 后台每 30 分钟检查一次（KEEP，避免每次启动把周期重置）
            val periodic = PeriodicWorkRequestBuilder<NewsCheckWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("seefox_news_check", ExistingPeriodicWorkPolicy.KEEP, periodic)
        } catch (e: Exception) {
            android.util.Log.e("SeefoxWorker", "schedule failed", e)
        }
    }

    companion object {
        fun hasNotificationPermission(ctx: Context): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                return ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }
    }
}
