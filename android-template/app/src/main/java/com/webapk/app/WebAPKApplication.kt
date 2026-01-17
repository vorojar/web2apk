package com.webapk.app

import android.app.Application
import android.os.Build
import android.webkit.WebView

/**
 * 应用程序入口
 * 主要用于 WebView 预热，加快首屏加载速度
 */
class WebAPKApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 预热 WebView（在后台线程执行避免阻塞启动）
        if (resources.getBoolean(R.bool.webview_preload)) {
            preloadWebView()
        }
    }

    /**
     * 预热 WebView
     *
     * WebView 首次创建时会加载大量原生库和资源，导致明显卡顿。
     * 通过提前创建并立即销毁一个 WebView 实例，可以：
     * 1. 触发 WebView 引擎初始化
     * 2. 加载原生库到内存
     * 3. 初始化 V8 JavaScript 引擎
     *
     * 后续创建 WebView 时就能复用这些已初始化的资源，大幅加快加载速度。
     */
    private fun preloadWebView() {
        try {
            // 在 Android 5.0+ 使用多进程 WebView，预热效果更明显
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ 可以设置 Data Directory Suffix（多进程安全）
                WebView.setDataDirectorySuffix("webview")
            }

            // 创建临时 WebView 触发初始化
            val webView = WebView(applicationContext)

            // 启用基础设置触发更多初始化
            webView.settings.javaScriptEnabled = true

            // 立即销毁，释放资源
            webView.destroy()
        } catch (e: Exception) {
            // 预热失败不影响正常使用，静默处理
            e.printStackTrace()
        }
    }
}
