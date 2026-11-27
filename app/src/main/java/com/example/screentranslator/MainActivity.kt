package com.example.screentranslator

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                statusText.text = "화면 번역 기능을 준비하고 있습니다."
            }
        }

        val stopButton = Button(this).apply {
            text = "번역 중지"
            setOnClickListener {
                statusText.text = "번역 중지"
            }
        }

        layout.addView(title)
        layout.addView(statusText)

        layout.addView(startButton)
       

cat > app/src/main/java/com/example/screentranslator/MainActivity.kt <<'EOF'
package com.example.screentranslator

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                statusText.text = "화면 번역 기능을 준비하고 있습니다."
            }
        }

        val stopButton = Button(this).apply {
            text = "번역 중지"
            setOnClickListener {
                statusText.text = "번역 중지"
            }
        }

        layout.addView(title)
        layout.addView(statusText)

        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)
    }
}
