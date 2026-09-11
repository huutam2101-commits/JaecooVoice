package com.jaecoo.voice

import android.content.Context
import android.util.Log

object CommandExecutor {

    private const val TAG = "CmdExec"
    const val SIMULATE = false

    enum class Action {
        HVAC_ON,
        HVAC_OFF,
        TEMP_UP,
        TEMP_DOWN,
        FAN_UP,
        FAN_DOWN,
        AC_ON,
        AC_OFF,
        AUTO_ON,
        AUTO_OFF,
        ION_ON,
        AIR_RECIRCULATE,
        AIR_FRESH,
        AIR_TOGGLE,
        DEFROST_FRONT,
        DEFROST_REAR,
        SYNC_ON,
        SUNROOF_OPEN,
        SUNROOF_CLOSE,
        TRUNK_OPEN,
        TRUNK_CLOSE,
        WINDOW_OPEN,
        WINDOW_CLOSE,
        LIGHT_ON,
        LIGHT_OFF,
        YOUTUBE,
        NAVIGATE,
        NAVIGATE_HOME,
        MAP_OPEN,
        UNKNOWN
    }

    fun executeCommand(context: Context, rawText: String): String {
        val normalized = normalize(rawText)
        Log.d(TAG, "executeCommand raw='$rawText' -> normalized='$normalized'")

        val action = parseAction(normalized)
        Log.d(TAG, "Parsed Action: $action")

        return when (action) {
            Action.HVAC_ON -> {
                if (HvacState.isPowerOn) return "Điều hòa đang bật rồi"
                VoiceAccessibilityService.hvacTogglePower()
                HvacState.isPowerOn = true
                "Đã bật điều hòa"
            }
            Action.HVAC_OFF -> {
                if (!HvacState.isPowerOn) return "Điều hòa đang tắt rồi"
                VoiceAccessibilityService.hvacTogglePower()
                HvacState.isPowerOn = false
                "Đã tắt điều hòa"
            }
            Action.TEMP_UP -> {
                if (HvacState.tempDriver >= 32f) return "Nhiệt độ đã tối đa 32°C"
                VoiceAccessibilityService.hvacIncreaseTempDriver()
                HvacState.tempDriver += 1f
                "Đã tăng nhiệt độ lên ${HvacState.tempDriver.toInt()}°C"
            }
            Action.TEMP_DOWN -> {
                if (HvacState.tempDriver <= 16f) return "Nhiệt độ đã tối thiểu 16°C"
                VoiceAccessibilityService.hvacDecreaseTempDriver()
                HvacState.tempDriver -= 1f
                "Đã giảm nhiệt độ xuống ${HvacState.tempDriver.toInt()}°C"
            }
            Action.FAN_UP -> {
                if (HvacState.fanSpeed >= 7) return "Quạt đã tối đa"
                VoiceAccessibilityService.hvacIncreaseFan()
                HvacState.fanSpeed += 1
                "Đã tăng quạt lên mức ${HvacState.fanSpeed}/7"
            }
            Action.FAN_DOWN -> {
                if (HvacState.fanSpeed <= 1) return "Quạt đã tối thiểu"
                VoiceAccessibilityService.hvacDecreaseFan()
                HvacState.fanSpeed -= 1
                "Đã giảm quạt xuống mức ${HvacState.fanSpeed}/7"
            }
            Action.AC_ON -> {
                if (HvacState.isAcOn) return "AC đang bật rồi"
                VoiceAccessibilityService.hvacToggleAc()
                HvacState.isAcOn = true
                "Đã bật AC"
            }
            Action.AC_OFF -> {
                if (!HvacState.isAcOn) return "AC đang tắt rồi"
                VoiceAccessibilityService.hvacToggleAc()
                HvacState.isAcOn = false
                "Đã tắt AC"
            }
            Action.AUTO_ON -> {
                if (HvacState.isAutoOn) return "Auto đang bật rồi"
                VoiceAccessibilityService.hvacToggleAuto()
                HvacState.isAutoOn = true
                "Đã bật chế độ tự động"
            }
            Action.AUTO_OFF -> {
                if (!HvacState.isAutoOn) return "Auto đang tắt rồi"
                VoiceAccessibilityService.hvacToggleAuto()
                HvacState.isAutoOn = false
                "Đã tắt chế độ tự động"
            }
            Action.ION_ON -> {
                VoiceAccessibilityService.hvacToggleIon()
                HvacState.isIonOn = !HvacState.isIonOn
                if (HvacState.isIonOn) "Đã bật lọc không khí" else "Đã tắt lọc không khí"
            }
            Action.AIR_RECIRCULATE -> {
                if (HvacState.isRecircOn) return "Đang lấy gió trong rồi"
                VoiceAccessibilityService.hvacToggleRecirculation()
                HvacState.isRecircOn = true
                "Đã chuyển sang lấy gió trong (tuần hoàn)"
            }
            Action.AIR_FRESH -> {
                if (!HvacState.isRecircOn) return "Đang lấy gió ngoài rồi"
                VoiceAccessibilityService.hvacToggleRecirculation()
                HvacState.isRecircOn = false
                "Đã chuyển sang lấy gió ngoài"
            }
            Action.AIR_TOGGLE -> {
                VoiceAccessibilityService.hvacToggleRecirculation()
                HvacState.isRecircOn = !HvacState.isRecircOn
                if (HvacState.isRecircOn) "Đã chuyển sang gió trong" else "Đã chuyển sang gió ngoài"
            }
            Action.DEFROST_FRONT -> {
                VoiceAccessibilityService.hvacDefrostFront()
                HvacState.isDefrostFront = !HvacState.isDefrostFront
                if (HvacState.isDefrostFront) "Đã bật sấy kính trước" else "Đã tắt sấy kính trước"
            }
            Action.DEFROST_REAR -> {
                VoiceAccessibilityService.hvacDefrostRear()
                HvacState.isDefrostRear = !HvacState.isDefrostRear
                if (HvacState.isDefrostRear) "Đã bật sấy kính sau" else "Đã tắt sấy kính sau"
            }
            Action.SYNC_ON -> {
                VoiceAccessibilityService.hvacToggleSync()
                HvacState.isSyncOn = !HvacState.isSyncOn
                if (HvacState.isSyncOn) "Đã đồng bộ nhiệt độ 2 vùng" else "Đã tắt đồng bộ"
            }
            Action.SUNROOF_OPEN -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cb_sunroof_open", "mở cửa sổ trời")
            }
            Action.SUNROOF_CLOSE -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cb_sunroof_open", "đóng cửa sổ trời")
            }
            Action.TRUNK_OPEN -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cb_trunk", "mở cốp xe")
            }
            Action.TRUNK_CLOSE -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cb_trunk", "đóng cốp xe")
            }
            Action.WINDOW_OPEN -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cb_window_lock", "mở cửa kính")
            }
            Action.WINDOW_CLOSE -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cb_window_lock", "đóng cửa kính")
            }
            Action.LIGHT_ON -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cl_light_model", "bật đèn nội thất")
            }
            Action.LIGHT_OFF -> {
                executeVehicleCenterAction("com.desaysv.setting:id/cl_light_model", "tắt đèn nội thất")
            }
            Action.YOUTUBE -> {
                VoiceAccessibilityService.launchApp("com.google.android.youtube")
                "Đang mở YouTube"
            }
            Action.NAVIGATE -> {
                val dest = extractDestination(rawText)
                navigate(context, dest)
                "Đang mở bản đồ đến $dest"
            }
            Action.NAVIGATE_HOME -> {
                navigate(context, "Nhà")
                "Đang chỉ đường về nhà"
            }
            Action.MAP_OPEN -> {
                navigate(context, "")
                "Đang mở bản đồ"
            }
            Action.UNKNOWN -> {
                "Tôi chưa hiểu lệnh này, bạn nói lại được không?"
            }
        }
    }

    private fun executeVehicleCenterAction(viewId: String, actionName: String): String {
        VoiceAccessibilityService.openVehicleCenterViaLauncher()
        Thread.sleep(1500)
        val node = VoiceAccessibilityService.waitForNodeByViewId(viewId, 3000)
        if (node != null) {
            val clicked = VoiceAccessibilityService.clickByViewId(viewId)
            Thread.sleep(500)
            if (clicked) return "Đã $actionName"
        }
        return "Đã $actionName"
    }

    private fun parseAction(n: String): Action {
        return when {
            // AC
            matches(n, listOf("bat ac", "mo ac")) -> Action.AC_ON
            matches(n, listOf("tat ac")) -> Action.AC_OFF

            // Auto
            matches(n, listOf("bat auto", "tu dong", "auto")) -> Action.AUTO_ON
            matches(n, listOf("tat auto")) -> Action.AUTO_OFF

            // Lọc khí
            matches(n, listOf("loc khong khi", "loc khi", "ion", "purifier")) -> Action.ION_ON

            // Gió trong/ngoài
            matches(n, listOf("gio trong", "tuan hoan", "lay gio trong")) -> Action.AIR_RECIRCULATE
            matches(n, listOf("gio ngoai", "lay gio ngoai", "gio tu nhien")) -> Action.AIR_FRESH
            matches(n, listOf("chuyen gio", "doi gio")) -> Action.AIR_TOGGLE

            // Sấy kính
            matches(n, listOf("say kinh truoc", "say kinh", "defrost truoc")) -> Action.DEFROST_FRONT
            matches(n, listOf("say kinh sau", "defrost sau")) -> Action.DEFROST_REAR

            // Sync
            matches(n, listOf("dong bo nhiet", "sync nhiet", "dong bo")) -> Action.SYNC_ON

            // HVAC Power
            matches(n, listOf("bat dieu hoa", "mo dieu hoa", "bat may lanh")) -> Action.HVAC_ON
            matches(n, listOf("tat dieu hoa", "tat may lanh")) -> Action.HVAC_OFF

            // Temp
            matches(n, listOf("tang nhiet do", "tang do", "nong hon")) -> Action.TEMP_UP
            matches(n, listOf("giam nhiet do", "giam do", "lanh hon")) -> Action.TEMP_DOWN

            // Fan
            matches(n, listOf("tang gio", "gio manh hon", "tang quat")) -> Action.FAN_UP
            matches(n, listOf("giam gio", "gio yeu hon", "giam quat")) -> Action.FAN_DOWN

            // Sunroof
            matches(n, listOf("mo cua so troi", "mo noc", "mo cua troi")) -> Action.SUNROOF_OPEN
            matches(n, listOf("dong cua so troi", "dong noc")) -> Action.SUNROOF_CLOSE

            // Trunk
            matches(n, listOf("mo cop", "mo trunk", "mo cop xe")) -> Action.TRUNK_OPEN
            matches(n, listOf("dong cop", "dong trunk")) -> Action.TRUNK_CLOSE

            // Window
            matches(n, listOf("mo cua kinh", "ha kinh", "mo kinh")) -> Action.WINDOW_OPEN
            matches(n, listOf("dong cua kinh", "nang kinh", "dong kinh")) -> Action.WINDOW_CLOSE

            // Light
            matches(n, listOf("bat den noi that", "bat den ambien", "bat den")) -> Action.LIGHT_ON
            matches(n, listOf("tat den noi that", "tat den ambien", "tat den")) -> Action.LIGHT_OFF

            // Navigation / YouTube
            matches(n, listOf("chi duong", "dan duong", "duong den")) -> Action.NAVIGATE
            matches(n, listOf("ve nha", "dua toi ve nha")) -> Action.NAVIGATE_HOME
            matches(n, listOf("mo ban do", "xem ban do")) -> Action.MAP_OPEN
            matches(n, listOf("youtube", "mo youtube")) -> Action.YOUTUBE

            else -> Action.UNKNOWN
        }
    }

    private fun matches(normalized: String, keywords: List<String>): Boolean {
        return keywords.any { normalized.contains(it) }
    }

    private fun normalize(s: String): String {
        if (s.isBlank()) return ""
        var t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        t = t.replace("\\p{M}+".toRegex(), "")
        t = t.replace('đ', 'd').replace('Đ', 'D')
        return t.lowercase().replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }

    private fun navigate(context: Context, destination: String) {
        try {
            val uri = if (destination.isBlank())
                android.net.Uri.parse("geo:0,0")
            else
                android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(destination))
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "navigate fail: ${e.message}")
        }
    }

    private fun extractDestination(text: String): String {
        val n = text.lowercase()
        val keywords = listOf("chỉ đường đến", "đường đến", "dẫn đường đến", "đi đến", "tới")
        for (kw in keywords) {
            val idx = n.indexOf(kw)
            if (idx >= 0) return text.substring(idx + kw.length).trim()
        }
        return text
    }
}
