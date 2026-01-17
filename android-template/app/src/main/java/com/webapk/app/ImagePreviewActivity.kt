package com.webapk.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 图片预览 Activity
 * 支持：手势缩放、滑动切换、长按保存/分享
 */
class ImagePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGES = "extra_images"
        const val EXTRA_POSITION = "extra_position"
        private const val REQUEST_WRITE_STORAGE = 1001
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tvIndicator: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var progressBar: ProgressBar

    private var imageUrls: ArrayList<String> = arrayListOf()
    private var currentPosition = 0
    private var pendingSaveUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 全屏显示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 设置布局
        setupLayout()
        
        // 获取数据
        imageUrls = intent.getStringArrayListExtra(EXTRA_IMAGES) ?: arrayListOf()
        currentPosition = intent.getIntExtra(EXTRA_POSITION, 0)
        
        if (imageUrls.isEmpty()) {
            Toast.makeText(this, "没有图片可预览", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 设置 ViewPager
        setupViewPager()
        
        // 设置关闭按钮
        btnClose.setOnClickListener {
            finishWithAnimation()
        }
    }

    private fun setupLayout() {
        // 动态创建布局（避免 XML）
        val rootLayout = android.widget.FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }

        // ViewPager2
        viewPager = ViewPager2(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(viewPager)

        // 进度条
        progressBar = ProgressBar(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                100, 100
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            visibility = View.GONE
        }
        rootLayout.addView(progressBar)

        // 指示器
        tvIndicator = TextView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 80
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }
        rootLayout.addView(tvIndicator)

        // 关闭按钮
        btnClose = ImageButton(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                120, 120
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = 40
                marginEnd = 20
            }
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        rootLayout.addView(btnClose)

        setContentView(rootLayout)
    }

    private fun setupViewPager() {
        viewPager.adapter = ImagePagerAdapter(imageUrls)
        viewPager.setCurrentItem(currentPosition, false)
        updateIndicator()
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateIndicator()
            }
        })
    }

    private fun updateIndicator() {
        if (imageUrls.size > 1) {
            tvIndicator.text = "${currentPosition + 1} / ${imageUrls.size}"
            tvIndicator.visibility = View.VISIBLE
        } else {
            tvIndicator.visibility = View.GONE
        }
    }

    private fun finishWithAnimation() {
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finishWithAnimation()
    }

    // 图片适配器
    inner class ImagePagerAdapter(private val urls: List<String>) : 
        RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val photoView = PhotoView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            return ImageViewHolder(photoView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val url = urls[position]
            
            // 加载图片
            progressBar.visibility = View.VISIBLE
            Glide.with(this@ImagePreviewActivity)
                .load(url)
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        holder.photoView.setImageDrawable(resource)
                        progressBar.visibility = View.GONE
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ImagePreviewActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                    }
                })

            // 单击退出
            holder.photoView.setOnClickListener {
                finishWithAnimation()
            }

            // 长按菜单
            holder.photoView.setOnLongClickListener {
                showOptionsMenu(url)
                true
            }
        }

        override fun getItemCount() = urls.size
    }

    // 长按菜单
    private fun showOptionsMenu(imageUrl: String) {
        val options = arrayOf("保存到相册", "分享图片", "取消")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveImage(imageUrl)
                    1 -> shareImage(imageUrl)
                }
            }
            .show()
    }

    // 保存图片
    private fun saveImage(imageUrl: String) {
        // Android 10+ 不需要存储权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                pendingSaveUrl = imageUrl
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE
                )
                return
            }
        }
        
        doSaveImage(imageUrl)
    }

    private fun doSaveImage(imageUrl: String) {
        progressBar.visibility = View.VISIBLE
        
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    try {
                        val fileName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10+ 使用 MediaStore
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                            }
                            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            uri?.let {
                                contentResolver.openOutputStream(it)?.use { os ->
                                    resource.compress(Bitmap.CompressFormat.JPEG, 100, os)
                                }
                            }
                        } else {
                            // Android 9 及以下
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            if (!picturesDir.exists()) picturesDir.mkdirs()
                            val file = File(picturesDir, fileName)
                            FileOutputStream(file).use { os ->
                                resource.compress(Bitmap.CompressFormat.JPEG, 100, os)
                            }
                            // 通知相册
                            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
                        }
                        
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ImagePreviewActivity, "已保存到相册", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ImagePreviewActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ImagePreviewActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // 分享图片
    private fun shareImage(imageUrl: String) {
        progressBar.visibility = View.VISIBLE
        
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    try {
                        // 保存到缓存目录
                        val shareCacheDir = File(this@ImagePreviewActivity.cacheDir, "shared_images")
                        if (!shareCacheDir.exists()) shareCacheDir.mkdirs()
                        val file = File(shareCacheDir, "share_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { os ->
                            resource.compress(Bitmap.CompressFormat.JPEG, 100, os)
                        }
                        
                        // 获取 URI
                        val uri = FileProvider.getUriForFile(
                            this@ImagePreviewActivity,
                            "${this@ImagePreviewActivity.packageName}.fileprovider",
                            file
                        )
                        
                        // 分享
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        progressBar.visibility = View.GONE
                        startActivity(Intent.createChooser(shareIntent, "分享图片"))
                    } catch (e: Exception) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ImagePreviewActivity, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ImagePreviewActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingSaveUrl?.let { doSaveImage(it) }
            pendingSaveUrl = null
        }
    }
}
