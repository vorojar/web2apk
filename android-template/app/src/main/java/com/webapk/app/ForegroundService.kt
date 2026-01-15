package com.webapk.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 前台服务：通知栏常驻，实现进程保活
 * 
 * 使用方式（网页JS调用）：
 * - 启动：Web2APK.startForegroundService("音乐播放中", "正在播放: 周杰伦 - 晴天")
 * - 停止：Web2APK.stopForegroundService()
 * - 更新通知：Web2APK.updateForegroundNotification("音乐播放中", "正在播放: 林俊杰 - 江南")
 */
class ForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "foreground_service_channel"
        const val NOTIFICATION_ID = 10001
        
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_CONTENT = "extra_content"
        private const val ACTION_UPDATE = "action_update"
        
        /**
         * 启动前台服务
         */
        fun start(context: Context, title: String, content: String) {
            val intent = Intent(context, ForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 更新通知内容（不重启服务）
         */
        fun update(context: Context, title: String, content: String) {
            val intent = Intent(context, ForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
            }
            context.startService(intent)
        }
        
        /**
         * 停止前台服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundService::class.java))
        }
        
        /**
         * 检查服务是否正在运行
         */
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (ForegroundService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    private var currentTitle = "应用运行中"
    private var currentContent = "点击返回应用"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
        val content = intent?.getStringExtra(EXTRA_CONTENT) ?: currentContent
        
        currentTitle = title
        currentContent = content
        
        val notification = createNotification(title, content)
        
        if (intent?.action == ACTION_UPDATE) {
            // 仅更新通知内容
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } else {
            // 启动前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ 需要指定类型，使用MEDIA_PLAYBACK覆盖大部分场景
                startForeground(
                    NOTIFICATION_ID, 
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        
        return START_STICKY // 被系统杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台服务",
                NotificationManager.IMPORTANCE_LOW // LOW = 静默通知，不发声
            ).apply {
                description = "应用正在后台运行"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        // 点击通知时打开 MainActivity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 常驻通知，用户无法滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
