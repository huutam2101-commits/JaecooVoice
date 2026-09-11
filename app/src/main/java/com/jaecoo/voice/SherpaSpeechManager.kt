package com.jaecoo.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
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

    @Volatile private var isRecordingThreadRunning = false
    @Volatile private var modelReady = false

    fun setListener(l: RecognitionListener) {
        listener = l
    }

    fun isModelReady(): Boolean = modelReady

    fun reset() {
        Log.d(TAG, "reset() called: forcing isRecordingThreadRunning = false and cleanup")
        isRecordingThreadRunning = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { recordingThread?.interrupt() } catch (_: Exception) {}
        recordingThread = null
    }

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
    @Synchronized
    fun startListening() {
        Log.d(TAG, "startListening() ENTER: isRecordingThreadRunning=$isRecordingThreadRunning")
        
        if (isRecordingThreadRunning) {
            Log.w(TAG, "Thread already running, skipping")
            return
        }
        
        isRecordingThreadRunning = true
        Log.d(TAG, "SET isRecordingThreadRunning = true")
        
        try {
            var rec = recognizer
            if (rec == null) {
                Log.d(TAG, "Recognizer null in startListening, initializing model on worker thread...")
                initModel()
                rec = recognizer
            }

            if (rec == null) {
                Log.e(TAG, "Recognizer null sau khi initModel, không thể start")
                isRecordingThreadRunning = false
                Log.d(TAG, "SET isRecordingThreadRunning = false (init failed)")
                listener?.onError("Model chưa sẵn sàng")
                return
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

            val minBuffer = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            Log.d(TAG, "minBufferSize=$minBuffer")

            val bufferSize = (minBuffer * 4).coerceAtLeast(8192)
            
            // Cleanup cũ nếu có
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
                        Log.d(TAG, "AudioRecord initialized successfully with source $src")
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
                isRecordingThreadRunning = false
                Log.d(TAG, "SET isRecordingThreadRunning = false (AudioRecord init failed)")
                listener?.onError("Không khởi tạo được mic")
                return
            }

            audioRecord?.startRecording()
            Log.d(TAG, "startRecording() called: recordingState=${audioRecord?.recordingState}")

            // Verify recording sau 100ms
            Thread.sleep(100)
            var recState = audioRecord?.recordingState
            Log.d(TAG, "After 100ms, recordingState = $recState (3=RECORDING)")

            if (recState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Mic KHÔNG ở trạng thái RECORDING sau 100ms! recordingState=$recState")
                var retries = 0
                while (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING && retries < 3) {
                    retries++
                    Log.w(TAG, "Retry startRecording() #$retries...")
                    Thread.sleep(500)
                    try { audioRecord?.startRecording() } catch (e: Exception) { Log.e(TAG, "Retry startRecording failed", e) }
                }
                recState = audioRecord?.recordingState
                if (recState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "Mic vẫn KHÔNG ở trạng thái RECORDING sau $retries retries! recordingState=$recState")
                    isRecordingThreadRunning = false
                    Log.d(TAG, "SET isRecordingThreadRunning = false (mic not recording)")
                    listener?.onError("Mic không bắt đầu")
                    return
                }
            }

            // Chạy thread ghi âm
            recordingThread = Thread {
                Log.d(TAG, "SherpaRecordingThread STARTED")
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    val chunkSamples = 1600
                    val buffer = ShortArray(chunkSamples)
                    var currentStream = rec.createStream()
                    var lastPartialText = ""
                    var lastSpeechTime = System.currentTimeMillis()
                    var hasDetectedSpeech = false
                    var loopCount = 0

                    while (isRecordingThreadRunning) {
                        val ar = audioRecord
                        if (ar == null) {
                            Log.e(TAG, "audioRecord null, break loop")
                            break
                        }

                        val readSize = ar.read(buffer, 0, chunkSamples)
                        if (loopCount % 100 == 0) {
                            Log.d(TAG, "readSize=$readSize, loopCount=$loopCount, recordingState=${ar.recordingState}")
                        }
                        
                        if (readSize > 0) {
                            val samples = FloatArray(readSize) { i -> buffer[i] / 32768.0f }
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

                                if (hasDetectedSpeech && System.currentTimeMillis() - lastSpeechTime > SILENCE_THRESHOLD_MS) {
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
                        } else {
                            try { Thread.sleep(20) } catch (_: Exception) {}
                        }
                        loopCount++
                    }
                    try { currentStream.release() } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.e(TAG, "Thread error: ${e.message}", e)
                } finally {
                    Log.d(TAG, "SherpaRecordingThread ENDED")
                    isRecordingThreadRunning = false
                    Log.d(TAG, "SET isRecordingThreadRunning = false (in thread finally)")
                    try { audioRecord?.stop() } catch (_: Exception) {}
                    try { audioRecord?.release() } catch (_: Exception) {}
                    audioRecord = null
                }
            }
            recordingThread?.name = "SherpaRecordingThread"
            recordingThread?.start()
            Log.d(TAG, "Recording thread started")

        } catch (e: Exception) {
            Log.e(TAG, "startListening() error: ${e.message}", e)
            isRecordingThreadRunning = false
            Log.d(TAG, "SET isRecordingThreadRunning = false (in catch)")
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening() called")
        isRecordingThreadRunning = false
        Log.d(TAG, "SET isRecordingThreadRunning = false (stopListening)")
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
        try { recordingThread?.interrupt() } catch (_: Exception) {}
        recordingThread = null
        recognizer?.release()
        recognizer = null
        modelReady = false
    }
}
