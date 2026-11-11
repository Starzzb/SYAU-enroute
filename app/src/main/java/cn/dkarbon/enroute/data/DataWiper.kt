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

        // ⑥ 调试日志（内存）
        DebugLog.clear()
        done += "调试日志（内存）"

        done += "登录会话（本来就在内存里，随进程失效）"
        return done
    }
}
