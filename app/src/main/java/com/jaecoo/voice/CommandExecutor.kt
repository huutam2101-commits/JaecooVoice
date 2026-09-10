package com.jaecoo.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Class xử lý chuẩn hóa tiếng Việt, phân tích câu lệnh bằng Fuzzy Matching Levenshtein và thực thi bằng Accessibility
 */
object CommandExecutor {

    private const val TAG = "CmdExec"

    // Phase 2: Giả lập kết quả câu lệnh (Set false khi chạy Phase 3 thực tế)
    const val SIMULATE = true

    enum class Action {
        SUNROOF_OPEN,
        SUNROOF_CLOSE,
        SUN_SHADE_OPEN,
        SUN_SHADE_CLOSE,
        HVAC_ON,
        HVAC_OFF,
        TEMP_UP,
        TEMP_DOWN,
        TEMP_SET,
        WINDOW_OPEN,
        WINDOW_CLOSE,
        HEADLIGHT_ON,
        HEADLIGHT_OFF,
        LIGHT_ON,
        LIGHT_OFF,
        TRUNK_OPEN,
        TRUNK_CLOSE,
        SEAT_HEATER_ON,
        SEAT_HEATER_OFF,
        DEFROSTER_ON,
        DEFROSTER_OFF,
        RADIO_ON,
        VOLUME_UP,
        VOLUME_DOWN,
        MEDIA_NEXT,
        MEDIA_PAUSE,
        MEDIA_PLAY,
        CALL_ANSWER,
        CALL_HANGUP,
        READ_MESSAGE,
        NAVIGATE,
        NAVIGATE_HOME,
        MAP_OPEN,
        FIND_GAS,
        FIND_RESTAURANT,
        YOUTUBE,
        WAKE_UP,
        UNKNOWN
    }

    /**
     * Thực thi câu lệnh từ giọng nói và trả về câu phản hồi TTS
     */
    fun executeCommand(context: Context, rawText: String): String {
        val normalized = removeAccents(rawText)
        Log.d(TAG, "executeCommand raw='$rawText' -> normalized='$normalized'")

        val action = parseAction(normalized)
        Log.d(TAG, "Parsed Action via FuzzyMatch: $action")

        if (SIMULATE) {
            return simulateResponse(action, rawText)
        }

        return when (action) {
            Action.SUNROOF_OPEN -> {
                executeVehicleCommand("com.desaysv.svsetting", "Sunroof")
                "Đã mở cửa sổ trời"
            }
            Action.SUNROOF_CLOSE -> {
                executeVehicleCommand("com.desaysv.svsetting", "Close")
                "Đã đóng cửa sổ trời"
            }
            Action.SUN_SHADE_OPEN -> {
                executeVehicleCommand("com.desaysv.svsetting", "Sun shade")
                "Đã mở tấm che nắng"
            }
            Action.SUN_SHADE_CLOSE -> {
                executeVehicleCommand("com.desaysv.svsetting", "Close shade")
                "Đã đóng tấm che nắng"
            }
            Action.HVAC_ON -> {
                executeVehicleCommand("com.desaysv.svhvac", "A/C")
                "Đã bật điều hòa"
            }
            Action.HVAC_OFF -> {
                executeVehicleCommand("com.desaysv.svhvac", "OFF")
                "Đã tắt điều hòa"
            }
            Action.TEMP_UP -> {
                executeVehicleCommand("com.desaysv.svhvac", "＋")
                "Đã tăng nhiệt độ"
            }
            Action.TEMP_DOWN -> {
                executeVehicleCommand("com.desaysv.svhvac", "－")
                "Đã giảm nhiệt độ"
            }
            Action.TEMP_SET -> {
                executeVehicleCommand("com.desaysv.svhvac", "Temp")
                "Đã chỉnh nhiệt độ"
            }
            Action.WINDOW_OPEN -> {
                executeVehicleCommand("com.desaysv.svsetting", "Window")
                "Đã mở cửa kính"
            }
            Action.WINDOW_CLOSE -> {
                executeVehicleCommand("com.desaysv.svsetting", "Close")
                "Đã đóng cửa kính"
            }
            Action.HEADLIGHT_ON -> {
                executeVehicleCommand("com.desaysv.svsetting", "Headlight")
                "Đã bật đèn pha"
            }
            Action.HEADLIGHT_OFF -> {
                executeVehicleCommand("com.desaysv.svsetting", "Off")
                "Đã tắt đèn pha"
            }
            Action.LIGHT_ON -> {
                executeVehicleCommand("com.desaysv.svsetting", "Light")
                "Đã bật đèn nội thất"
            }
            Action.LIGHT_OFF -> {
                executeVehicleCommand("com.desaysv.svsetting", "Off")
                "Đã tắt đèn nội thất"
            }
            Action.TRUNK_OPEN -> {
                executeVehicleCommand("com.desaysv.svsetting", "Trunk")
                "Đã mở cốp xe"
            }
            Action.TRUNK_CLOSE -> {
                executeVehicleCommand("com.desaysv.svsetting", "Close")
                "Đã đóng cốp xe"
            }
            Action.SEAT_HEATER_ON -> {
                executeVehicleCommand("com.desaysv.svhvac", "Seat Heater")
                "Đã bật sưởi ghế"
            }
            Action.SEAT_HEATER_OFF -> {
                executeVehicleCommand("com.desaysv.svhvac", "Off")
                "Đã tắt sưởi ghế"
            }
            Action.DEFROSTER_ON -> {
                executeVehicleCommand("com.desaysv.svhvac", "Defrost")
                "Đã bật sấy kính"
            }
            Action.DEFROSTER_OFF -> {
                executeVehicleCommand("com.desaysv.svhvac", "Off")
                "Đã tắt sấy kính"
            }
            Action.RADIO_ON -> {
                VoiceAccessibilityService.launchApp("com.desaysv.ivi.vds.tuner")
                "Đã mở Radio"
            }
            Action.VOLUME_UP -> {
                "Đã tăng âm lượng"
            }
            Action.VOLUME_DOWN -> {
                "Đã giảm âm lượng"
            }
            Action.MEDIA_NEXT -> {
                "Đã chuyển bài tiếp theo"
            }
            Action.MEDIA_PAUSE -> {
                "Đã tạm dừng phát nhạc"
            }
            Action.MEDIA_PLAY -> {
                "Đã phát nhạc"
            }
            Action.CALL_ANSWER -> {
                "Đã nghe máy"
            }
            Action.CALL_HANGUP -> {
                "Đã kết thúc cuộc gọi"
            }
            Action.READ_MESSAGE -> {
                "Đang đọc tin nhắn mới nhất"
            }
            Action.NAVIGATE -> {
                val destination = extractDestination(rawText)
                openNavigation(context, destination)
                "Đang mở chỉ đường đến $destination"
            }
            Action.NAVIGATE_HOME -> {
                openNavigation(context, "Nhà")
                "Đang chỉ đường về nhà"
            }
            Action.MAP_OPEN -> {
                openNavigation(context, "")
                "Đang mở bản đồ"
            }
            Action.FIND_GAS -> {
                openNavigation(context, "Cây xăng gần nhất")
                "Đang tìm cây xăng gần nhất"
            }
            Action.FIND_RESTAURANT -> {
                openNavigation(context, "Nhà hàng gần đây")
                "Đang tìm nhà hàng gần đây"
            }
            Action.YOUTUBE -> {
                openYouTube(context)
                "Đang mở YouTube"
            }
            Action.WAKE_UP -> {
                "Chào bạn, tôi là Jaecoo Assistant. Tôi có thể giúp gì cho bạn?"
            }
            Action.UNKNOWN -> {
                "Tôi chưa hiểu lệnh này, bạn nói lại được không?"
            }
        }
    }

    private fun simulateResponse(action: Action, rawText: String): String {
        return when (action) {
            Action.SUNROOF_OPEN -> "Đã mở cửa sổ trời"
            Action.SUNROOF_CLOSE -> "Đã đóng cửa sổ trời"
            Action.SUN_SHADE_OPEN -> "Đã mở tấm che nắng"
            Action.SUN_SHADE_CLOSE -> "Đã đóng tấm che nắng"
            Action.HVAC_ON -> "Đã bật điều hòa ở 24 độ"
            Action.HVAC_OFF -> "Đã tắt điều hòa"
            Action.TEMP_UP -> "Đã tăng nhiệt độ lên 25 độ"
            Action.TEMP_DOWN -> "Đã giảm nhiệt độ xuống 23 độ"
            Action.TEMP_SET -> "Đã đặt nhiệt độ"
            Action.WINDOW_OPEN -> "Đã mở cửa kính"
            Action.WINDOW_CLOSE -> "Đã đóng cửa kính"
            Action.HEADLIGHT_ON -> "Đã bật đèn pha"
            Action.HEADLIGHT_OFF -> "Đã tắt đèn pha"
            Action.LIGHT_ON -> "Đã bật đèn nội thất"
            Action.LIGHT_OFF -> "Đã tắt đèn nội thất"
            Action.TRUNK_OPEN -> "Đã mở cốp xe"
            Action.TRUNK_CLOSE -> "Đã đóng cốp xe"
            Action.SEAT_HEATER_ON -> "Đã bật sưởi ghế"
            Action.SEAT_HEATER_OFF -> "Đã tắt sưởi ghế"
            Action.DEFROSTER_ON -> "Đã bật sấy kính"
            Action.DEFROSTER_OFF -> "Đã tắt sấy kính"
            Action.RADIO_ON -> "Đã mở Radio"
            Action.VOLUME_UP -> "Đã tăng âm lượng"
            Action.VOLUME_DOWN -> "Đã giảm âm lượng"
            Action.MEDIA_NEXT -> "Đã chuyển bài kế tiếp"
            Action.MEDIA_PAUSE -> "Đã tạm dừng nhạc"
            Action.MEDIA_PLAY -> "Đã phát nhạc"
            Action.CALL_ANSWER -> "Đã nghe máy"
            Action.CALL_HANGUP -> "Đã kết thúc cuộc gọi"
            Action.READ_MESSAGE -> "Đang đọc tin nhắn mới nhất"
            Action.NAVIGATE -> "Đang mở bản đồ chỉ đường"
            Action.NAVIGATE_HOME -> "Đang đưa bạn về nhà"
            Action.MAP_OPEN -> "Đang mở bản đồ"
            Action.FIND_GAS -> "Đang tìm cây xăng gần nhất"
            Action.FIND_RESTAURANT -> "Đang tìm nhà hàng gần đây"
            Action.YOUTUBE -> "Đang mở ứng dụng YouTube"
            Action.WAKE_UP -> "Chào bạn, tôi là Jaecoo Assistant. Tôi có thể giúp gì cho bạn?"
            Action.UNKNOWN -> "Tôi chưa hiểu lệnh này, bạn nói lại được không?"
        }
    }

    private fun parseAction(text: String): Action {
        // Thử fuzzyMatch trước
        val matchedAction = fuzzyMatch(text, threshold = 3)
        if (matchedAction != Action.UNKNOWN) {
            return matchedAction
        }
        return Action.UNKNOWN
    }

    /**
     * Tính Levenshtein distance giữa 2 chuỗi (số ký tự cần đổi/xóa/thêm).
     * Dùng để match câu lệnh gần đúng khi user nói sai 1-2 từ.
     */
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // xóa
                    dp[i][j - 1] + 1,      // thêm
                    dp[i - 1][j - 1] + cost  // thay thế
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Fuzzy match: tìm command keyword khớp nhất với input.
     * Trả về Action nếu tìm được, UNKNOWN nếu không.
     *
     * @param input câu user nói (đã normalize, bỏ dấu)
     * @param threshold ngưỡng tối đa — VD: 3 nghĩa là cho phép sai 3 ký tự
     */
    private fun fuzzyMatch(input: String, threshold: Int = 3): Action {
        // Map keyword → Action
        val keywords = mapOf(
            // SUNROOF
            "mo cua so troi" to Action.SUNROOF_OPEN,
            "mo cua troi" to Action.SUNROOF_OPEN,
            "mo noc" to Action.SUNROOF_OPEN,
            "dong cua so troi" to Action.SUNROOF_CLOSE,
            "dong noc" to Action.SUNROOF_CLOSE,

            // SUN SHADE
            "mo tam che nang" to Action.SUN_SHADE_OPEN,
            "mo rem" to Action.SUN_SHADE_OPEN,
            "dong tam che nang" to Action.SUN_SHADE_CLOSE,
            "dong rem" to Action.SUN_SHADE_CLOSE,

            // HVAC
            "bat dieu hoa" to Action.HVAC_ON,
            "mo dieu hoa" to Action.HVAC_ON,
            "bat may lanh" to Action.HVAC_ON,
            "tat dieu hoa" to Action.HVAC_OFF,
            "tat may lanh" to Action.HVAC_OFF,

            // TEMP
            "tang nhiet do" to Action.TEMP_UP,
            "tang do" to Action.TEMP_UP,
            "nong hon" to Action.TEMP_UP,
            "giam nhiet do" to Action.TEMP_DOWN,
            "giam do" to Action.TEMP_DOWN,
            "lanh hon" to Action.TEMP_DOWN,
            "dat nhiet do" to Action.TEMP_SET,
            "chinh nhiet do" to Action.TEMP_SET,

            // WINDOW
            "mo cua kinh" to Action.WINDOW_OPEN,
            "mo kinh" to Action.WINDOW_OPEN,
            "ha kinh" to Action.WINDOW_OPEN,
            "dong cua kinh" to Action.WINDOW_CLOSE,
            "dong kinh" to Action.WINDOW_CLOSE,
            "nang kinh" to Action.WINDOW_CLOSE,

            // HEADLIGHT
            "bat den pha" to Action.HEADLIGHT_ON,
            "bat den" to Action.HEADLIGHT_ON,
            "tat den pha" to Action.HEADLIGHT_OFF,
            "tat den" to Action.HEADLIGHT_OFF,

            // AMBIENT LIGHT
            "bat den noi that" to Action.LIGHT_ON,
            "tat den noi that" to Action.LIGHT_OFF,

            // TRUNK
            "mo cop" to Action.TRUNK_OPEN,
            "mo cop xe" to Action.TRUNK_OPEN,
            "mo trunk" to Action.TRUNK_OPEN,
            "dong cop" to Action.TRUNK_CLOSE,

            // SEAT HEATER
            "bat suoi ghe" to Action.SEAT_HEATER_ON,
            "bat suoi" to Action.SEAT_HEATER_ON,
            "tat suoi ghe" to Action.SEAT_HEATER_OFF,
            "tat suoi" to Action.SEAT_HEATER_OFF,

            // DEFROSTER
            "bat say kinh" to Action.DEFROSTER_ON,
            "bat say" to Action.DEFROSTER_ON,
            "tat say kinh" to Action.DEFROSTER_OFF,
            "tat say" to Action.DEFROSTER_OFF,

            // MEDIA
            "mo radio" to Action.RADIO_ON,
            "bat radio" to Action.RADIO_ON,
            "tang am luong" to Action.VOLUME_UP,
            "to hon" to Action.VOLUME_UP,
            "giam am luong" to Action.VOLUME_DOWN,
            "nho hon" to Action.VOLUME_DOWN,
            "bai tiep theo" to Action.MEDIA_NEXT,
            "bai ke" to Action.MEDIA_NEXT,
            "next bai" to Action.MEDIA_NEXT,
            "tam dung nhac" to Action.MEDIA_PAUSE,
            "dung nhac" to Action.MEDIA_PAUSE,
            "phat nhac" to Action.MEDIA_PLAY,
            "mo nhac" to Action.MEDIA_PLAY,
            "choi nhac" to Action.MEDIA_PLAY,

            // PHONE
            "nghe may" to Action.CALL_ANSWER,
            "tra loi" to Action.CALL_ANSWER,
            "cup may" to Action.CALL_HANGUP,
            "ket thuc cuoc goi" to Action.CALL_HANGUP,
            "doc tin nhan" to Action.READ_MESSAGE,
            "doc tin nhan cuoi" to Action.READ_MESSAGE,

            // NAVIGATE
            "chi duong" to Action.NAVIGATE,
            "dan duong" to Action.NAVIGATE,
            "duong den" to Action.NAVIGATE,
            "ve nha" to Action.NAVIGATE_HOME,
            "dua toi ve nha" to Action.NAVIGATE_HOME,
            "mo ban do" to Action.MAP_OPEN,
            "xem ban do" to Action.MAP_OPEN,
            "cay xang" to Action.FIND_GAS,
            "tim cay xang gan nhat" to Action.FIND_GAS,
            "nha hang" to Action.FIND_RESTAURANT,
            "quan an" to Action.FIND_RESTAURANT,
            "tim nha hang gan day" to Action.FIND_RESTAURANT,

            // YOUTUBE / OTHER
            "mo youtube" to Action.YOUTUBE,
            "youtube" to Action.YOUTUBE,
            "chao jaecoo" to Action.WAKE_UP,
            "hey jaecoo" to Action.WAKE_UP
        )

        var bestAction = Action.UNKNOWN
        var bestDistance = Int.MAX_VALUE

        for ((keyword, action) in keywords) {
            // Partial match: nếu input chứa keyword (hoặc ngược lại)
            if (input.contains(keyword) || keyword.contains(input)) {
                val lenDiff = Math.abs(input.length - keyword.length)
                if (lenDiff < bestDistance) {
                    bestDistance = lenDiff
                    bestAction = action
                }
            } else {
                val dist = levenshtein(input, keyword)
                if (dist < bestDistance) {
                    bestDistance = dist
                    bestAction = action
                }
            }
        }

        Log.d(TAG, "fuzzyMatch: input='$input', best='$bestAction', distance=$bestDistance")

        return if (bestDistance <= threshold) bestAction else Action.UNKNOWN
    }

    private fun executeVehicleCommand(targetApp: String, buttonText: String) {
        Log.d(TAG, "executeVehicleCommand: targetApp=$targetApp, buttonText='$buttonText'")

        val launched = VoiceAccessibilityService.launchApp(targetApp)
        if (launched) {
            Log.d(TAG, "Launched $targetApp, waiting for node '$buttonText'")
            val node = VoiceAccessibilityService.waitForNode(buttonText, 3000L)
            if (node != null) {
                val clicked = VoiceAccessibilityService.clickNode(node)
                Log.d(TAG, "Clicked node '$buttonText': $clicked")
            } else {
                Log.w(TAG, "Node '$buttonText' not found in $targetApp within 3000ms")
            }
        } else {
            Log.w(TAG, "Could not launch app $targetApp, searching UI in active window directly")
            val node = VoiceAccessibilityService.waitForNode(buttonText, 1500L)
            if (node != null) {
                VoiceAccessibilityService.clickNode(node)
            }
        }
    }

    private fun openNavigation(context: Context, destination: String) {
        try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Navigation opened: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening navigation: ${e.message}", e)
            VoiceAccessibilityService.launchApp("com.google.android.apps.maps")
        }
    }

    private fun openYouTube(context: Context) {
        val success = VoiceAccessibilityService.launchApp("com.google.android.youtube")
        if (!success) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening YouTube: ${e.message}", e)
            }
        }
    }

    private fun extractDestination(rawText: String): String {
        val lower = rawText.lowercase().trim()
        val keywords = listOf("dẫn đường đến", "chỉ đường đến", "bản đồ đến", "đến", "dẫn đường", "chỉ đường")
        for (kw in keywords) {
            if (lower.contains(kw)) {
                val dest = lower.substringAfter(kw).trim()
                if (dest.isNotBlank()) return dest
            }
        }
        return rawText
    }

    private fun removeAccents(src: String): String {
        val temp = Normalizer.normalize(src, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("").lowercase().trim()
            .replace('đ', 'd').replace('Đ', 'd')
    }
}
