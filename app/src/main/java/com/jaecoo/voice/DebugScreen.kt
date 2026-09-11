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

        val btnHvacPower: Button = findViewById(R.id.btnHvacPower)
        val btnHvacAc: Button = findViewById(R.id.btnHvacAc)
        val btnHvacFanUp: Button = findViewById(R.id.btnHvacFanUp)
        val btnHvacFanDown: Button = findViewById(R.id.btnHvacFanDown)
        val btnHvacTempUp: Button = findViewById(R.id.btnHvacTempUp)
        val btnHvacTempDown: Button = findViewById(R.id.btnHvacTempDown)
        val btnHvacAuto: Button = findViewById(R.id.btnHvacAuto)
        val btnHvacRecirc: Button = findViewById(R.id.btnHvacRecirc)
        val btnHvacIon: Button = findViewById(R.id.btnHvacIon)
        val btnHvacSync: Button = findViewById(R.id.btnHvacSync)
        val btnHvacState: Button = findViewById(R.id.btnHvacState)

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

        btnCoord1.setOnClickListener { testClimateCoord(725, 1830) }
        btnCoord2.setOnClickListener { testClimateCoord(287, 1830) }
        btnCoord3.setOnClickListener { testClimateCoord(475, 1830) }

        btnHvacPower.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacTogglePower()
                HvacState.isPowerOn = !HvacState.isPowerOn
                runOnUiThread { appendLog("Power toggle=$res\n${HvacState.describe()}") }
            }.start()
        }

        btnHvacAc.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacToggleAc()
                HvacState.isAcOn = !HvacState.isAcOn
                runOnUiThread { appendLog("AC toggle=$res\n${HvacState.describe()}") }
            }.start()
        }

        btnHvacFanUp.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacIncreaseFan()
                if (HvacState.fanSpeed < 7) HvacState.fanSpeed++
                runOnUiThread { appendLog("Fan + =$res (Speed: ${HvacState.fanSpeed})") }
            }.start()
        }

        btnHvacFanDown.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacDecreaseFan()
                if (HvacState.fanSpeed > 1) HvacState.fanSpeed--
                runOnUiThread { appendLog("Fan - =$res (Speed: ${HvacState.fanSpeed})") }
            }.start()
        }

        btnHvacTempUp.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacIncreaseTempDriver()
                if (HvacState.tempDriver < 32f) HvacState.tempDriver += 1f
                runOnUiThread { appendLog("Temp + =$res (${HvacState.tempDriver.toInt()}°C)") }
            }.start()
        }

        btnHvacTempDown.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacDecreaseTempDriver()
                if (HvacState.tempDriver > 16f) HvacState.tempDriver -= 1f
                runOnUiThread { appendLog("Temp - =$res (${HvacState.tempDriver.toInt()}°C)") }
            }.start()
        }

        btnHvacAuto.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacToggleAuto()
                HvacState.isAutoOn = !HvacState.isAutoOn
                runOnUiThread { appendLog("Auto toggle=$res (Auto: ${HvacState.isAutoOn})") }
            }.start()
        }

        btnHvacRecirc.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacToggleRecirculation()
                HvacState.isRecircOn = !HvacState.isRecircOn
                runOnUiThread { appendLog("⭐ Recirculation toggle=$res (Gió: ${if (HvacState.isRecircOn) "TRONG" else "NGOÀI"})") }
            }.start()
        }

        btnHvacIon.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacToggleIon()
                HvacState.isIonOn = !HvacState.isIonOn
                runOnUiThread { appendLog("ION toggle=$res (ION: ${HvacState.isIonOn})") }
            }.start()
        }

        btnHvacSync.setOnClickListener {
            Thread {
                val res = VoiceAccessibilityService.hvacToggleSync()
                HvacState.isSyncOn = !HvacState.isSyncOn
                runOnUiThread { appendLog("Sync toggle=$res (Sync: ${HvacState.isSyncOn})") }
            }.start()
        }

        btnHvacState.setOnClickListener {
            appendLog(HvacState.describe())
        }
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

                // b. Đợi 800ms
                try { Thread.sleep(800) } catch (_: Exception) {}

                // c. Gọi clickByCoordinates(x, y, 300L)
                runOnUiThread {
                    appendLog("Clicking ($x, $y) 300ms...")
                }
                val result = VoiceAccessibilityService.clickByCoordinates(x, y, 300L)
                Log.d(TAG, "Result ($x, $y): $result")

                // d. Đợi 1.5 giây
                try { Thread.sleep(1500) } catch (_: Exception) {}

                // e. Kiểm tra foreground app
                val pkg = getForegroundPackage()
                val logMsg = "Foreground sau click ($x,$y): $pkg (result=$result)"
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
            "lấy gió trong",
            "lấy gió ngoài",
            "mở cửa kính",
            "đóng cửa kính",
            "mở cốp xe",
            "bật đèn",
            "dẫn đường đến Hồ Hoàn Kiếm"
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
