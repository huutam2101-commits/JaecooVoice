package com.jaecoo.voice

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity màn hình Debug test nhanh các câu lệnh không cần nói
 */
class DebugScreen : AppCompatActivity() {

    companion object {
        private const val TAG = "DebugScreen"
    }

    private lateinit var edtCommand: EditText
    private lateinit var btnTest: Button
    private lateinit var btnTestAll: Button
    private lateinit var btnDumpUi: Button
    private lateinit var btnOpenVr: Button
    private lateinit var btnA11y: Button
    private lateinit var tvDebugLog: TextView

    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        edtCommand = findViewById(R.id.edtCommand)
        btnTest = findViewById(R.id.btnTest)
        btnTestAll = findViewById(R.id.btnTestAll)
        btnDumpUi = findViewById(R.id.btnDumpUi)
        btnOpenVr = findViewById(R.id.btnOpenVr)
        btnA11y = findViewById(R.id.btnA11y)
        tvDebugLog = findViewById(R.id.tvDebugLog)

        appendLog("DebugScreen khởi chạy")

        btnTest.setOnClickListener {
            val cmdText = edtCommand.text.toString().trim()
            if (cmdText.isNotBlank()) {
                val result = CommandExecutor.executeCommand(this, cmdText)
                appendLog("Lệnh: '$cmdText' -> $result")
            } else {
                Toast.makeText(this, "Hãy nhập câu lệnh", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestAll.setOnClickListener {
            runAll13SampleCommands()
        }

        btnDumpUi.setOnClickListener {
            val service = VoiceAccessibilityService.getInstance()
            if (service != null) {
                val root = service.rootInActiveWindow
                NodeFinder.dumpToLog(root)
                appendLog("Đã dump cây UI vào Logcat (Tag: NodeFinder)")
            } else {
                appendLog("Lỗi: VoiceAccessibilityService chưa bật!")
            }
        }

        btnOpenVr.setOnClickListener {
            val success = VoiceAccessibilityService.launchApp("com.desaysv.vrcontrol")
            appendLog("Mở com.desaysv.vrcontrol: $success")
        }

        btnA11y.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                appendLog("Đã mở Cài đặt Accessibility")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi mở Accessibility Settings: ${e.message}", e)
                appendLog("Lỗi mở Accessibility Settings: ${e.message}")
            }
        }
    }

    private fun runAll13SampleCommands() {
        val sampleList = listOf(
            "mở cửa sổ trời",
            "đóng cửa sổ trời",
            "bật điều hòa",
            "tắt điều hòa",
            "tăng nhiệt độ",
            "giảm nhiệt độ",
            "mở cửa kính",
            "đóng cửa kính",
            "mở cốp xe",
            "bật đèn",
            "tắt đèn",
            "dẫn đường đến Hồ Hoàn Kiếm",
            "mở youtube nhạc trẻ"
        )

        appendLog("=== BẮT ĐẦU TEST 13 LỆNH MẪU ===")
        sampleList.forEachIndexed { index, cmd ->
            val res = CommandExecutor.executeCommand(this, cmd)
            appendLog("[${index + 1}] '$cmd' => $res")
        }
        appendLog("=== HOÀN TẤT TEST 13 LỆNH ===")
    }

    private fun appendLog(text: String) {
        Log.d(TAG, text)
        logBuffer.append(text).append("\n")
        tvDebugLog.text = logBuffer.toString()
    }
}
