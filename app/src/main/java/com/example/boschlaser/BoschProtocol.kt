package com.example.boschlaser

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ProtocolCommand(
    val key: String,
    val label: String,
    val command: Int,
    val data: ByteArray = byteArrayOf(),
    val description: String,
) {
    val payload: ByteArray get() = BoschProtocol.longRequest(command, data)
    override fun toString(): String = label
}

data class Measurement(
    val timestamp: Long,
    val meters: Float,
    val operation: String? = null,
    val accumulatedMeters: Float? = null,
    val raw: ByteArray,
) {
    val millimeters: Float get() = meters * 1000f
    val timeText: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object BoschProtocol {
    const val SERVICE_UUID = "02a6c0d0-0451-4000-b000-fb3210111989"
    const val CHARACTERISTIC_UUID = "02a6c0d1-0451-4000-b000-fb3210111989"

    val enableMeasurements = longRequest(85, byteArrayOf(1, 0))
    val greenMeasureKey = longRequest(86, byteArrayOf(0))
    val continuousPoll = longRequest(64, byteArrayOf(1))
    val continuousStop = longRequest(64, byteArrayOf(2))

    val testCommands = listOf(
        ProtocolCommand("communication_info", "读取通信能力", 0, description = "程序模式、帧能力和最大收发长度"),
        ProtocolCommand("protocol_version", "读取 MT/LRF 协议版本", 4, description = "MT基础协议和LRF项目协议版本"),
        ProtocolCommand("device_name", "读取设备名称", 5, description = "设备报告的名称"),
        ProtocolCommand("device_info", "读取设备详细信息", 6, description = "序列号、软硬件版本和Bosch零件号"),
        ProtocolCommand("echo", "通信回环测试（GLM）", 62, "GLM".toByteArray(), "设备应原样返回GLM"),
        ProtocolCommand("ping", "Ping设备", 63, description = "验证基础通信，不改变设备状态"),
        ProtocolCommand("user_settings", "读取用户设置", 83, description = "读取蜂鸣器、背光和单位等设置"),
        ProtocolCommand("mode_get", "查询当前测量模式", 85, byteArrayOf(0xF1.toByte(), 0), "查询当前机身模式"),
        ProtocolCommand("mode_single", "切换单次距离模式", 85, byteArrayOf(0xF1.toByte(), 1), "切换为单次距离模式"),
        ProtocolCommand("mode_continuous", "切换连续距离模式", 85, byteArrayOf(0xF1.toByte(), 2), "切换为连续距离模式"),
        ProtocolCommand("reference_front", "切换前基准", 85, byteArrayOf(0xF9.toByte(), 0), "以前端为测量基准"),
        ProtocolCommand("reference_tripod", "切换三脚架基准", 85, byteArrayOf(0xF9.toByte(), 1), "以三脚架中心为测量基准"),
        ProtocolCommand("reference_rear", "切换后基准", 85, byteArrayOf(0xF9.toByte(), 2), "以后端为测量基准"),
        ProtocolCommand("laser_on", "打开激光（指针模式）", 65, description = "只打开瞄准激光"),
        ProtocolCommand("laser_off", "关闭激光", 66, description = "关闭瞄准激光"),
        ProtocolCommand("single_measurement", "协议单次测量", 64, byteArrayOf(0), "Command 64单次距离测量"),
        ProtocolCommand("buzzer_on", "蜂鸣器打开", 69, description = "打开蜂鸣器设置"),
        ProtocolCommand("buzzer_off", "蜂鸣器关闭", 70, description = "关闭蜂鸣器设置"),
        ProtocolCommand("lcd_on", "屏幕背光打开", 71, description = "请求打开LCD背光"),
        ProtocolCommand("lcd_off", "屏幕背光关闭", 72, description = "请求关闭LCD背光"),
        ProtocolCommand("laser_status", "读取激光允许状态", 76, description = "读取激光允许引脚，不是实时开关状态"),
    )

    fun crc8(data: ByteArray, endExclusive: Int = data.size): Int {
        var value = 0xAA
        for (index in 0 until endExclusive) {
            val byte = data[index].toInt() and 0xFF
            for (bit in 0 until 8) {
                val feedback = (value ushr 7) xor ((byte ushr (7 - bit)) and 1)
                value = (value shl 1) and 0xFF
                if (feedback != 0) value = value xor 0xA6
            }
        }
        return value
    }

    fun longRequest(command: Int, data: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..255 && data.size <= 254)
        val result = ByteArray(data.size + 4)
        result[0] = 0xC0.toByte()
        result[1] = command.toByte()
        result[2] = data.size.toByte()
        data.copyInto(result, 3)
        result[result.lastIndex] = crc8(result, result.lastIndex).toByte()
        return result
    }

    fun isCommand17(frame: ByteArray): Boolean =
        frame.size == 4 && u(frame[0]) and 0xC0 == 0xC0 && u(frame[1]) == 17 &&
            u(frame[2]) == 0 && crc8(frame, frame.lastIndex) == u(frame.last())

    fun parseLaserState(frame: ByteArray): Boolean? {
        if (frame.size < 6 || u(frame[0]) and 0xC0 != 0xC0 || u(frame[1]) != 85) return null
        val length = u(frame[2])
        if (length < 2 || frame.size != length + 4 || crc8(frame, frame.lastIndex) != u(frame.last())) return null
        return u(frame[4]) and 1 != 0
    }

    fun parseMeasurement(frame: ByteArray): Measurement? {
        if (frame.size < 12 || u(frame[0]) != 0xC0 || u(frame[1]) != 85 || u(frame[2]) != 16) return null
        if (frame.size != 20 || crc8(frame, frame.lastIndex) != u(frame.last())) return null
        val mode = u(frame[3]) ushr 2
        val result = floatAt(frame, 7)
        if (!result.isFinite()) return null
        return when (mode) {
            1 -> if (result in 0f..1000f) Measurement(System.currentTimeMillis(), result, raw = frame) else null
            16, 17 -> {
                val component2 = floatAt(frame, 15)
                if (!component2.isFinite() || component2 !in 0f..1000f || result !in -1000f..1000f) null
                else Measurement(
                    System.currentTimeMillis(), component2,
                    if (mode == 16) "距离加法" else "距离减法", result, frame,
                )
            }
            else -> null
        }
    }

    fun parseContinuousMillimeters(frame: ByteArray): Float? {
        if (frame.size != 7 || u(frame[0]) and 0x1F != 0 || u(frame[1]) != 4) return null
        if (crc8(frame, frame.lastIndex) != u(frame.last())) return null
        val raw = ByteBuffer.wrap(frame, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        return if (raw == 0L) null else raw * 0.05f
    }

    fun describe(frame: ByteArray, command: ProtocolCommand? = null): String {
        if (frame.isEmpty()) return "空数据"
        return when (u(frame[0]) and 0xC0) {
            0x00 -> describeResponse(frame, command)
            0xC0 -> {
                if (frame.size < 4) return "设备请求帧过短"
                val commandNumber = u(frame[1])
                val length = u(frame[2])
                val valid = frame.size == length + 4 && crc8(frame, frame.lastIndex) == u(frame.last())
                val measurement = parseMeasurement(frame)
                buildString {
                    append("设备事件/请求：Command $commandNumber，数据 $length 字节，")
                    append(if (valid) "CRC正确" else "CRC/长度异常")
                    if (measurement != null) {
                        append("；本次=%.1f mm".format(measurement.millimeters))
                        measurement.accumulatedMeters?.let { append("；累计=%.1f mm".format(it * 1000f)) }
                    }
                }
            }
            else -> "未知帧类型：${frame.hex()}"
        }
    }

    private fun describeResponse(frame: ByteArray, command: ProtocolCommand?): String {
        if (frame.size < 3) return "响应帧过短（${frame.hex()}）"
        val length = u(frame[1])
        val valid = frame.size == length + 3 && crc8(frame, frame.lastIndex) == u(frame.last())
        val status = u(frame[0])
        val names = listOf("成功", "通信超时", "模式不支持", "校验错误", "未知命令", "无访问权限", "参数/数据无效", "保留错误")
        val payload = if (frame.size >= 3) frame.copyOfRange(2, frame.lastIndex) else byteArrayOf()
        val base = buildString {
            append("协议响应：${names[status and 7]}")
            if (status and 0x10 != 0) append("；设备未就绪")
            if (status and 0x08 != 0) append("；硬件错误")
            if (status and 0x20 != 0) append("；设备请求发言")
            append(if (valid) "；CRC正确" else "；CRC/长度异常")
        }
        if (payload.isEmpty()) return if (command?.key == "ping") "$base；Ping成功" else base
        val detail = when (command?.key) {
            "device_name" -> "设备名称=${ascii(payload)}"
            "device_info" -> if (payload.size == 29) {
                val software = payload.copyOfRange(10, 13).joinToString(".") { u(it).toString() }
                val hardware = payload.copyOfRange(13, 16).joinToString(".") { u(it).toString() }
                "日期码=${ascii(payload.copyOfRange(0, 4))}，序列号=${littleU32(payload, 4)}，" +
                    "SW修订=${littleU16(payload, 8)}，软件=$software，硬件=$hardware，" +
                    "Bosch零件号=${ascii(payload.copyOfRange(16, 29))}"
            } else null
            "echo" -> "回环文本=${ascii(payload)}"
            "protocol_version" -> if (payload.size == 6) "MT=${payload.take(3).joinToString(".") { u(it).toString() }}，LRF=${payload.drop(3).joinToString(".") { u(it).toString() }}" else null
            "communication_info" -> if (payload.size == 8) {
                val program = mapOf(0 to "Bootloader", 1 to "Flashloader", 2 to "Application")[u(payload[0])] ?: "未知(${u(payload[0])})"
                "程序=$program，帧能力=0x%02X，波特率能力=0x%02X，通信模式=%d，最大RX=%d，最大TX=%d".format(
                    u(payload[1]), u(payload[2]), u(payload[3]), littleU16(payload, 4), littleU16(payload, 6),
                )
            } else null
            "user_settings" -> if (payload.size >= 9) {
                fun switch(value: Byte) = if (u(value) != 0) "开" else "关"
                val backlight = mapOf(0 to "自动", 1 to "开", 2 to "关")[u(payload[4])] ?: u(payload[4]).toString()
                "水平仪=${switch(payload[0])}，自动旋转=${switch(payload[1])}，蜂鸣器=${switch(payload[2])}，" +
                    "连续激光=${switch(payload[3])}，背光=$backlight，角度单位=0x%02X，距离单位=0x%02X，".format(u(payload[5]), u(payload[6])) +
                    "设备配置=0x%02X，历史末索引=%d".format(u(payload[7]), u(payload[8]))
            } else null
            "laser_status" -> if (payload.size == 1) "激光允许状态=${if (u(payload[0]) != 0) "允许" else "关闭"}" else null
            "single_measurement" -> if (payload.size == 4) "距离=%.1f mm".format(littleU32(payload).toFloat() * 0.05f) else null
            "mode_get", "mode_single", "mode_continuous" -> if (payload.size == 16) {
                val value = floatAt(payload, 4).roundToInt()
                "当前模式=${deviceModeName(value)}"
            } else null
            "reference_front", "reference_tripod", "reference_rear" -> if (payload.size == 16) {
                val reference = listOf("前基准", "三脚架基准", "后基准", "未支持基准")[u(payload[0]) and 3]
                "$reference，设备模式=${deviceModeName(u(payload[0]) ushr 2)}"
            } else null
            else -> null
        }
        return "$base；${detail ?: "数据=${payload.hex()}"}"
    }

    private fun floatAt(data: ByteArray, offset: Int): Float =
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float

    private fun littleU16(data: ByteArray, offset: Int): Int = u(data[offset]) or (u(data[offset + 1]) shl 8)

    private fun littleU32(data: ByteArray, offset: Int = 0): Long =
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    private fun deviceModeName(value: Int): String = mapOf(
        0 to "无操作", 1 to "单次距离", 2 to "连续距离", 4 to "面积", 7 to "体积",
        8 to "单次角度", 9 to "连续角度", 10 to "间接高度", 11 to "间接长度",
        13 to "双重间接高度", 15 to "墙面积", 23 to "连续水平仪", 24 to "未公开模式24",
    )[value] ?: "未知($value)"

    private fun ascii(data: ByteArray): String =
        data.takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.US_ASCII).trim().ifEmpty { "（空）" }

    private fun u(value: Byte): Int = value.toInt() and 0xFF
}

class MtFrameAssembler {
    private var buffer = byteArrayOf()
    private var lastChunkAt = 0L

    fun reset() {
        buffer = byteArrayOf()
        lastChunkAt = 0L
    }

    fun feed(chunk: ByteArray): List<ByteArray> {
        val now = System.currentTimeMillis()
        if (buffer.isNotEmpty() && now - lastChunkAt > 750) reset()
        buffer += chunk
        lastChunkAt = now
        val frames = mutableListOf<ByteArray>()
        while (buffer.isNotEmpty()) {
            val kind = buffer[0].toInt() and 0xC0
            val expected = when (kind) {
                0x00 -> if (buffer.size >= 2) (buffer[1].toInt() and 0xFF) + 3 else null
                0xC0 -> if (buffer.size >= 3) (buffer[2].toInt() and 0xFF) + 4 else null
                else -> buffer.size
            } ?: break
            if (buffer.size < expected) break
            frames += buffer.copyOfRange(0, expected)
            buffer = buffer.copyOfRange(expected, buffer.size)
        }
        if (buffer.size > 1024) reset()
        return frames
    }
}

fun ByteArray.hex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
