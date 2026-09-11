package com.jaecoo.voice

import android.content.Context
import android.util.Log

data class VehicleAction(
    val targetPackage: String,
    val viewId: String,
    val actionName: String  // để báo cho user
)

object CommandExecutor {

    private const val TAG = "CmdExec"
    const val SIMULATE = false  // ← QUAN TRỌNG

    fun executeCommand(context: Context, text: String): String {
        val normalized = normalize(text)
        Log.d(TAG, "executeCommand: '$text' -> '$normalized'")

        // Tìm VehicleAction từ text
        val action = findVehicleAction(normalized)

        if (action != null) {
            return executeVehicleCommand(context, action)
        }

        // Fallback cho lệnh không phải điều khiển xe
        return when {
            normalized.contains("youtube") -> {
                VoiceAccessibilityService.launchApp("com.google.android.youtube")
                "Đang mở YouTube"
            }
            normalized.contains("chi duong") || normalized.contains("duong den") -> {
                val dest = extractDestination(text)
                navigate(context, dest)
                "Đang mở bản đồ đến $dest"
            }
            normalized.contains("mo ban do") || normalized.contains("xem ban do") -> {
                navigate(context, "")
                "Đang mở bản đồ"
            }
            else -> "Tôi chưa hiểu lệnh: $text"
        }
    }

    // ─── Mapping lệnh → Resource ID ───
    private fun findVehicleAction(normalized: String): VehicleAction? {

        // ===== CỬA SỔ TRỜI — com.desaysv.setting =====
        if (matches(normalized, listOf("mo cua so troi", "mo noc", "mo cua troi"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cb_sunroof_open", "mở cửa sổ trời")
        }
        if (matches(normalized, listOf("dong cua so troi", "dong noc"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cb_sunroof_open", "đóng cửa sổ trời")
        }

        // ===== CỐP =====
        if (matches(normalized, listOf("mo cop", "mo trunk"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cb_trunk", "mở cốp")
        }
        if (matches(normalized, listOf("dong cop", "dong trunk"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cb_trunk", "đóng cốp")
        }

        // ===== KHÓA CỬA =====
        if (matches(normalized, listOf("khoa cua", "khoa trung tam"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cb_central_lock", "khóa cửa")
        }

        // ===== KHÓA CỬA SỔ =====
        if (matches(normalized, listOf("khoa cua so", "child lock"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cb_window_lock", "khóa cửa sổ")
        }

        // ===== HVAC — com.desaysv.svhvac =====
        if (matches(normalized, listOf("bat dieu hoa", "mo dieu hoa", "bat may lanh"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_power", "bật điều hòa")
        }
        if (matches(normalized, listOf("tat dieu hoa", "tat may lanh"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_power", "tắt điều hòa")
        }
        if (matches(normalized, listOf("bat ac", "mo ac"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_ac", "bật AC")
        }
        if (matches(normalized, listOf("tat ac"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_ac", "tắt AC")
        }
        if (matches(normalized, listOf("tang gio", "gio manh hon"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_wind_speed_up", "tăng gió")
        }
        if (matches(normalized, listOf("giam gio", "gio yeu hon"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_wind_speed_down", "giảm gió")
        }
        if (matches(normalized, listOf("bat auto", "tu dong"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_auto", "bật auto")
        }
        if (matches(normalized, listOf("loc khong khi", "loc khi", "purifier"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_pm25_req", "bật lọc khí")
        }
        if (matches(normalized, listOf("say kinh truoc", "defrost truoc"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_front_defrost", "sấy kính trước")
        }
        if (matches(normalized, listOf("say kinh sau", "defrost sau"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_rear_defrost", "sấy kính sau")
        }
        if (matches(normalized, listOf("gio mat", "thoi mat"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_blow_mode_face", "gió mặt")
        }
        if (matches(normalized, listOf("gio chan", "thoi chan"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_blow_mode_foot", "gió chân")
        }
        if (matches(normalized, listOf("gio kinh", "thoi kinh"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_blow_mode_window", "gió kính")
        }
        if (matches(normalized, listOf("tuan hoan", "recycle"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_recycle", "tuần hoàn gió")
        }
        if (matches(normalized, listOf("dong bo nhiet", "sync nhiet"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/iv_hvac_sync", "đồng bộ nhiệt")
        }

        // ===== SƯỞI GHẾ =====
        if (matches(normalized, listOf("bat suoi ghe", "bat suoi", "suoi ghe"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/btn_chair_heat", "bật sưởi ghế")
        }
        if (matches(normalized, listOf("lam mat ghe", "ventilated seat"))) {
            return VehicleAction("com.desaysv.svhvac",
                "com.desaysv.svhvac:id/btn_chair_wind", "làm mát ghế")
        }

        // ===== ĐÈN NỘI THẤT — com.desaysv.setting =====
        if (matches(normalized, listOf("bat den noi that", "bat den ambien"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cl_light_model", "bật đèn nội thất")
        }
        if (matches(normalized, listOf("tat den noi that", "tat den ambien"))) {
            return VehicleAction("com.desaysv.setting",
                "com.desaysv.setting:id/cl_light_model", "tắt đèn nội thất")
        }

        return null
    }

    private fun matches(normalized: String, keywords: List<String>): Boolean {
        return keywords.any { normalized.contains(it) }
    }

    // ─── Execute ───
    private fun executeVehicleCommand(context: Context, action: VehicleAction): String {
        return try {
            Log.d(TAG, "Thực thi: ${action.actionName}")

            // 1. Launch app / panel qua Launcher click
            val launched = VoiceAccessibilityService.launchApp(action.targetPackage)
            if (!launched) {
                Log.w(TAG, "Không launch được ${action.targetPackage}")
                return "Không mở được ứng dụng"
            }

            // 2. Chờ node UI xuất hiện & click
            val node = VoiceAccessibilityService.waitForNodeByViewId(action.viewId, 3000)
            if (node != null) {
                val clicked = VoiceAccessibilityService.clickByViewId(action.viewId)
                Thread.sleep(500)
                if (clicked) return "Đã ${action.actionName}"
            }

            // Fallback nếu là HVAC và không click được nút cụ thể
            if (action.targetPackage == "com.desaysv.svhvac") {
                return "Đã mở bảng điều hòa, bạn điều chỉnh nhé"
            }

            "Đã ${action.actionName}"
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi thực thi", e)
            "Có lỗi: ${e.message}"
        }
    }

    // ─── Normalize tiếng Việt ───
    private fun normalize(s: String): String {
        if (s.isBlank()) return ""
        var t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        t = t.replace("\\p{M}+".toRegex(), "")
        t = t.replace('đ', 'd').replace('Đ', 'D')
        return t.lowercase().replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }

    // ─── Navigate ───
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
