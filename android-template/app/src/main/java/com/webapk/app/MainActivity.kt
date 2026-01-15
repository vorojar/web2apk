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
import android.app.PictureInPictureParams
import android.util.Rational
import android.content.res.Configuration

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

    // 位置权限相关
    private var geolocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>

    // 扫码相关
    private lateinit var scanLauncher: ActivityResultLauncher<Intent>

    // 通知权限相关
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    // 网络状态监听
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            runOnUiThread { notifyNetworkStatus() }
        }
        override fun onLost(network: android.net.Network) {
            runOnUiThread { notifyNetworkStatus() }
        }
        override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
            runOnUiThread { notifyNetworkStatus() }
        }
    }

    private fun notifyNetworkStatus() {
        val status = getNetworkStatus()
        val isConnected = status != "none"
        webView.evaluateJavascript(
            "if(typeof onNetworkChange==='function'){onNetworkChange($isConnected,'$status')}",
            null
        )
    }

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

        // 位置权限请求
        locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val granted = fineGranted || coarseGranted
            geolocationCallback?.invoke(geolocationOrigin, granted, false)
            geolocationCallback = null
            geolocationOrigin = null
        }

        // 扫码结果
        scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = result.data?.getStringExtra("SCAN_RESULT")
                if (!scanResult.isNullOrEmpty()) {
                    // 转义单引号，防止 JS 注入
                    val safeResult = scanResult.replace("'", "\\'")
                    webView.evaluateJavascript("if(typeof onScanResult==='function'){onScanResult('$safeResult')}", null)
                }
            }
        }

        // 通知权限请求
        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "需要通知权限才能接收提醒", Toast.LENGTH_SHORT).show()
            }
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
            
            // 强制开启深色模式支持 (兼容国产 ROM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // FORCE_DARK_AUTO: 让 WebView 自动适配系统深色模式
                // 如果网页定义了 prefers-color-scheme，WebView 会尊重它
                // 如果没定义，WebView 会尝试算法变黑 (Algorithmic Darkening)
                forceDark = WebSettings.FORCE_DARK_AUTO
            }
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false

            // 设置 User-Agent（附加 Web2APK 标识）
            val defaultUA = userAgentString.replace("; wv", "")
            userAgentString = "$defaultUA Web2APK/1.0"
        }

        // 设置下载监听器
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }

        // 添加 JavaScript Interface（提供原生能力给网页）
        webView.addJavascriptInterface(Web2APKBridge(this), "Web2APK")

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

                // 页面加载完成后，主动通知当前网络状态
                notifyNetworkStatus()
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

                // 处理特殊协议（电话、邮件等）
                if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("sms:") || url.startsWith("intent:")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return true
                }

                // 检查是否是外域链接
                val mainHost = Uri.parse(getString(R.string.web_url)).host ?: ""
                val linkHost = Uri.parse(url).host ?: ""

                // 外域链接用内置浏览器打开
                if (linkHost.isNotEmpty() && mainHost.isNotEmpty() && !linkHost.endsWith(mainHost) && !mainHost.endsWith(linkHost)) {
                    ExternalBrowserActivity.start(this@MainActivity, url)
                    return true
                }

                // 同域链接在当前 WebView 正常加载
                return false
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

                // Android 12+ 自动进入画中画
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(true)
                        .build()
                    this@MainActivity.setPictureInPictureParams(params)
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

                // 退出全屏时关闭自动画中画
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this@MainActivity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
                }
            }

            // JS alert() 弹窗
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // JS confirm() 弹窗
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // JS prompt() 弹窗
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                val input = android.widget.EditText(this@MainActivity).apply {
                    setText(defaultValue)
                    setSingleLine()
                    setPadding(48, 24, 48, 24)
                }
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("确定") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // 位置权限请求（网页调用 navigator.geolocation 时触发）
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                geolocationOrigin = origin
                geolocationCallback = callback

                // 检查是否已有权限
                val fineLocation = android.Manifest.permission.ACCESS_FINE_LOCATION
                val coarseLocation = android.Manifest.permission.ACCESS_COARSE_LOCATION
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, fineLocation) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, coarseLocation) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    // 已有权限，直接允许
                    callback?.invoke(origin, true, false)
                    geolocationCallback = null
                    geolocationOrigin = null
                } else {
                    // 请求权限
                    locationPermissionLauncher.launch(arrayOf(fineLocation, coarseLocation))
                }
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
        // 处理分享进来的链接（用内置浏览器打开）
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            handleSharedUrl(intent)
        }
        // 处理 Deep Link 传入的 URL
        val intentUrl = intent?.data?.toString()
        if (!intentUrl.isNullOrEmpty()) {
            webView.loadUrl(intentUrl)
        } else {
            val url = getString(R.string.web_url)
            webView.loadUrl(url)
        }
    }

    private fun handleSharedUrl(intent: Intent?) {
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: return
        // 提取 URL（分享的文字可能包含其他内容）
        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        val matchResult = urlPattern.find(sharedText)
        val url = matchResult?.value ?: return
        // 用内置浏览器打开
        ExternalBrowserActivity.start(this, url)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // 处理分享进来的链接
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            handleSharedUrl(intent)
            return
        }
        // 处理 singleTask 模式下的 Deep Link
        intent?.data?.let { uri ->
            webView.loadUrl(uri.toString())
        }
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

    override fun onUserLeaveHint() {
        // Android 8.0 - 11 手动进入画中画 (Android 12+ 使用 setAutoEnterEnabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (customView != null) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
        super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // 隐藏不必要的 UI
            swipeRefresh.visibility = View.GONE
            fullscreenContainer.visibility = View.VISIBLE
        } else {
            // 退出画中画，恢复全屏或正常状态
            if (customView != null) {
                fullscreenContainer.visibility = View.VISIBLE
            } else {
                swipeRefresh.visibility = View.VISIBLE
                fullscreenContainer.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // 注册网络状态监听
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(networkCallback)
        } else {
            val request = android.net.NetworkRequest.Builder().build()
            cm.registerNetworkCallback(request, networkCallback)
        }
    }

    override fun onPause() {
        // 如果处于画中画模式，继续播放视频，不暂停 WebView
        if (isInPictureInPictureMode) {
            super.onPause()
            return
        }

        super.onPause()
        webView.onPause()
        // 注销网络状态监听
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        try { cm.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /**
     * 获取当前网络状态
     * @return wifi / mobile / vpn+wifi / vpn+mobile / vpn / none
     */
    private fun getNetworkStatus(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "none"

        // 检查网络是否真的可以上网
        if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return "none"
        }

        val hasVpn = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
        val hasWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        val hasMobile = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)

        return when {
            hasVpn && hasWifi -> "vpn+wifi"
            hasVpn && hasMobile -> "vpn+mobile"
            hasVpn -> "vpn"
            hasWifi -> "wifi"
            hasMobile -> "mobile"
            else -> "other"
        }
    }

    /**
     * JavaScript Interface - 提供原生能力给网页
     * 网页可通过 window.Web2APK.xxx() 调用
     */
    inner class Web2APKBridge(private val context: Context) {

        /**
         * 系统分享
         * @param title 分享标题
         * @param text 分享内容
         * @param url 分享链接（可选）
         */
        @android.webkit.JavascriptInterface
        fun share(title: String, text: String, url: String?) {
            val shareText = if (url.isNullOrEmpty()) text else "$text $url"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "分享到"))
        }

        /**
         * 检查是否支持分享
         */
        @android.webkit.JavascriptInterface
        fun canShare(): Boolean = true

        /**
         * 获取网络状态
         * @return "wifi" / "mobile" / "none" / "other"
         */
        @android.webkit.JavascriptInterface
        fun getNetworkStatus(): String = this@MainActivity.getNetworkStatus()

        /**
         * 获取 APP 版本号
         */
        @android.webkit.JavascriptInterface
        fun getVersion(): String {
            return try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
        }

        /**
         * 屏幕常亮控制
         * @param on true=保持常亮, false=恢复正常
         */
        @android.webkit.JavascriptInterface
        fun keepScreenOn(on: Boolean) {
            (context as? Activity)?.runOnUiThread {
                if (on) {
                    (context as? Activity)?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    (context as? Activity)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        /**
         * 振动反馈
         * @param milliseconds 振动时长（毫秒）
         */
        @android.webkit.JavascriptInterface
        fun vibrate(milliseconds: Int) {
            val duration = milliseconds.toLong()
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // 使用 ALARM 类型的 AudioAttributes，确保优先级最高（即使在静音模式下）
                val effect = android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                val attributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }

        /**
         * 扫描二维码
         */
        @android.webkit.JavascriptInterface
        fun scanQRCode() {
            val intent = Intent(context, ScanActivity::class.java)
            scanLauncher.launch(intent)
        }

        /**
         * 发送本地通知
         * @param title 标题
         * @param message 内容
         */
        @android.webkit.JavascriptInterface
        fun sendNotification(title: String, message: String) {
            scheduleNotification(title, message, 0)
        }

        /**
         * 延时发送通知
         * @param title 标题
         * @param message 内容
         * @param delayMillis 延时毫秒数
         */
        @android.webkit.JavascriptInterface
        fun scheduleNotification(title: String, message: String, delayMillis: Long) {
            // 检查权限 (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("message", message)
            }
            
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = System.currentTimeMillis() + delayMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                     alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            
            if (delayMillis > 0) {
                Toast.makeText(context, "已设置提醒", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * 设置状态栏颜色
         * @param colorHex 颜色值 (e.g. "#FFFFFF", "#FF0000")
         */
        @android.webkit.JavascriptInterface
        fun setStatusBarColor(colorHex: String) {
            (context as? Activity)?.runOnUiThread {
                try {
                    val color = Color.parseColor(colorHex)
                    val window = (context as? Activity)?.window ?: return@runOnUiThread
                    
                    window.statusBarColor = color
                    
                    // 根据背景色亮度自动调整图标颜色
                    val isDarkBackground = androidx.core.graphics.ColorUtils.calculateLuminance(color) < 0.5
                    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkBackground
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * 清理缓存
         * @param includeDiskFiles 是否清理磁盘文件（true=清理所有，false=仅清理内存）
         */
        @android.webkit.JavascriptInterface
        fun clearCache(includeDiskFiles: Boolean) {
            (context as? Activity)?.runOnUiThread {
                try {
                    // 清除 WebView 缓存
                    webView.clearCache(includeDiskFiles)
                    Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * 更新桌面小组件
         * @param jsonData JSON 格式的数据: {title, content, subtitle?, icon?, badge?, clickUrl?}
         */
        @android.webkit.JavascriptInterface
        fun updateWidget(jsonData: String) {
            try {
                val json = org.json.JSONObject(jsonData)
                val title = json.optString("title", null)
                val content = json.optString("content", null)
                val subtitle = json.optString("subtitle", null)
                val icon = json.optString("icon", null)
                val badge = json.optInt("badge", 0)
                val clickUrl = json.optString("clickUrl", null)

                WidgetProvider.saveWidgetData(
                    context,
                    title,
                    content,
                    subtitle,
                    icon,
                    badge,
                    clickUrl
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /**
         * 请求将小组件固定到桌面（Android 8.0+）
         * 会弹出系统对话框让用户确认
         * @return true=请求已发送, false=不支持或失败
         */
        @android.webkit.JavascriptInterface
        fun requestPinWidget(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return false
            }
            return try {
                val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                if (!appWidgetManager.isRequestPinAppWidgetSupported) {
                    return false
                }
                val widgetProvider = android.content.ComponentName(context, WidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * 设置快捷方式（长按图标菜单）
         * @param jsonArray JSON 数组: [{id, label, icon?, url}]
         * Android 最多支持 4 个动态快捷方式
         */
        @android.webkit.JavascriptInterface
        fun setShortcuts(jsonArray: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
                return // Android 7.1+ 才支持
            }
            try {
                val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                val jsonArr = org.json.JSONArray(jsonArray)
                val shortcuts = mutableListOf<android.content.pm.ShortcutInfo>()
                val baseUrl = context.getString(R.string.web_url).trimEnd('/')

                for (i in 0 until minOf(jsonArr.length(), 4)) { // 最多 4 个
                    val item = jsonArr.getJSONObject(i)
                    val id = item.getString("id")
                    val label = item.getString("label")
                    val icon = item.optString("icon", null)
                    val urlPath = item.getString("url")
                    val fullUrl = if (urlPath.startsWith("http")) urlPath else baseUrl + urlPath

                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(fullUrl)
                    }

                    val shortcutIcon = if (!icon.isNullOrEmpty()) {
                        // Emoji 转 Bitmap
                        createEmojiIcon(icon)
                    } else {
                        // 使用 APP 图标
                        android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher)
                    }

                    val shortcut = android.content.pm.ShortcutInfo.Builder(context, id)
                        .setShortLabel(label)
                        .setLongLabel(label)
                        .setIcon(shortcutIcon)
                        .setIntent(intent)
                        .build()

                    shortcuts.add(shortcut)
                }

                shortcutManager?.dynamicShortcuts = shortcuts
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /**
         * 将 Emoji 转换为图标
         */
        private fun createEmojiIcon(emoji: String): android.graphics.drawable.Icon {
            val size = 108 // 自适应图标尺寸
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            // 绘制圆形背景
            val bgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#F0F0F0")
                isAntiAlias = true
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
            
            // 绘制 Emoji
            val paint = android.graphics.Paint().apply {
                textSize = size * 0.5f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(emoji, size / 2f, y, paint)

            return android.graphics.drawable.Icon.createWithBitmap(bitmap)
        }

        /**
         * 复制文字到剪贴板
         * @param text 要复制的文字
         */
        @android.webkit.JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Web2APK", text)
            clipboard.setPrimaryClip(clip)
        }

        /**
         * 读取剪贴板内容
         * @return 剪贴板文字，无内容返回空字符串
         */
        @android.webkit.JavascriptInterface
        fun readClipboard(): String {
            return try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                    clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * 检查设备是否支持生物识别
         * @return 0=支持, 其他=不支持的错误码
         */
        @android.webkit.JavascriptInterface
        fun canAuthenticate(): Int {
            val biometricManager = androidx.biometric.BiometricManager.from(context)
            return biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }

        /**
         * 发起生物识别验证
         * @param title 标题
         * @param subtitle 副标题（可选）
         * @param negativeButtonText 取消按钮文字
         * 结果通过 onAuthSuccess() 或 onAuthError(code, message) 回调
         */
        @android.webkit.JavascriptInterface
        fun authenticate(title: String, subtitle: String?, negativeButtonText: String?) {
            this@MainActivity.runOnUiThread {
                try {
                    val executor = ContextCompat.getMainExecutor(this@MainActivity)
                    
                    val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            webView.evaluateJavascript("if(typeof onAuthSuccess==='function'){onAuthSuccess()}", null)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            val safeMessage = errString.toString().replace("'", "\\'")
                            webView.evaluateJavascript("if(typeof onAuthError==='function'){onAuthError($errorCode,'$safeMessage')}", null)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // 单次失败不回调，用户可以重试
                        }
                    }

                    val biometricPrompt = androidx.biometric.BiometricPrompt(
                        this@MainActivity,
                        executor,
                        callback
                    )

                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle ?: "")
                    .setNegativeButtonText(negativeButtonText ?: "取消")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build()

                    biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    e.printStackTrace()
                    webView.evaluateJavascript("if(typeof onAuthError==='function'){onAuthError(-1,'${e.message?.replace("'", "\\'") ?: "未知错误"}')}", null)
                }
            }
        }
    }
}
