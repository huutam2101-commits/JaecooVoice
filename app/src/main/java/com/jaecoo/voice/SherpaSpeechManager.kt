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
    @Synchronized
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
                    encoder = "zipformer-vi/encoder.int8.onnx",
                    decoder = "zipformer-vi/decoder.onnx",
                    joiner  = "zipformer-vi/joiner.int8.onnx"
                ),
                tokens = "zipformer-vi/tokens.txt",
                numThreads = 2,
                modelType = "transducer"
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

        Log.d(TAG, "=== startListening() BEGIN ===")
        isListening = true

        // Chạy setup + loop trên 1 background thread duy nhất
        val t = Thread {
            android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
            )
            Log.d(TAG, "Worker thread started")

            try {
                var rec = recognizer
                if (rec == null) {
                    Log.d(TAG, "Recognizer null in startListening, initializing model on worker thread...")
                    initModel()
                    rec = recognizer
                }

                if (rec == null) {
                    Log.e(TAG, "Recognizer null sau khi initModel, không thể start")
                    isListening = false
                    listener?.onError("Model chưa sẵn sàng")
                    return@Thread
                }

                // 1. Get min buffer
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                Log.d(TAG, "minBufferSize = $minBuffer bytes")

                if (minBuffer <= 0) {
                    Log.e(TAG, "getMinBufferSize failed: $minBuffer")
                    isListening = false
                    listener?.onError("Không lấy được buffer size")
                    return@Thread
                }

                val bufferSize = (minBuffer * 4).coerceAtLeast(8192)
                Log.d(TAG, "Using bufferSize = $bufferSize bytes")

                // 2. Release old
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null

                // 3. Create AudioRecord
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                val arState = audioRecord?.state
                Log.d(TAG, "AudioRecord.state = $arState (1=OK)")

                if (arState != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord init failed, state=$arState")
                    isListening = false
                    listener?.onError("Không khởi tạo được mic")
                    return@Thread
                }

                // 4. Start recording
                audioRecord?.startRecording()
                Log.d(TAG, "startRecording() called, recordingState=${audioRecord?.recordingState}")

                // 5. Verify recording
                Thread.sleep(100)
                val recState = audioRecord?.recordingState
                Log.d(TAG, "After 100ms, recordingState = $recState (3=RECORDING)")

                if (recState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "AudioRecord không recording, state=$recState")
                    isListening = false
                    listener?.onError("Mic không bắt đầu")
                    return@Thread
                }

                // 6. Chạy recognition loop
                runRecognitionLoop(rec, 1600)

            } catch (e: Throwable) {
                Log.e(TAG, "Worker thread crashed", e)
                isListening = false
                listener?.onError("Lỗi worker: ${e.message}")
            } finally {
                isListening = false
                Log.d(TAG, "=== Worker thread END ===")
            }
        }
        t.name = "SherpaRecordingThread"
        recordingThread = t
        t.start()

        Log.d(TAG, "Thread started, name=SherpaRecordingThread")
    }

    private fun runRecognitionLoop(rec: OfflineRecognizer, chunkSamples: Int) {
        Log.d(TAG, "=== runRecognitionLoop BEGIN, chunk=$chunkSamples ===")

        val buffer = ShortArray(chunkSamples)
        var currentStream = rec.createStream()
        var lastPartialText = ""
        var lastSpeechTime = System.currentTimeMillis()
        var hasDetectedSpeech = false
        var loopCount = 0
        var silentFrames = 0

        while (isListening) {
            loopCount++

            val ar = audioRecord
            if (ar == null) {
                Log.e(TAG, "audioRecord null, break loop")
                break
            }

            val readSize = ar.read(buffer, 0, chunkSamples)

            // Log mỗi 100 loops (10 giây) hoặc khi readSize bất thường
            if (loopCount % 100 == 1 || readSize <= 0) {
                Log.d(TAG, "Loop #$loopCount readSize=$readSize")
            }

            if (readSize <= 0) {
                silentFrames++
                try { Thread.sleep(20) } catch (_: Exception) {}
                continue
            }
            silentFrames = 0

            // Convert to float
            val samples = FloatArray(readSize) { i -> buffer[i] / 32768.0f }

            // RMS
            var sum = 0.0
            for (s in samples) sum += s.toDouble() * s
            val rms = Math.sqrt(sum / samples.size)

            if (rms > 0.005) {
                Log.v(TAG, "RMS=$rms readSize=$readSize")
            }

            // Feed vào model
            try {
                currentStream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(currentStream)
                val text = rec.getResult(currentStream).text

                if (text.isNotBlank() && text != lastPartialText) {
                    lastPartialText = text
                    lastSpeechTime = System.currentTimeMillis()
                    hasDetectedSpeech = true
                    Log.d(TAG, "Partial: '$text'")
                    listener?.onPartialResult(text)
                }

                // Silence -> final
                if (hasDetectedSpeech &&
                    System.currentTimeMillis() - lastSpeechTime > SILENCE_THRESHOLD_MS) {
                    Log.d(TAG, "Final: '$lastPartialText'")
                    listener?.onFinalResult(lastPartialText)
                    currentStream.release()
                    currentStream = rec.createStream()
                    lastPartialText = ""
                    hasDetectedSpeech = false
                    lastSpeechTime = System.currentTimeMillis()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Loop decode error: ${e.message}", e)
            }
        }

        Log.d(TAG, "=== runRecognitionLoop END, total loops=$loopCount ===")
        try { currentStream.release() } catch (_: Exception) {}
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
