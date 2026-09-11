package com.jaecoo.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService hỗ trợ bắt phím vô lăng và tương tác UI hệ thống
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceA11y"

        @Volatile
        private var instance: VoiceAccessibilityService? = null

        fun getInstance(): VoiceAccessibilityService? = instance

        fun getCurrentRoot(): AccessibilityNodeInfo? = instance?.rootInActiveWindow

        fun findNodeByText(text: String): AccessibilityNodeInfo? {
            val root = instance?.rootInActiveWindow ?: return null
            return NodeFinder.find(root, text)
        }

        fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
            val root = getCurrentRoot() ?: return null
            return NodeFinder.findByViewId(root, viewId)
        }

        fun clickByViewId(viewId: String): Boolean {
            val node = findNodeByViewId(viewId)
            if (node == null) {
                Log.e(TAG, "Không tìm thấy node: $viewId")
                return false
            }
            return clickNode(node)
        }

        fun waitForNodeByViewId(viewId: String, timeoutMs: Long): AccessibilityNodeInfo? {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val node = findNodeByViewId(viewId)
                if (node != null) return node
                try { Thread.sleep(200) } catch (_: InterruptedException) {}
            }
            Log.w(TAG, "Timeout chờ node: $viewId")
            return null
        }

        fun clickNode(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "clickNode via performAction SUCCESS")
                return true
            }

            // Fallback click bằng cử chỉ chạm tọa độ
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.d(TAG, "performAction failed, falling back to clickByBounds: $bounds")
            return clickByBounds(bounds)
        }

        fun clickByBounds(bounds: Rect): Boolean {
            return clickByCoordinates(bounds.centerX(), bounds.centerY())
        }

        fun clickByCoordinates(x: Int, y: Int): Boolean {
            return testClickCoordinates(x, y)
        }

        fun testClickCoordinates(x: Int, y: Int): Boolean {
            Log.d(TAG, "testClickCoordinates ($x, $y)")
            val service = instance ?: return false
            return try {
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())

                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(path, 0, 50)
                    )
                    .build()

                val result = service.dispatchGesture(gesture, null, null)
                Log.d(TAG, "dispatchGesture: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "testClickCoordinates crash", e)
                false
            }
        }

        fun launchApp(packageName: String): Boolean {
            val ctx = instance ?: return false

            val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                Log.d(TAG, "Launched via intent: $packageName")
                return true
            }

            // Fallback chạy trên background thread
            Thread {
                try {
                    Log.w(TAG, "Fallback for: $packageName")
                    goHome()
                    Thread.sleep(1200)

                    val coords = when (packageName) {
                        "com.desaysv.svhvac" -> Pair(810, 1840)
                        else -> null
                    }

                    if (coords != null) {
                        testClickCoordinates(coords.first, coords.second)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback fail", e)
                }
            }.start()

            return true  // return ngay, không chờ
        }

        fun goHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun waitForNode(text: String, timeoutMs: Long = 3000L): AccessibilityNodeInfo? {
            val startTime = SystemClock.uptimeMillis()
            while (SystemClock.uptimeMillis() - startTime < timeoutMs) {
                val node = findNodeByText(text)
                if (node != null) return node
                SystemClock.sleep(200)
            }

            Log.w(TAG, "waitForNode TIMEOUT for text='$text'. Dumping UI tree:")
            instance?.rootInActiveWindow?.let { NodeFinder.dumpToLog(it) }
            return null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "VoiceAccessibilityService Connected")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        Log.d(TAG, "onAccessibilityEvent: type=$eventType, pkg=${event.packageName}")
    }

    override fun onInterrupt() {
        Log.d(TAG, "VoiceAccessibilityService Interrupted")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        Log.d(TAG, "onKeyEvent: keyCode=${event.keyCode}, action=${event.action}")

        val isVoiceKey = event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                event.keyCode == 231 ||
                event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                event.keyCode == 79 ||
                event.keyCode == KeyEvent.KEYCODE_SEARCH ||
                event.keyCode == 84 ||
                event.keyCode == 290 // KEYCODE_VR từ CarDefs

        if (isVoiceKey) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Bắt phím Voice vô lăng (${event.keyCode}) -> Mở Overlay")
                OverlayService.showOverlay(this)
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceAccessibilityService Destroyed")
        instance = null
    }
}
