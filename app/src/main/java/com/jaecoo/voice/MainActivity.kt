package com.jaecoo.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activity khởi chạy trong suốt, lập tức mở Overlay Service và tự đóng task
 */
class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openOverlayAndFinish()
        } else {
            Toast.makeText(this, "Cần quyền micro để ghi âm", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reset sạch trạng thái SherpaSpeechManager khi mở app
        SherpaSpeechManager.getInstance(this).reset()

        // Kiểm tra quyền RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            openOverlayAndFinish()
        }
    }

    private fun openOverlayAndFinish() {
        try {
            if (Settings.canDrawOverlays(this)) {
                Log.d("MainActivity", "Overlay permission: GRANTED -> Launching OverlayService")
                OverlayService.showOverlay(this)

                // Chờ 500ms để Overlay kịp hiện rồi finish hẳn khỏi recent apps
                Handler(Looper.getMainLooper()).postDelayed({
                    finishAndRemoveTask()
                }, 500L)
            } else {
                Log.d("MainActivity", "Overlay permission: DENIED")
                Toast.makeText(
                    this,
                    "Chưa cấp quyền Overlay. Chạy lệnh ADB:\nadb shell appops set com.jaecoo.voice SYSTEM_ALERT_WINDOW allow",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting OverlayService: ${e.message}", e)
            finish()
        }
    }
}
