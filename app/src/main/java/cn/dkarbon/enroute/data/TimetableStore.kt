package cn.dkarbon.enroute.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 课表的本地仓库 —— **两条数据来源的唯一汇合点**。
 *
 * ```
 *   ① 校内登录 UrpClient.login → fetchSchedule → parseSchedule ─┐
 *                                                              ├→ [save] → filesDir/timetable/timetable.json
 *   ② 导入（SAF 选文件 / adb push 到 /data/local/tmp）──────────┘
 * ```
 *
 * 为什么坚持只落一个文件：两条来源各存一份必然分叉（CET6 的复习记录就栽在这上面）。
 * 无论从哪来，最后都归一成 `karbon-timetable/1` 这一个 JSON。
 *
 * 为什么不用 /sdcard：Android 11+ 分区存储下没有 MANAGE_EXTERNAL_STORAGE 就读不到，
 * 正确姿势只有 SAF，或者让 adb 把文件丢到 `/data/local/tmp`（App 可读）。
 */
class TimetableStore(ctx: Context) {

    private val dir = File(ctx.applicationContext.filesDir, "timetable")
    private val file = File(dir, "timetable.json")

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writeJson = Json { prettyPrint = true; encodeDefaults = true }

    /** 读不到/解析失败一律返回 null，让上层显示「还没有课表」而不是崩 */
    fun load(): Timetable? = runCatching {
        if (!file.isFile) return null
        readJson.decodeFromString(Timetable.serializer(), file.readText())
    }.getOrNull()

    fun save(tt: Timetable): Boolean = runCatching {
        dir.mkdirs()
        file.writeText(writeJson.encodeToString(Timetable.serializer(), tt))
        true
    }.getOrDefault(false)

    fun clear(): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** 解析外部 JSON（导入用）。解析失败抛异常，由上层转成提示文案。 */
    fun parse(text: String): Timetable =
        readJson.decodeFromString(Timetable.serializer(), text)

    /** 已存文件的绝对路径（给 adb pull / 备份用） */
    fun filePath(): String = file.absolutePath

    /**
     * 找 adb 推进来的课表文件。
     *
     * 用法：`adb push karbon-timetable.json /data/local/tmp/enroute-data/`
     * （`/data/local/tmp` 对 App 可读，是分区存储下唯一不折腾的「外部」入口）
     */
    fun findPushedFile(): File? {
        val dirs = listOf(
            File("/data/local/tmp/enroute-data"),
            File("/data/local/tmp"),
        )
        return dirs.asSequence()
            .flatMap { d -> d.listFiles()?.asSequence() ?: emptySequence() }
            .firstOrNull { it.isFile && it.name.contains("timetable", true) && it.name.endsWith(".json") }
    }
}
