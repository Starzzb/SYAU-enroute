package cn.dkarbon.enroute.data

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import cn.dkarbon.enroute.ui.timetable.TermCalendar

/**
 * 一键抹掉本机**所有** App 数据 —— 用于「我想从头再登录一次」。
 *
 * 为什么需要它：登录态、课表、周次校准、提醒任务、调试日志分散在好几个地方，
 * 只清课表会出现「课表空了但周次还记着上次的」「课表清空了提醒还在响」这类鬼问题。
 * 所以要么全清、要么不清 —— 没有中间态。
 *
 * ⚠️ 不删的东西：APK 本身、系统权限授权（通知权限仍在）、剪贴板。
 *
 * 票 04 起多清一样：**加密的会话存档 + 它的 Keystore 密钥**（见 [SessionStore.forget]）——
 * 否则「清除所有数据」之后下次冷启动还会把登录态悄悄恢复回来。
 */
object DataWiper {

    /** 清完返回一份「清了什么」的清单，给界面显示 */
    fun wipeAll(ctx: Context): List<String> {
        val app = ctx.applicationContext
        val done = mutableListOf<String>()

        // ① 课表 JSON（filesDir/timetable 整个目录）
        runCatching {
            val dir = java.io.File(app.filesDir, "timetable")
            if (dir.exists() && dir.deleteRecursively()) {
                done += "课表文件 timetable.json"
            } else {
                done += "课表文件（本来就空）"
            }
        }.onFailure { Log.w("DataWiper", "删课表失败: ${it.message}") }

        // ② 偏好：周次、主题、提醒开关（文件名 "enroute"，见 Prefs）
        runCatching {
            Prefs(app).clearAll()
            done += "偏好设置（周次 / 主题 / 提醒开关）"
        }.onFailure { Log.w("DataWiper", "清偏好失败: ${it.message}") }

        // ②b 登录会话加密存档 + Keystore 密钥（票 04）。
        //     只清 Prefs 会留下孤儿密钥；连 key 一起删（crypto-shred），
        //     万一密文残留在备份/快照里也读不出任何东西。
        runCatching {
            SessionStore(app).forget()
            done += "登录会话（加密存档 + Keystore 密钥）"
        }.onFailure { Log.w("DataWiper", "清会话存档失败: ${it.message}") }

        // ③ 学期周次校准（独立 sp 文件 "term-calendar"）
        runCatching {
            TermCalendar(app).clear()
            done += "学期周次校准"
        }.onFailure { Log.w("DataWiper", "清周次失败: ${it.message}") }

        // ④ 所有 WorkManager 任务（提醒 + 每日重排）
        runCatching {
            WorkManager.getInstance(app).cancelAllWork()
            done += "已排的上课提醒任务"
        }.onFailure { Log.w("DataWiper", "清任务失败: ${it.message}") }

        // ⑤ 资讯：列表 + 已抓的正文（正文是永久缓存，全清也得清掉）
        runCatching {
            val feedDir = java.io.File(app.filesDir, "feed")
            if (feedDir.exists() && feedDir.deleteRecursively()) done += "资讯列表与已缓存正文" else done += "资讯（本来就空）"
        }.onFailure { Log.w("DataWiper", "清资讯失败: ${it.message}") }

        // ⑤b 教师官网公开资料缓存（票 08，键=工号；TTL 30 天，与 feed 分开存）
        runCatching {
            val before = cn.dkarbon.enroute.data.faculty.FacultyStore(app).count()
            cn.dkarbon.enroute.data.faculty.FacultyStore(app).clear()
            done += if (before > 0) "教师官网资料缓存（$before 位）" else "教师官网资料（本来就空）"
        }.onFailure { Log.w("DataWiper", "清教师官网缓存失败: ${it.message}") }

        // ⑤c 历年课表缓存（票 11）：filesDir/timetable/history/<planCode>.json
        runCatching {
            val store = cn.dkarbon.enroute.data.HistoryTimetableStore(app)
            val histBefore = store.count()
            store.clear()
            done += if (histBefore > 0) "历年课表缓存（$histBefore 个学期）" else "历年课表（本来就空）"
        }.onFailure { Log.w("DataWiper", "清历年课表失败: ${it.message}") }

        // ⑤d 校园地图标注与导出（票 12）：filesDir/map/edits.json + export/
        runCatching {
            val store = cn.dkarbon.enroute.data.map.MapEditsStore(app)
            val mapBefore = store.count()
            store.clear()
            done += if (mapBefore > 0) "校园地图标注（$mapBefore 个面）与导出文件" else "校园地图标注（本来就空）"
        }.onFailure { Log.w("DataWiper", "清地图标注失败: ${it.message}") }

        // ⑤e 静默续期凭据（票 14）：密文 + Keystore 别名一起销毁（crypto-shredding）
        runCatching {
            val cred = cn.dkarbon.enroute.data.CredentialStore(app)
            val had = cred.isEnabled() || cred.savedAt() > 0L
            cred.forget()
            done += if (had) "本机保存的学号密码（密文与设备密钥）" else "本机保存的密码（本来就没有）"
        }.onFailure { Log.w("DataWiper", "清凭据失败: ${it.javaClass.simpleName}") }

        // ⑤f 教务数据三档缓存（票 24）：filesDir/academic/cache.json
        //     身份是「永久档」，只有这里能把它作废 —— 清完再打开就是全新的空缓存，
        //     下一次登录/联网会重新抓一遍。
        runCatching {
            val store = AcademicCacheStore(app)
            val hadBytes = store.sizeBytes()
            store.clear()
            done += if (hadBytes > 0) {
                "教务缓存（身份/成绩/考试，${hadBytes / 1024}KB）"
            } else {
                "教务缓存（本来就空）"
            }
        }.onFailure { Log.w("DataWiper", "清教务缓存失败: ${it.message}") }

        // ⑥ 调试日志（内存 + 落盘）
        DebugLog.clear()
        done += "调试日志（内存）"

        // ⑦ 目录级兜底清扫（2026-11-27 补）。
        //    上面每个 Store 只清自己认识的文件，于是漏了三类没人认领的东西：
        //    遥测队列与退避文件（telemetry/）、下载中的安装包（updates/）、落盘调试日志（debug/）。
        //    「清除所有数据」若留下它们，用户看到的是「清了但没全清」—— 比不清更糟。
        //    所以最后一律按**目录**扫，不依赖任何 Store 记得自己有什么。
        val leftovers = listOf(
            "telemetry" to "遥测队列与重试状态",
            "updates" to "下载中的安装包",
            "debug" to "落盘调试日志",
            "map" to "地图数据与待提交补丁",
            "faculty" to "教师官网资料与图片",
        )
        for ((name, label) in leftovers) {
            runCatching {
                val dir = java.io.File(app.filesDir, name)
                if (dir.exists()) {
                    val ok = dir.deleteRecursively()
                    done += if (ok) label else "$label（删除未完成）"
                }
            }.onFailure { Log.w("DataWiper", "清扫 $name 失败: ${it.message}") }
        }

        // ⑧ 残留自检：清完再扫一遍 filesDir，把「还有东西」如实报出来。
        //    这里不假装成功 —— 有残留就让界面看得见，下一次修才有据可查。
        runCatching {
            val rest = app.filesDir.listFiles()?.filter { it.exists() }?.map { it.name }.orEmpty()
            done += if (rest.isEmpty()) "残留检查：filesDir 已空" else "残留检查：仍有 ${rest.joinToString("、")}"
        }.onFailure { Log.w("DataWiper", "残留检查失败: ${it.message}") }

        return done
    }
}
