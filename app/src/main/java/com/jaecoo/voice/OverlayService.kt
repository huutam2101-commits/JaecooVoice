package com.jaecoo.voice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Foreground Service quản lý cửa sổ nổi Overlay trợ lý giọng nói liên tục kiểu Gemini Live
 */
class OverlayService : Service(), SherpaSpeechManager.RecognitionListener, TextToSpeech.OnInitListener {

    enum class State {
        LISTENING,      // Đang nghe, waveform chạy
        PROCESSING,     // Đang xử lý lệnh
        SHOWING_RESULT, // Hiện kết quả + TTS
        WAITING_NEXT,   // Chờ lệnh tiếp
        CLOSING         // Đang đóng
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jaecoo_overlay_channel"
        private const val TIMEOUT_MS = 10000L

        @Volatile
        private var instance: OverlayService? = null

        fun getInstance(): OverlayService? = instance

        fun showOverlay(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hideOverlay() {
            instance?.stopSelf()
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var txtHeard: TextView? = null
    private var txtReply: TextView? = null
    private var waveView: WaveView? = null
    private var btnClose: ImageView? = null

    private lateinit var speechManager: SherpaSpeechManager
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = State.LISTENING

    @Volatile
    private var isSpeaking = false

    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Timeout 10s — không nghe gì -> đóng overlay")
        dismissAndExit()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate OverlayService")
        instance = this

        startForegroundNotification()
        setupOverlayWindow()

        // Khởi tạo TTS tiếng Việt
        tts = TextToSpeech(this, this)

        // Dùng Singleton SherpaSpeechManager
        speechManager = SherpaSpeechManager.getInstance(this)
        speechManager.setListener(this)

        Thread {
            speechManager.initModel()
        }.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Tiếng Việt chưa cài đặt trên TTS engine, fallback dùng Locale mặc định")
                tts?.language = Locale.getDefault()
            }
            ttsReady = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS init failed with status $status")
        }
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jaecoo Voice Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jaecoo Assistant")
            .setContentText("Trợ lý giọng nói Gemini Live đang hoạt động")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_assistant, null)

        txtHeard = overlayView?.findViewById(R.id.txtHeard)
        txtReply = overlayView?.findViewById(R.id.txtReply)
        waveView = overlayView?.findViewById(R.id.waveView)
        btnClose = overlayView?.findViewById(R.id.btnClose)

        btnClose?.setOnClickListener {
            Log.d(TAG, "User pressed Close button X")
            dismissAndExit()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }
        windowParams = params

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY - (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        overlayView?.let {
            windowManager?.addView(it, params)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        speechManager.setListener(this)

        transitionTo(State.LISTENING)

        return START_NOT_STICKY
    }

    override fun onReady() {
        Log.d(TAG, "onReady")
        mainHandler.post {
            if (state == State.LISTENING) {
                transitionTo(State.LISTENING)
            }
        }
    }

    override fun onPartialResult(text: String) {
        if (isSpeaking) {
            Log.d(TAG, "Bỏ qua partial (đang TTS): $text")
            return
        }
        Log.d(TAG, "onPartialResult: '$text'")
        mainHandler.post {
            if (state == State.LISTENING) {
                updateUi(heard = text, reply = "Đang nhận...")
                resetTimeout()
            }
        }
    }

    override fun onFinalResult(text: String) {
        if (isSpeaking) {
            Log.d(TAG, "Bỏ qua final (đang TTS): $text")
            return
        }

        Log.d(TAG, "onFinalResult: '$text'")

        if (text.isBlank()) {
            mainHandler.post { transitionTo(State.LISTENING) }
            return
        }

        mainHandler.post {
            transitionTo(State.PROCESSING)
            updateUi(heard = text, reply = "Đang xử lý...")

            val response = try {
                CommandExecutor.executeCommand(this@OverlayService, text)
            } catch (e: Exception) {
                Log.e(TAG, "CommandExecutor error", e)
                "Lỗi xử lý lệnh"
            }

            Log.d(TAG, "Response: $response")

            transitionTo(State.SHOWING_RESULT)
            updateUi(heard = text, reply = response)

            speakResponse(response)
        }
    }

    private fun speakResponse(response: String) {
        Log.d(TAG, "speakResponse: '$response'")
        isSpeaking = true

        // TẮT MIC trước khi TTS đọc
        speechManager.stopListening()
        Log.d(TAG, "Mic stopped, chuẩn bị TTS")

        // Chờ 200ms để mic tắt hẳn trước khi loa phát
        mainHandler.postDelayed({
            if (tts == null) {
                Log.w(TAG, "TTS null -> fallback")
                onTtsFinished()
                return@postDelayed
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS bắt đầu đọc")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS đọc xong")
                    mainHandler.post { onTtsFinished() }
                }

                @Suppress("DEPRECATION")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS lỗi")
                    mainHandler.post { onTtsFinished() }
                }
            })

            val utteranceId = "jv_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = tts?.speak(response, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS speak fail -> fallback delay")
                mainHandler.postDelayed({ onTtsFinished() }, 2000L)
            }
        }, 200L)

        // Safety fallback: nếu TTS không callback trong 5s -> force finish
        mainHandler.postDelayed({
            if (isSpeaking) {
                Log.w(TAG, "TTS timeout 5s -> force onTtsFinished")
                onTtsFinished()
            }
        }, 5000L)
    }

    private fun onTtsFinished() {
        if (!isSpeaking) return
        Log.d(TAG, "onTtsFinished: chờ 800ms rồi bật mic")

        // Chờ 800ms để echo tail biến mất hoàn toàn
        mainHandler.postDelayed({
            isSpeaking = false
            Log.d(TAG, "Bật mic lại -> nghe tiếp")

            transitionTo(State.LISTENING)
        }, 800L)
    }

    override fun onError(error: String) {
        Log.e(TAG, "Error: $error")
        mainHandler.post {
            updateUi(heard = "Lỗi", reply = error)
            mainHandler.postDelayed({
                if (state != State.CLOSING) {
                    transitionTo(State.LISTENING)
                }
            }, 1500L)
        }
    }

    private fun transitionTo(newState: State) {
        Log.d(TAG, "State: $state -> $newState")
        state = newState

        when (newState) {
            State.LISTENING, State.WAITING_NEXT -> {
                if (isSpeaking) {
                    Log.d(TAG, "Đang TTS, không start mic")
                    return
                }
                updateUi(heard = "Đang nghe...", reply = "Hãy nói câu lệnh")
                waveView?.setListening(true)
                speechManager.startListening()
                resetTimeout()
            }
            State.PROCESSING, State.SHOWING_RESULT -> {
                mainHandler.removeCallbacks(timeoutRunnable)
                waveView?.setListening(false)
            }
            State.CLOSING -> {
                mainHandler.removeCallbacks(timeoutRunnable)
                waveView?.setListening(false)
            }
        }
    }

    private fun updateUi(heard: String, reply: String) {
        txtHeard?.text = heard
        txtReply?.text = reply
    }

    private fun resetTimeout() {
        mainHandler.removeCallbacks(timeoutRunnable)
        if (state == State.LISTENING || state == State.WAITING_NEXT) {
            mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        }
    }

    private fun dismissAndExit() {
        Log.d(TAG, "Closed by timeout/user")
        transitionTo(State.CLOSING)
        speechManager.stopListening()

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "TTS shutdown error: ${e.message}", e)
        }

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "removeView error: ${e.message}", e)
            }
        }
        overlayView = null
        instance = null

        stopSelf()

        mainHandler.postDelayed({
            Process.killProcess(Process.myPid())
        }, 300L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        speechManager.stopListening()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
