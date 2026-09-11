package com.jaecoo.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.util.concurrent.atomic.AtomicBoolean

class SherpaSpeechManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SherpaSpeech"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_THRESHOLD_MS = 1500L

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

    // Dùng AtomicBoolean thay vì @Volatile boolean — an toàn hơn với đa luồng
    private val isRecordingThreadRunning = AtomicBoolean(false)
    @Volatile private var modelReady = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setListener(l: RecognitionListener) {
        listener = l
    }

    fun isModelReady(): Boolean = modelReady

    fun reset() {
        Log.d(TAG, "reset() called: forcing cleanup")
        isRecordingThreadRunning.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { recordingThread?.interrupt() } catch (_: Exception) {}
        recordingThread = null
        Log.d(TAG, "reset() done: isRecordingThreadRunning=${isRecordingThreadRunning.get()}")
    }

    @Synchronized
    fun initModel(): Boolean {
        if (modelReady && recognizer != null) {
            Log.d(TAG, "Model đã khởi tạo trước đó, bỏ qua reload")
            mainHandler.post { listener?.onReady() }
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
            mainHandler.post { listener?.onReady() }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Init failed: ${e.message}", e)
            modelReady = false
            mainHandler.post { listener?.onError("Không tải được model: ${e.message}") }
            false
        }
    }

    /**
     * KHÔNG dùng @Synchronized ở đây vì có Thread.sleep() bên trong.
     * Dùng AtomicBoolean.compareAndSet để đảm bảo atomic.
     */
    @SuppressLint("MissingPermission")
    fun startListening() {
        Log.d(TAG, "startListening() ENTER: isRunning=${isRecordingThreadRunning.get()}")

        // Atomic compareAndSet: chỉ set true nếu đang là false
        if (!isRecordingThreadRunning.compareAndSet(false, true)) {
            Log.w(TAG, "Thread already running, skipping")
            return
        }
        Log.d(TAG, "SET isRecordingThreadRunning = true")

        // Bọc toàn bộ trong try/catch(Throwable) và finally
        try {
            var rec = recognizer
            if (rec == null) {
                Log.d(TAG, "Recognizer null, calling initModel()...")
                initModel()
                rec = recognizer
            }

            if (rec == null) {
                Log.e(TAG, "Recognizer null sau khi initModel")
                listener?.onError("Model chưa sẵn sàng")
                return  // finally sẽ reset
            }

            Log.d(TAG, "PASSED check, creating AudioRecord...")

            // Audio Focus
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            var focusResult = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            Log.d(TAG, "requestAudioFocus result=$focusResult")

            var focusRetries = 0
            while (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED && focusRetries < 3) {
                focusRetries++
                Log.w(TAG, "Retry requestAudioFocus #$focusRetries...")
                Thread.sleep(500)
                @Suppress("DEPRECATION")
                focusResult = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                Log.d(TAG, "Retry requestAudioFocus #$focusRetries result=$focusResult")
            }

            val minBuffer = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            Log.d(TAG, "minBufferSize=$minBuffer")

            val bufferSize = (minBuffer * 4).coerceAtLeast(8192)

            // Cleanup cũ
            try { audioRecord?.stop() } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null

            val sources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )

            var createdRecord: AudioRecord? = null
            for (src in sources) {
                try {
                    val candidate = AudioRecord(
                        src,
                        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        createdRecord = candidate
                        Log.d(TAG, "AudioRecord initialized OK with source=$src")
                        break
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed source $src: ${e.message}")
                }
            }

            audioRecord = createdRecord
            Log.d(TAG, "AudioRecord created: state=${audioRecord?.state}")

            if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                listener?.onError("Không khởi tạo được mic")
                return  // finally sẽ reset
            }

            audioRecord?.startRecording()
            Log.d(TAG, "startRecording() called: recordingState=${audioRecord?.recordingState}")

            Thread.sleep(100)
            var recState = audioRecord?.recordingState
            Log.d(TAG, "After 100ms, recordingState = $recState (3=RECORDING)")

            if (recState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Mic KHÔNG ở trạng thái RECORDING sau 100ms! state=$recState")
                var retries = 0
                while (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING && retries < 3) {
                    retries++
                    Log.w(TAG, "Retry startRecording() #$retries...")
                    Thread.sleep(500)
                    try { audioRecord?.startRecording() } catch (e: Exception) {
                        Log.e(TAG, "Retry startRecording failed", e)
                    }
                }
                recState = audioRecord?.recordingState
                if (recState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "Mic vẫn KHÔNG RECORDING sau $retries retries! state=$recState")
                    listener?.onError("Mic không bắt đầu")
                    return  // finally sẽ reset
                }
            }

            // Tạo thread ghi âm
            val capturedRecognizer = rec  // capture local var
            val thread = Thread {
                Log.d(TAG, "SherpaRecordingThread STARTED")
                var currentStream = capturedRecognizer.createStream()
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    val chunkSamples = 1600
                    val buffer = ShortArray(chunkSamples)
                    var lastPartialText = ""
                    var lastSpeechTime = System.currentTimeMillis()
                    var hasDetectedSpeech = false
                    var loopCount = 0

                    while (isRecordingThreadRunning.get()) {
                        val ar = audioRecord
                        if (ar == null) {
                            Log.e(TAG, "audioRecord null, break loop")
                            break
                        }

                        val readSize = try {
                            ar.read(buffer, 0, chunkSamples)
                        } catch (e: Exception) {
                            Log.e(TAG, "read() error: ${e.message}", e)
                            -1
                        }

                        if (loopCount % 100 == 0) {
                            Log.d(TAG, "readSize=$readSize, loopCount=$loopCount, recState=${ar.recordingState}")
                        }

                        if (readSize > 0) {
                            val samples = FloatArray(readSize) { i -> buffer[i] / 32768.0f }
                            try {
                                currentStream.acceptWaveform(samples, SAMPLE_RATE)
                                capturedRecognizer.decode(currentStream)
                                val text = capturedRecognizer.getResult(currentStream).text

                                if (text.isNotBlank() && text != lastPartialText) {
                                    lastPartialText = text
                                    lastSpeechTime = System.currentTimeMillis()
                                    hasDetectedSpeech = true
                                    Log.d(TAG, "Partial: '$text'")
                                    // Post lên main thread
                                    mainHandler.post { listener?.onPartialResult(text) }
                                }

                                if (hasDetectedSpeech &&
                                    System.currentTimeMillis() - lastSpeechTime > SILENCE_THRESHOLD_MS) {
                                    Log.d(TAG, "Final: '$lastPartialText'")
                                    val finalText = lastPartialText
                                    mainHandler.post { listener?.onFinalResult(finalText) }
                                    try { currentStream.release() } catch (_: Exception) {}
                                    currentStream = capturedRecognizer.createStream()
                                    lastPartialText = ""
                                    hasDetectedSpeech = false
                                    lastSpeechTime = System.currentTimeMillis()
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Loop decode error: ${e.message}", e)
                            }
                        } else {
                            try { Thread.sleep(20) } catch (_: Exception) {}
                        }
                        loopCount++
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Thread error: ${e.message}", e)
                } finally {
                    Log.d(TAG, "SherpaRecordingThread ENDED")
                    try { currentStream.release() } catch (_: Exception) {}
                    isRecordingThreadRunning.set(false)
                    Log.d(TAG, "SET isRecordingThreadRunning = false (thread finally)")
                    try { audioRecord?.stop() } catch (_: Exception) {}
                    try { audioRecord?.release() } catch (_: Exception) {}
                    audioRecord = null
                }
            }
            thread.name = "SherpaRecordingThread"
            recordingThread = thread
            thread.start()
            Log.d(TAG, "Recording thread started, isRecordingThreadRunning=${isRecordingThreadRunning.get()}")

        } catch (e: Throwable) {
            Log.e(TAG, "startListening() error: ${e.message}", e)
            listener?.onError("Lỗi startListening: ${e.message}")
        } finally {
            // CHỈ reset nếu thread chưa chạy hoặc đã kết thúc
            // Nếu thread đang chạy, giữ nguyên true để thread biết cần dừng
            val t = recordingThread
            if (t == null || !t.isAlive) {
                isRecordingThreadRunning.set(false)
                Log.d(TAG, "SET isRecordingThreadRunning = false (startListening finally, thread not alive)")
            } else {
                Log.d(TAG, "Thread is alive, keeping isRecordingThreadRunning=true")
            }
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening() called")
        isRecordingThreadRunning.set(false)
        Log.d(TAG, "SET isRecordingThreadRunning = false (stopListening)")
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        Log.d(TAG, "Đã dừng ghi âm")
    }

    fun destroy() {
        Log.d(TAG, "Destroy SherpaSpeechManager")
        stopListening()
        try { recordingThread?.interrupt() } catch (_: Exception) {}
        recordingThread = null
        try { recognizer?.release() } catch (_: Exception) {}
        recognizer = null
        modelReady = false
    }
}