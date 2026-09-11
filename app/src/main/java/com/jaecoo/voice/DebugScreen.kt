package com.jaecoo.voice

import android.content.Context
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

    private lateinit var btnCoord1: Button
    private lateinit var btnCoord2: Button
    private lateinit var btnCoord3: Button
    private lateinit var btnCoord4: Button
    private lateinit var btnCoord5: Button
    private lateinit var btnCoord6: Button

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

        btnCoord1 = findViewById(R.id.btnCoord1)
        btnCoord2 = findViewById(R.id.btnCoord2)
        btnCoord3 = findViewById(R.id.btnCoord3)
        btnCoord4 = findViewById(R.id.btnCoord4)
        btnCoord5 = findViewById(R.id.btnCoord5)
        btnCoord6 = findViewById(R.id.btnCoord6)

        appendLog("DebugScreen khởi chạy")

        btnTest.setOnClickListener {
            val cmdText = edtCommand.text.toString().trim()
            if (cmdText.isNotBlank()) {
                Thread {
                    val result = CommandExecutor.executeCommand(this, cmdText)
                    runOnUiThread {
                        appendLog("Lệnh: '$cmdText' -> $result")
                    }
                }.start()
            } else {
                Toast.makeText(this, "Hãy nhập câu lệnh", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestAll.setOnClickListener {
            Thread {
                runAll13SampleCommands()
            }.start()
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

        btnCoord1.setOnClickListener { testClimateCoord(810, 1800) }
        btnCoord2.setOnClickListener { testClimateCoord(810, 1840) }
        btnCoord3.setOnClickListener { testClimateCoord(810, 1880) }
        btnCoord4.setOnClickListener { testClimateCoord(800, 1840) }
        btnCoord5.setOnClickListener { testClimateCoord(820, 1840) }
        btnCoord6.setOnClickListener { testClimateCoord(850, 1840) }
    }

    private fun testClimateCoord(x: Int, y: Int) {
        Thread {
            try {
                Log.d(TAG, "Test ($x, $y) starting...")
                runOnUiThread {
                    appendLog("Test ($x, $y) starting -> Về Home...")
                }

                val svc = VoiceAccessibilityService.getInstance()
                if (svc == null) {
                    Log.e(TAG, "VoiceA11y service NULL")
                    runOnUiThread {
                        appendLog("Lỗi: VoiceA11y service NULL")
                    }
                    return@Thread
                }

                // a. Về Home trước
                VoiceAccessibilityService.goHome()

                // b. Đợi 1 giây
                try { Thread.sleep(1000) } catch (_: Exception) {}

                // c. Gọi testClickCoordinates(x, y)
                runOnUiThread {
                    appendLog("Clicking ($x, $y)...")
                }
                val result = VoiceAccessibilityService.testClickCoordinates(x, y)
                Log.d(TAG, "Result ($x, $y): $result")

                // d. Đợi 1.5 giây
                try { Thread.sleep(1500) } catch (_: Exception) {}

                // e. Kiểm tra foreground app
                val pkg = getForegroundPackage()
                val logMsg = "Foreground sau click ($x,$y): $pkg (dispatchResult=$result)"
                Log.d(TAG, logMsg)

                // f. Hiển thị kết quả lên TextView
                runOnUiThread {
                    appendLog(logMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Crash in testClimateCoord", e)
                runOnUiThread {
                    appendLog("Crash ($x, $y): ${e.message}")
                }
            }
        }.start()
    }

    private fun getForegroundPackage(): String {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            @Suppress("DEPRECATION")
            val tasks = activityManager.getRunningTasks(1)
            val topPkg = tasks.firstOrNull()?.topActivity?.packageName
            if (!topPkg.isNullOrBlank()) return topPkg

            val a11yPkg = VoiceAccessibilityService.getInstance()?.rootInActiveWindow?.packageName?.toString()
            if (!a11yPkg.isNullOrBlank()) return a11yPkg

            "unknown"
        } catch (e: Exception) {
            val a11yPkg = VoiceAccessibilityService.getInstance()?.rootInActiveWindow?.packageName?.toString()
            if (!a11yPkg.isNullOrBlank()) return a11yPkg
            "error: ${e.message}"
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

        runOnUiThread { appendLog("=== BẮT ĐẦU TEST 13 LỆNH MẪU ===") }
        sampleList.forEachIndexed { index, cmd ->
            val res = CommandExecutor.executeCommand(this, cmd)
            runOnUiThread { appendLog("[${index + 1}] '$cmd' => $res") }
        }
        runOnUiThread { appendLog("=== HOÀN TẤT TEST 13 LỆNH ===") }
    }

    private fun appendLog(text: String) {
        Log.d(TAG, text)
        logBuffer.append(text).append("\n")
        tvDebugLog.text = logBuffer.toString()
    }
}
