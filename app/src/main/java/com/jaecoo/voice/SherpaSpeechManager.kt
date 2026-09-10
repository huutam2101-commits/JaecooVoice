package com.jaecoo.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig

/**
 * Singleton quản lý khởi tạo và xử lý nhận diện giọng nói offline với sherpa-onnx Zipformer-vi
 */
class SherpaSpeechManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SherpaSpeech"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_THRESHOLD_MS = 1500L  // 1.5s im lặng → final result

        @Volatile
        private var instance: SherpaSpeechManager? = null

        fun getInstance(context: Context): SherpaSpeechManager {
            return instance ?: synchronized(this) {
                instance ?: SherpaSpeechManager(context.applicationContext).also { instance = it }
            }
        }
    }

    interface RecognitionListener {
        fun onReady()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(error: String)
    }

    private var recognizer: OfflineRecognizer? = null
    private var listener: RecognitionListener? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile private var isListening = false
    @Volatile private var modelReady = false

    fun setListener(l: RecognitionListener) {
        listener = l
    }

    fun isModelReady(): Boolean = modelReady

    /**
     * Khởi tạo model sherpa-onnx.
     */
    fun initModel(): Boolean {
        if (modelReady && recognizer != null) {
            Log.d(TAG, "Model đã khởi tạo trước đó, bỏ qua reload")
            listener?.onReady()
            return true
        }

        return try {
            Log.d(TAG, "Initializing sherpa-onnx Zipformer-vi...")

            val modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    // ĐƯỜNG DẪN TƯƠNG ĐỐI — không có /data/, không có filesDir
                    encoder = "zipformer-vi/encoder.int8.onnx",
                    decoder = "zipformer-vi/decoder.onnx",
                    joiner  = "zipformer-vi/joiner.int8.onnx"
                ),
                tokens = "zipformer-vi/tokens.txt",
                numThreads = 2,
                modelType = "zipformer"
            )

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig
            )

            recognizer = OfflineRecognizer(assetManager = context.assets, config = config)
            modelReady = true

            Log.d(TAG, "Model init OK")
            listener?.onReady()
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Init failed: ${e.message}", e)
            modelReady = false
            listener?.onError("Không tải được model: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Đã listening rồi, bỏ qua")
            return
        }

        val rec = recognizer ?: run {
            Log.e(TAG, "Recognizer null")
            return
        }

        try {
            isListening = true

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE) * 2

            // Release cái cũ nếu còn
            audioRecord?.let {
                try { it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                isListening = false
                return
            }

            audioRecord?.startRecording()
            Log.d(TAG, "Bắt đầu ghi âm")

            recordingThread = Thread {
                runRecognitionLoop(rec, bufferSize)
            }.also { it.start() }

        } catch (e: Throwable) {
            Log.e(TAG, "startListening failed", e)
            isListening = false
        }
    }

    private fun runRecognitionLoop(rec: OfflineRecognizer, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        var currentStream = rec.createStream()
        var lastPartialText = ""
        var lastSpeechTime = System.currentTimeMillis()
        var hasDetectedSpeech = false

        while (isListening) {
            val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readSize <= 0) {
                try {
                    Thread.sleep(10)
                } catch (_: Exception) {}
                continue
            }

            // Chuyển ShortArray → FloatArray (normalize -1..1)
            val samples = FloatArray(readSize) { i ->
                buffer[i] / 32768.0f
            }

            currentStream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(currentStream)
            val text = rec.getResult(currentStream).text

            // Nếu nhận thêm từ mới -> reset timer im lặng
            if (text.isNotBlank() && text != lastPartialText) {
                lastPartialText = text
                lastSpeechTime = System.currentTimeMillis()
                hasDetectedSpeech = true
                Log.d(TAG, "Partial: $text")
                listener?.onPartialResult(text)
            }

            // Nếu đã có âm thanh và im lặng đủ SILENCE_THRESHOLD_MS (1.5s) -> FINAL
            if (hasDetectedSpeech && System.currentTimeMillis() - lastSpeechTime > SILENCE_THRESHOLD_MS) {
                Log.d(TAG, "Final (silence detected): $lastPartialText")
                listener?.onFinalResult(lastPartialText)

                // Reset stream và state để nghe câu mới (KHÔNG tắt mic, KHÔNG stop)
                currentStream.release()
                currentStream = rec.createStream()
                lastPartialText = ""
                hasDetectedSpeech = false
                lastSpeechTime = System.currentTimeMillis()
            }
        }

        currentStream.release()
    }

    fun stopListening() {
        stopListeningInternal()
    }

    private fun stopListeningInternal() {
        isListening = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        Log.d(TAG, "Đã dừng ghi âm")
    }

    fun destroy() {
        Log.d(TAG, "Destroy SherpaSpeechManager")
        stopListening()
        recordingThread?.interrupt()
        recordingThread = null
        recognizer?.release()
        recognizer = null
        modelReady = false
    }
}
