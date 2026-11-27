package com.example.screentranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var projectionManager: MediaProjectionManager

    companion object {
        private const val REQUEST_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Screen Translator"
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "번역 준비 완료"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "번역 시작"
            setOnClickListener {
                statusText.text = "화면 캡처 권한을 요청합니다."
                startActivityForResult(
                    projectionManager.createScreenCaptureIntent(),
                    REQUEST_CAPTURE
                )
            }
        }

        val stopButton = Button(this).apply {
            text = "번역 중지"
            setOnClickListener {
                stopService(
                    Intent(this@MainActivity, ScreenCaptureService::class.java)
                )
                statusText.text = "화면 캡처 중지"
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {

                val serviceIntent = Intent(
                    this,
                    ScreenCaptureService::class.java
                ).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                statusText.text = "화면 캡처 실행 중"

            } else {
                statusText.text = "화면 캡처 권한이 거부되었습니다."
            }
        }
    }
}
