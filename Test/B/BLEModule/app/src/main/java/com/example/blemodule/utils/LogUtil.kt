package com.example.blemodule.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {
    private const val TAG = "BleMeshApp"
    private var logTextView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setLogTextView(textView: TextView) {
        logTextView = textView
    }

    fun log(message: String) {
        // LogCat에 출력
        Log.d(TAG, message)

        // TextView에 출력 (UI 스레드에서)
        mainHandler.post {
            logTextView?.let { tv ->
                val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                    .format(Date())

                tv.append("\n[$timestamp] $message")

                // 자동 스크롤
                val scrollAmount = tv.layout?.getLineTop(tv.lineCount) ?: 0
                if (scrollAmount > tv.height) {
                    tv.scrollTo(0, scrollAmount - tv.height)
                }
            }
        }
    }
}