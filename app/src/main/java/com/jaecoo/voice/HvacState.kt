package com.jaecoo.voice

object HvacState {
    @Volatile var isPowerOn = false
    @Volatile var isAcOn = false
    @Volatile var isAutoOn = false
    @Volatile var isRecircOn = false       // true = gió trong, false = gió ngoài
    @Volatile var isSyncOn = false
    @Volatile var isDefrostFront = false
    @Volatile var isDefrostRear = false
    @Volatile var isIonOn = false
    @Volatile var fanSpeed = 3             // 1-7
    @Volatile var tempDriver = 25f         // 16-32
    @Volatile var tempPassenger = 25f      // 16-32

    fun reset() {
        isPowerOn = false; isAcOn = false; isAutoOn = false
        isRecircOn = false; isSyncOn = false
        isDefrostFront = false; isDefrostRear = false; isIonOn = false
        fanSpeed = 3; tempDriver = 25f; tempPassenger = 25f
    }

    fun describe(): String = """
        HVAC State:
        ├─ Power: ${if (isPowerOn) "ON" else "OFF"}
        ├─ AC: ${if (isAcOn) "ON" else "OFF"}
        ├─ Auto: ${if (isAutoOn) "ON" else "OFF"}
        ├─ Quạt: $fanSpeed/7
        ├─ Nhiệt tài xế: ${tempDriver.toInt()}°C
        ├─ Nhiệt phụ: ${tempPassenger.toInt()}°C
        ├─ Gió: ${if (isRecircOn) "TRONG (tuần hoàn)" else "NGOÀI (fresh air)"}
        ├─ Sync: ${if (isSyncOn) "ON" else "OFF"}
        ├─ Sấy kính trước: ${if (isDefrostFront) "ON" else "OFF"}
        ├─ Sấy kính sau: ${if (isDefrostRear) "ON" else "OFF"}
        └─ ION: ${if (isIonOn) "ON" else "OFF"}
    """.trimIndent()
}
