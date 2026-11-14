package cn.dkarbon.enroute.data

/**
 * release 变体的「示例数据」占位（见 `src/debug` 里的同名对象）。
 *
 * 为什么要有这个空壳：设置页只判断 [available]，不需要在业务代码里散落
 * `if (BuildConfig.DEBUG)`；而**真正的 fixture 字符串只存在于 debug 源码集**，
 * release 包编译时根本不会包含那段 JSON。
 */
object AcademicSample {

    const val available: Boolean = false

    fun exams(): List<ExamItem> = emptyList()

    fun scores(): List<ScoreItem> = emptyList()
}
