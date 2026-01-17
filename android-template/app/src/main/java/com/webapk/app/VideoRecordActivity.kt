package com.webapk.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 视频录制 Activity
 * 支持前后摄像头、时长限制、质量设置
 */
class VideoRecordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MAX_DURATION = "max_duration"  // 最大录制时长（秒），0=不限制
        const val EXTRA_QUALITY = "quality"            // 视频质量: low/medium/high
        const val EXTRA_CAMERA_FACING = "camera_facing" // front/back
        const val RESULT_VIDEO_PATH = "video_path"
        const val RESULT_DURATION = "duration"
        private const val PERMISSION_REQUEST_CODE = 200
    }

    private lateinit var textureView: TextureView
    private lateinit var btnRecord: ImageButton
    private lateinit var btnSwitch: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var chronometer: Chronometer
    private lateinit var timerContainer: View

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private var isRecording = false
    private var isFrontCamera = false
    private var videoFile: File? = null
    private var recordingStartTime: Long = 0

    private var maxDuration: Int = 0  // 秒
    private var quality: String = "medium"

    private val previewSize = Size(1280, 720)
    private val videoSize = Size(1280, 720)

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_record)

        // 解析 Intent 参数
        maxDuration = intent.getIntExtra(EXTRA_MAX_DURATION, 0)
        quality = intent.getStringExtra(EXTRA_QUALITY) ?: "medium"
        isFrontCamera = intent.getStringExtra(EXTRA_CAMERA_FACING) == "front"

        initViews()
        checkPermissions()
    }

    private fun initViews() {
        textureView = findViewById(R.id.texture_view)
        btnRecord = findViewById(R.id.btn_record)
        btnSwitch = findViewById(R.id.btn_switch)
        btnClose = findViewById(R.id.btn_close)
        chronometer = findViewById(R.id.chronometer)
        timerContainer = findViewById(R.id.timerContainer)

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        btnSwitch.setOnClickListener {
            switchCamera()
        }

        btnClose.setOnClickListener {
            if (isRecording) {
                cancelRecording()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startBackgroundThread()
            if (textureView.isAvailable) {
                openCamera()
            } else {
                textureView.surfaceTextureListener = surfaceTextureListener
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startBackgroundThread()
                if (textureView.isAvailable) {
                    openCamera()
                } else {
                    textureView.surfaceTextureListener = surfaceTextureListener
                }
            } else {
                Toast.makeText(this, "需要相机和麦克风权限才能录制视频", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun getCameraId(): String {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val facing = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        for (cameraId in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == facing) {
                return cameraId
            }
        }
        return manager.cameraIdList[0]
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = getCameraId()
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    runOnUiThread {
                        Toast.makeText(this@VideoRecordActivity, "相机打开失败", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "无法访问相机", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(texture)

        try {
            val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        session.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(this@VideoRecordActivity, "预览配置失败", Toast.LENGTH_SHORT).show()
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setupMediaRecorder() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoDir = File(cacheDir, "videos")
        if (!videoDir.exists()) videoDir.mkdirs()
        videoFile = File(videoDir, "VID_$timeStamp.mp4")

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoFile!!.absolutePath)

            // 根据质量设置参数
            when (quality) {
                "low" -> {
                    setVideoEncodingBitRate(1_000_000)
                    setVideoFrameRate(24)
                    setVideoSize(640, 480)
                }
                "high" -> {
                    setVideoEncodingBitRate(10_000_000)
                    setVideoFrameRate(30)
                    setVideoSize(1920, 1080)
                }
                else -> { // medium
                    setVideoEncodingBitRate(5_000_000)
                    setVideoFrameRate(30)
                    setVideoSize(videoSize.width, videoSize.height)
                }
            }

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            // 设置旋转角度
            val rotation = windowManager.defaultDisplay.rotation
            val orientation = when (rotation) {
                Surface.ROTATION_0 -> if (isFrontCamera) 270 else 90
                Surface.ROTATION_90 -> 0
                Surface.ROTATION_180 -> if (isFrontCamera) 90 else 270
                Surface.ROTATION_270 -> 180
                else -> if (isFrontCamera) 270 else 90
            }
            setOrientationHint(orientation)

            if (maxDuration > 0) {
                setMaxDuration(maxDuration * 1000)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        runOnUiThread { stopRecording() }
                    }
                }
            }

            prepare()
        }
    }

    private fun startRecording() {
        try {
            closePreviewSession()
            setupMediaRecorder()

            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder!!.surface

            val recordRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            recordRequestBuilder?.addTarget(previewSurface)
            recordRequestBuilder?.addTarget(recorderSurface)

            cameraDevice?.createCaptureSession(
                listOf(previewSurface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        recordRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        session.setRepeatingRequest(recordRequestBuilder!!.build(), null, backgroundHandler)

                        runOnUiThread {
                            mediaRecorder?.start()
                            isRecording = true
                            recordingStartTime = System.currentTimeMillis()
                            // 切换按钮样式：红色圆形 → 白框+红色方块
                            btnRecord.setImageDrawable(null)
                            btnRecord.setBackgroundResource(R.drawable.bg_record_button_recording)
                            btnSwitch.visibility = View.INVISIBLE
                            // 显示计时器
                            chronometer.base = SystemClock.elapsedRealtime()
                            chronometer.start()
                            timerContainer.visibility = View.VISIBLE
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(this@VideoRecordActivity, "录制配置失败", Toast.LENGTH_SHORT).show()
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "录制启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanupRecording()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            isRecording = false
            chronometer.stop()

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val duration = System.currentTimeMillis() - recordingStartTime

            // 返回结果
            val resultIntent = Intent().apply {
                putExtra(RESULT_VIDEO_PATH, videoFile?.absolutePath)
                putExtra(RESULT_DURATION, duration)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "停止录制失败", Toast.LENGTH_SHORT).show()
            cleanupRecording()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun cancelRecording() {
        if (!isRecording) return

        try {
            isRecording = false
            chronometer.stop()

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            // 删除临时文件
            videoFile?.delete()
            videoFile = null

            setResult(RESULT_CANCELED)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            cleanupRecording()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun cleanupRecording() {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        videoFile?.delete()
        videoFile = null
    }

    private fun closePreviewSession() {
        captureSession?.close()
        captureSession = null
    }

    private fun switchCamera() {
        if (isRecording) return

        closeCamera()
        isFrontCamera = !isFrontCamera
        openCamera()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        if (isRecording) {
            cancelRecording()
        }
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onDestroy() {
        cleanupRecording()
        super.onDestroy()
    }
}
