package com.webapk.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

/**
 * 桌面小组件 Provider
 * 网页通过 JS 接口推送数据，Widget 显示
 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val PREFS_NAME = "widget_data"
        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private const val KEY_SUBTITLE = "subtitle"
        private const val KEY_ICON = "icon"
        private const val KEY_BADGE = "badge"
        private const val KEY_CLICK_URL = "click_url"
        private const val KEY_TIMESTAMP = "timestamp"

        /**
         * 保存 Widget 数据（由 JS Bridge 调用）
         */
        fun saveWidgetData(
            context: Context,
            title: String?,
            content: String?,
            subtitle: String?,
            icon: String?,
            badge: Int,
            clickUrl: String?
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_TITLE, title)
                putString(KEY_CONTENT, content)
                putString(KEY_SUBTITLE, subtitle)
                putString(KEY_ICON, icon)
                putInt(KEY_BADGE, badge)
                putString(KEY_CLICK_URL, clickUrl)
                putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                apply()
            }

            // 通知所有 Widget 更新
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        /**
         * 更新单个 Widget
         */
        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val title = prefs.getString(KEY_TITLE, null)
            val content = prefs.getString(KEY_CONTENT, null)
            val subtitle = prefs.getString(KEY_SUBTITLE, null)
            val icon = prefs.getString(KEY_ICON, "📱")
            val badge = prefs.getInt(KEY_BADGE, 0)
            val clickUrl = prefs.getString(KEY_CLICK_URL, null)
            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // 设置图标（Emoji）
            views.setTextViewText(R.id.widget_icon, icon ?: "📱")

            // 设置标题
            if (title != null) {
                views.setTextViewText(R.id.widget_title, title)
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            }

            // 设置内容
            if (content != null) {
                views.setTextViewText(R.id.widget_content, content)
            } else {
                views.setTextViewText(R.id.widget_content, "打开 APP 同步数据")
            }

            // 设置副标题
            if (subtitle != null) {
                views.setTextViewText(R.id.widget_subtitle, subtitle)
                views.setViewVisibility(R.id.widget_subtitle, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_subtitle, android.view.View.GONE)
            }

            // 设置角标
            if (badge > 0) {
                views.setTextViewText(R.id.widget_badge, badge.toString())
                views.setViewVisibility(R.id.widget_badge, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_badge, android.view.View.GONE)
            }

            // 设置更新时间
            if (timestamp > 0) {
                val timeText = getRelativeTimeText(timestamp)
                views.setTextViewText(R.id.widget_time, timeText)
                views.setViewVisibility(R.id.widget_time, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_time, android.view.View.GONE)
            }

            // 设置点击事件
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (clickUrl != null) {
                    val baseUrl = context.getString(R.string.web_url).trimEnd('/')
                    val fullUrl = if (clickUrl.startsWith("http")) clickUrl else baseUrl + clickUrl
                    data = Uri.parse(fullUrl)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * 获取相对时间文字
         */
        private fun getRelativeTimeText(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "刚刚"
                diff < 3600_000 -> "${diff / 60_000} 分钟前"
                diff < 86400_000 -> "${diff / 3600_000} 小时前"
                else -> "${diff / 86400_000} 天前"
            }
        }
    }
}
