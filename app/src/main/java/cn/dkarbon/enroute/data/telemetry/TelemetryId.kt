package cn.dkarbon.enroute.data.telemetry

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 事件里的几个「不是身份的身份」。
 *
 * ## `i` 安装标识：跨天可拼
 *
 * `i = SHA-256(installId).take(16)`，64 bit。`installId` 是 128 位随机数，只存在本机 Prefs 里，
 * 一次都不出门；出门的是它的哈希前 16 位 —— 反推不回 installId，两台设备也不会撞。
 *
 * 为什么要跨天可拼：活跃统计需要「同一台设备的连续多天」，否则连「一共多少人用过」都答不出来。
 * 早先的设计是每天重算一次的 `d`，昨天的 d 和今天的 d 没有数学关系，服务端没法把两条记录
 * 归到同一台设备上，于是只能数「今天有多少个 d」—— 留存、用龄、总用户数全都没法算。
 *
 * 隐私边界没有变：不传学号、姓名、课程名、搜索词、设备硬件标识。`i` 与本机安装绑定，
 * 卸载重装即换新身份，服务端拿不到任何能回指到人的东西。
 *
 * ## `d` 当日标识：历史数据还在按天读
 *
 * `d = SHA-256(installId + ":" + yyyy-MM-dd).take(8)`，继续发，服务端按需选。
 * 8 位十六进制只有 32 bit，单靠它数人会在几万设备量级上碰撞，所以新统计一律走 `i`。
 *
 * ## `s` 会话标识
 *
 * 进程内随机 4 位十六进制，只用于把同一次使用里的事件串起来。它跟着进程死，
 * 不进 Prefs、不跨重启 —— 重启就是新的 `s`。
 */
object TelemetryId {

    /** 安装标识：`SHA-256(installId)` 的前 16 位十六进制（64 bit） */
    fun install(installId: String): String {
        if (installId.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(installId.toByteArray(Charsets.UTF_8))
        return hex(digest).take(16)
    }

    /**
     * 当日标识：`SHA-256(installId + ":" + yyyy-MM-dd)` 的前 8 位十六进制。
     *
     * @param today 由调用方给（测试要能换日期，别在这里读时钟）
     */
    fun daily(installId: String, today: LocalDate): String {
        if (installId.isBlank()) return ""
        val material = installId + ":" + today.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return hex(digest).take(8)
    }

    /** 同上，接受毫秒时间戳（落盘时用） */
    fun daily(installId: String, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        daily(installId, Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    /** 会话标识：4 位十六进制，进程内一次 */
    fun newSession(): String {
        val bytes = ByteArray(2)
        java.security.SecureRandom().nextBytes(bytes)
        return hex(bytes)
    }

    /** 安装标识的原像：128 位随机，本地生成、永不上传 */
    fun newInstallId(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return hex(bytes)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
