package cn.dkarbon.enroute.data.custom

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 自定义课程的本地存档（票 19）：`filesDir/timetable/custom.json`。
 *
 * 与 `timetable.json`（教务数据）**分文件**：这样「重拉课表覆盖教务数据」时自定义条目天然不受影响
 * （票面硬规矩 2：重拉不清自定义）。也绝不进 `map/patches`、不上传。
 */
@Serializable
data class CustomCourseFile(
    val schema: Int = SCHEMA,
    val courses: List<CustomCourse> = emptyList(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

class CustomCourseStore(ctx: Context) {

    private val dir = File(ctx.applicationContext.filesDir, "timetable")
    private val file = File(dir, "custom.json")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "type"   // sealed CustomRule 的判别字段
    }

    fun load(): List<CustomCourse> = runCatching {
        val text = cn.dkarbon.enroute.data.SafeWrite.read(file) ?: return emptyList()
        json.decodeFromString(CustomCourseFile.serializer(), text).courses
    }.getOrDefault(emptyList())

    /** 原子写：统一走 [cn.dkarbon.enroute.data.SafeWrite]（同目录 tmp + fsync + 改名 + 回滚） */
    fun save(courses: List<CustomCourse>): Boolean =
        cn.dkarbon.enroute.data.SafeWrite.text(
            file,
            json.encodeToString(CustomCourseFile.serializer(), CustomCourseFile(courses = courses)),
        )

    /** 纯编解码（给单测用，不碰文件） */
    fun encode(courses: List<CustomCourse>): String =
        json.encodeToString(CustomCourseFile.serializer(), CustomCourseFile(courses = courses))

    fun decode(text: String): List<CustomCourse>? = runCatching {
        json.decodeFromString(CustomCourseFile.serializer(), text).courses
    }.getOrNull()

    fun clear(): Boolean = runCatching { file.delete() }.getOrDefault(false)

    fun path(): String = file.absolutePath
}
