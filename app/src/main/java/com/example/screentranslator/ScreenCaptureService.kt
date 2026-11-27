package com.example.screentranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var windowManager: WindowManager? = null
    private var floatingButton: Button? = null

    private val translationViews = mutableListOf<View>()

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
            3
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = 20
            y = 0
        }

        val button = Button(this).apply {
            text = "번역"
            textSize = 10.5f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45, 110, 200))

            scaleX = 0.7f
            scaleY = 0.7f
            alpha = 0.45f

            setOnClickListener {
                captureAndTranslate()
            }
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        val touchSlop =
            android.view.ViewConfiguration.get(this).scaledTouchSlop

        button.setOnTouchListener { view, event ->

            when (event.action) {

                android.view.MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY

                    startX = params.x
                    startY = params.y

                    dragging = false
                    view.alpha = 1.0f
                    true
                }

                android.view.MotionEvent.ACTION_MOVE -> {

                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY

                    if (!dragging &&
                        (kotlin.math.abs(dx) > touchSlop ||
                         kotlin.math.abs(dy) > touchSlop)
                    ) {
                        dragging = true
                    }

                    if (dragging) {
                        params.x = (startX - dx.toInt()).coerceIn(
                            0,
                            resources.displayMetrics.widthPixels - view.width
                        )

                        params.y = (startY + dy.toInt()).coerceIn(
                            -resources.displayMetrics.heightPixels / 2,
                            resources.displayMetrics.heightPixels / 2
                        )

                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }

                    true
                }

                android.view.MotionEvent.ACTION_UP -> {
                    view.alpha = 0.45f

                    if (!dragging) {
                        view.performClick()
                    }

                    true
                }

                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 0.45f
                    true
                }

                else -> true
            }
        }

        try {
            windowManager?.addView(button, params)
            floatingButton = button
        } catch (e: Exception) {
            showMessage("버튼 표시 실패: ${e.message}")
        }
    }

    private fun captureAndTranslate() {

        val image = try {
            imageReader?.acquireLatestImage()
        } catch (e: Exception) {
            null
        }

        if (image == null) {
            showMessage("화면을 캡처할 수 없습니다. 잠시 후 다시 눌러주세요.")
            return
        }

        val bitmap = try {
            imageToBitmap(image)
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }

        if (bitmap == null) {
            showMessage("화면 이미지 변환 실패")
            return
        }

        clearTranslationOverlays()

        showMessage("문자를 인식하고 있습니다.")

        recognizeText(bitmap)
    }

    private fun imageToBitmap(image: Image): Bitmap {

        val plane = image.planes[0]
        val buffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val rowPadding =
            rowStride - pixelStride * image.width

        val paddedWidth =
            image.width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        buffer.rewind()
        paddedBitmap.copyPixelsFromBuffer(buffer)

        val bitmap = Bitmap.createBitmap(
            paddedBitmap,
            0,
            0,
            image.width,
            image.height
        )

        paddedBitmap.recycle()

        return bitmap
    }

    private fun recognizeText(bitmap: Bitmap) {

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        val japaneseRecognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )

        val englishRecognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        japaneseRecognizer.process(inputImage)
            .addOnCompleteListener { japaneseTask ->

                englishRecognizer.process(inputImage)
                    .addOnCompleteListener { englishTask ->

                        val japaneseResult =
                            if (japaneseTask.isSuccessful)
                                japaneseTask.result
                            else null

                        val englishResult =
                            if (englishTask.isSuccessful)
                                englishTask.result
                            else null

                        val isJapanese =
                            japaneseResult != null &&
                            containsJapanese(japaneseResult.text)

                        val selectedResult: Text? =
                            if (isJapanese) japaneseResult
                            else englishResult

                        val sourceLanguage =
                            if (isJapanese)
                                TranslateLanguage.JAPANESE
                            else
                                TranslateLanguage.ENGLISH

                        japaneseRecognizer.close()
                        englishRecognizer.close()
                        bitmap.recycle()

                        if (selectedResult == null ||
                            selectedResult.textBlocks.isEmpty()
                        ) {
                            showMessage("인식된 문자가 없습니다.")
                            return@addOnCompleteListener
                        }

                        translateText(
                            selectedResult,
                            sourceLanguage
                        )
                    }
            }
    }

    private fun containsJapanese(text: String): Boolean {
        return text.any {
            it in '\u3040'..'\u30FF' ||
            it in '\u3400'..'\u9FFF'
        }
    }

    private fun translateText(
        result: Text,
        sourceLanguage: String
    ) {

        val targets = result.textBlocks.mapNotNull { block ->
            block.boundingBox?.let {
                Pair(it, block.text)
            }
        }

        if (targets.isEmpty()) {
            showMessage("번역할 문자가 없습니다.")
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(TranslateLanguage.KOREAN)
            .build()

        val translator = Translation.getClient(options)

        showMessage("번역 모델을 준비하고 있습니다.")

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {

                var remaining = targets.size

                for ((box, originalText) in targets) {

                    translator.translate(originalText)
                        .addOnSuccessListener { translatedText ->

                            addTranslationOverlay(
                                box,
                                translatedText
                            )
                        }
                        .addOnFailureListener {
                            showMessage("일부 문장 번역에 실패했습니다.")
                        }
                        .addOnCompleteListener {

                            remaining--

                            if (remaining == 0) {
                                translator.close()
                                showMessage("번역 완료")
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                translator.close()
                showMessage(
                    "번역 모델 다운로드 실패: ${error.message}"
                )
            }
    }

    private fun addTranslationOverlay(
        box: Rect,
        translatedText: String
    ) {
        val screenWidth = resources.displayMetrics.widthPixels

        val statusBarId = resources.getIdentifier(
            "status_bar_height", "dimen", "android"
        )
        val statusBarHeight = if (statusBarId > 0) {
            resources.getDimensionPixelSize(statusBarId)
        } else {
            0
        }

        val left = box.left.coerceIn(0, screenWidth - 1)
        val top = (box.top - statusBarHeight).coerceAtLeast(0)

        val overlayWidth = box.width()
            .coerceAtLeast(120)
            .coerceAtMost(screenWidth - left)

        val textView = TextView(this).apply {
            text = translatedText
            textSize = 16f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
            )
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(6, 4, 6, 4)
            setBackgroundColor(Color.TRANSPARENT)
            minHeight = box.height().coerceAtLeast(36)
        }

        val container = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            background.alpha = 255
            alpha = 1.0f

            addView(
                textView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val params = WindowManager.LayoutParams(
            overlayWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = left
            y = top
            alpha = 1.0f
        }

        try {
            windowManager?.addView(container, params)
            translationViews.add(container)
        } catch (e: Exception) {
            showMessage("번역문 표시 실패: ${e.message}")
        }
    }

    private fun clearTranslationOverlays() {

        for (view in translationViews) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }

        translationViews.clear()
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
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

        clearTranslationOverlays()

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
