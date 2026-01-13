package com.webapk.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var splashScreen: FrameLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorView: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private var isFirstLoad = true
    private var hasError = false

    // 文件上传相关
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    // 视频全屏相关
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明状态栏，内容延伸到状态栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        splashScreen = findViewById(R.id.splashScreen)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        errorView = findViewById(R.id.errorView)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        // 给内容区域添加顶部 padding = 状态栏高度
        val statusBarHeight = getStatusBarHeight()
        swipeRefresh.setPadding(0, statusBarHeight, 0, 0)
        errorView.setPadding(0, statusBarHeight, 0, 0)

        setupActivityResultLaunchers()
        setupSwipeRefresh()
        setupWebView()
        setupRetryButton()
        loadUrl()
    }

    private fun setupActivityResultLaunchers() {
        // 文件选择结果
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val results = when {
                    data?.clipData != null -> {
                        val count = data.clipData!!.itemCount
                        Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> null
                }
                filePathCallback?.onReceiveValue(results)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }

        // 拍照结果
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraPhotoUri != null) {
                filePathCallback?.onReceiveValue(arrayOf(cameraPhotoUri!!))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
            cameraPhotoUri = null
        }

        // 相机权限请求（仅用于拍照）
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
        }

        // Photo Picker（Android 13+ 无需权限）
        photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                filePathCallback?.onReceiveValue(arrayOf(uri))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }
    }

    private fun setupSwipeRefresh() {
        // 根据配置启用或禁用下拉刷新
        val pullToRefreshEnabled = resources.getBoolean(R.bool.pull_to_refresh_enabled)
        swipeRefresh.isEnabled = pullToRefreshEnabled

        if (!pullToRefreshEnabled) return

        swipeRefresh.setOnRefreshListener {
            hideError()
            webView.reload()
        }
        swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun setupRetryButton() {
        retryButton.setOnClickListener {
            hideError()
            webView.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false

            // 设置 User-Agent
            userAgentString = userAgentString.replace("; wv", "")
        }

        // 设置下载监听器
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasError = false
                if (!isFirstLoad) {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false

                // 首次加载完成后，淡出启动画面
                if (isFirstLoad) {
                    isFirstLoad = false
                    hideSplashScreen()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // 检查是否是需要强制下载的文件类型
                val downloadExtensions = listOf(
                    ".mp4", ".mp3", ".wav", ".ogg", ".webm",  // 音视频
                    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp",  // 图片
                    ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",  // 文档
                    ".zip", ".rar", ".7z", ".tar", ".gz",  // 压缩包
                    ".apk", ".exe", ".dmg"  // 安装包
                )
                val urlLower = url.lowercase()
                val isDownloadableFile = downloadExtensions.any { ext -> 
                    urlLower.endsWith(ext) || urlLower.contains("$ext?") || urlLower.contains("$ext#")
                }

                if (isDownloadableFile) {
                    // 强制下载，不让 WebView 打开
                    val mimeType = getMimeTypeFromUrl(url)
                    downloadFile(url, webView.settings.userAgentString, "", mimeType)
                    return true
                }

                // 处理外部链接（电话、邮件等）
                return if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                           url.startsWith("sms:") || url.startsWith("intent:")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                } else {
                    false
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showError("页面加载失败", "请检查网络连接后重试")
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: 0
                    val message = when (statusCode) {
                        404 -> "页面不存在 (404)"
                        500 -> "服务器错误 (500)"
                        502 -> "网关错误 (502)"
                        503 -> "服务不可用 (503)"
                        else -> "HTTP 错误 ($statusCode)"
                    }
                    showError("页面加载失败", message)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // 重要：先取消之前未完成的回调，避免 WebView 卡住
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()
                val isImageType = acceptTypes.any { it.contains("image") }
                val isVideoType = acceptTypes.any { it.contains("video") }
                val isCaptureEnabled = fileChooserParams?.isCaptureEnabled == true

                when {
                    // 直接拍照模式
                    isImageType && isCaptureEnabled -> {
                        requestCameraAndLaunch()
                    }
                    // 图片类型：显示选择对话框
                    isImageType -> {
                        showImageSourceDialog()
                    }
                    // 视频类型：用 Photo Picker
                    isVideoType -> {
                        launchVideoPicker()
                    }
                    // 其他文件类型：直接打开文件选择器
                    else -> {
                        launchFilePicker(fileChooserParams)
                    }
                }
                return true
            }

            // 视频全屏播放
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback
                originalOrientation = requestedOrientation

                // 隐藏主内容，显示全屏容器
                swipeRefresh.visibility = View.GONE
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view)

                // 切换到横屏
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                // 隐藏系统栏
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            override fun onHideCustomView() {
                if (customView == null) return

                // 移除全屏视图
                fullscreenContainer.removeView(customView)
                fullscreenContainer.visibility = View.GONE
                swipeRefresh.visibility = View.VISIBLE

                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null

                // 恢复屏幕方向
                requestedOrientation = originalOrientation

                // 显示系统栏
                WindowInsetsControllerCompat(window, window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("拍照", "从相册选择")
        AlertDialog.Builder(this)
            .setTitle("选择图片来源")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraAndLaunch()
                    1 -> launchPhotoPicker()
                }
            }
            .setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }

    private fun requestCameraAndLaunch() {
        // 只检查相机权限，不需要存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            cameraPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(cameraPhotoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }

    // 使用系统 Photo Picker（无需权限）
    private fun launchPhotoPicker() {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun launchVideoPicker() {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    private fun launchFilePicker(params: WebChromeClient.FileChooserParams?) {
        val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        fileChooserLauncher.launch(intent)
    }

    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        try {
            // 从 Content-Disposition 或 URL 提取文件名
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            
            // 解码 URL 编码的文件名
            try {
                fileName = URLDecoder.decode(fileName, "UTF-8")
            } catch (e: Exception) {
                // 保持原始文件名
            }

            // 获取 WebView 的 Cookie
            val cookies = CookieManager.getInstance().getCookie(url)

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                // 添加 Cookie 头（解决需要认证的下载）
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
                // 添加 Referer 头（解决防盗链）
                addRequestHeader("Referer", url)
                setTitle(fileName)
                setDescription("正在下载...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                // 允许在移动网络和 WiFi 下下载
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                // 允许漫游时下载
                setAllowedOverRoaming(true)
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "开始下载: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeTypeFromUrl(url: String): String {
        val extension = url.lowercase().substringAfterLast(".").substringBefore("?").substringBefore("#")
        return when (extension) {
            // 音视频
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "webm" -> "video/webm"
            // 图片
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            // 文档
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            // 压缩包
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            // 安装包
            "apk" -> "application/vnd.android.package-archive"
            "exe" -> "application/x-msdownload"
            "dmg" -> "application/x-apple-diskimage"
            // 默认
            else -> "application/octet-stream"
        }
    }

    private fun showError(title: String, message: String) {
        hasError = true
        swipeRefresh.isRefreshing = false
        errorTitle.text = title
        errorMessage.text = message
        errorView.visibility = View.VISIBLE

        if (isFirstLoad) {
            isFirstLoad = false
            hideSplashScreen()
        }
    }

    private fun hideError() {
        hasError = false
        errorView.visibility = View.GONE
    }

    private fun hideSplashScreen() {
        val statusBarColor = getColor(R.color.status_bar_color)
        window.statusBarColor = statusBarColor

        val isLightBackground = isColorLight(statusBarColor)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLightBackground

        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 300
            fillAfter = true
        }
        splashScreen.startAnimation(fadeOut)

        splashScreen.postDelayed({
            splashScreen.visibility = View.GONE
        }, 300)
    }

    private fun isColorLight(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance > 0.5
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun loadUrl() {
        val url = getString(R.string.web_url)
        webView.loadUrl(url)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 优先退出视频全屏
            if (customView != null) {
                customViewCallback?.onCustomViewHidden()
                return true
            }
            if (hasError) {
                hideError()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
