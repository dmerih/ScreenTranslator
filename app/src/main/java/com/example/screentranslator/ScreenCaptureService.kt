package com.example.screentranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null
    private var floatingButton: Button? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra("resultCode", 0)

        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>("data")
        }

        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotification()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                1,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification())
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

        mediaProjection = manager.getMediaProjection(
            resultCode,
            resultData
        )

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseCapture()
                    stopSelf()
                }
            },
            null
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenTranslator",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        showFloatingButton()

        return START_NOT_STICKY
    }

    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE)
                as WindowManager

        val button = Button(this).apply {
            text = "번역"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45, 110, 200))

            setOnClickListener {
                Toast.makeText(
                    this@ScreenCaptureService,
                    "번역 기능을 연결하는 중입니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = 20
            y = 0
        }

        try {
            windowManager?.addView(button, params)
            floatingButton = button
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "번역 버튼 표시 실패: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "screen_capture",
                "화면 번역",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        return Notification.Builder(this, "screen_capture")
            .setContentTitle("Screen Translator")
            .setContentText("화면 캡처가 실행 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun releaseCapture() {
        floatingButton?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }

        floatingButton = null
        windowManager = null

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
