package cn.dkarbon.enroute.data.faculty

/**
 * 一个学院在官网站群里的位置。
 *
 * ⚠️ **一院一配，不许猜**：17 个院系的师资栏目路径各院自己起名
 * （`szdw` / `szdw1` / `szdw2` / `xkjs/szdw` / `bmgk/jsdw` …），没有统一规则。
 * 表来自票 05 的实测调研：`docs/学院官网-教师公开信息源调研.md` §1。
 *
 * @param college 显示名（`土地与环境学院`）
 * @param host 站群子域（`thxy.syau.edu.cn`）
 * @param columns 师资栏目路径，按顺序试（有的院把「在岗职称」「博导」分成两栏）
 * @param aliases 教务 `departmentName` 的写法（大学校只给 `土环` 这种缩写）
 */
data class FacultySource(
    val college: String,
    val host: String,
    val columns: List<String>,
    val aliases: List<String>,
) {
    /** 栏目页绝对地址（同时用作「原文/自选」入口） */
    fun columnUrl(path: String): String = "https://$host$path"

    val primaryUrl: String get() = columnUrl(columns.first())
}

/**
 * 教务院系 → 官网站点。
 *
 * 只收「能确认栏目路径」的 16 个院部（调研文档实测过的那些）。
 * 教务里还会出现「图书」「全院」这类没有师资栏目的值 —— 那些返回 null，
 * 界面照实说「这个院系没有接入官网信息」，**不许**拿别的学院的页面顶替。
 */
object FacultySources {

    private data class Row(
        val college: String,
        val host: String,
        val columns: List<String>,
        val aliases: List<String>,
    )

    private val ROWS = listOf(
        Row("土地与环境学院", "thxy.syau.edu.cn", listOf("/szdw/jsxl/zgjzc.htm", "/szdw/bssds.htm"), listOf("土环", "土地与环境", "土地与环境学院", "土环学院")),
        Row("农学院", "nxy.syau.edu.cn", listOf("/szdw1.htm"), listOf("农学", "农学院")),
        Row("食品学院", "spxy.syau.edu.cn", listOf("/szdw/zysz.htm", "/szdw/dsfc.htm"), listOf("食品", "食品学院")),
        Row("经济管理学院", "jgxy.syau.edu.cn", listOf("/szdw/bssds.htm"), listOf("经管", "经管学院", "经济管理")),
        Row("信息与电气工程学院", "xdxy.syau.edu.cn", listOf("/szdw.htm"), listOf("信电", "信电学院", "信息与电气")),
        Row("工程学院", "gcxy.syau.edu.cn", listOf("/szdw1/xxmj.htm"), listOf("工程", "工程学院")),
        Row("水利学院", "slxy.syau.edu.cn", listOf("/szdw2.htm"), listOf("水利", "水利学院")),
        Row("园艺学院", "yyxy.syau.edu.cn", listOf("/xkjs/szdw.htm"), listOf("园艺", "园艺学院")),
        Row("植物保护学院", "zbxy.syau.edu.cn", listOf("/xkjs/szll.htm"), listOf("植保", "植保学院", "植物保护")),
        Row("林学院", "lin.syau.edu.cn", listOf("/szdw/zyjs.htm"), listOf("林学", "林学院")),
        Row("生物学院", "swxy.syau.edu.cn", listOf("/szdw/zzjs.htm"), listOf("生物", "生物学院", "生命科学")),
        Row("理学院", "lxy.syau.edu.cn", listOf("/szdw1.htm"), listOf("理学", "理学院")),
        Row("畜牧兽医学院", "myxy.syau.edu.cn", listOf("/szdw.htm"), listOf("牧医", "畜牧兽医", "牧医学院")),
        Row("马克思主义学院", "marx.syau.edu.cn", listOf("/szll/sxddyfzjys.htm"), listOf("马院", "马克思", "马克思主义学院")),
        Row("体育部", "tyb.syau.edu.cn", listOf("/bmgk/jsdw.htm"), listOf("体育", "体育部")),
        Row("外语部", "wyb.syau.edu.cn", listOf("/szdw.htm"), listOf("外语", "外语部", "外国语")),
    )

    private val SOURCES = ROWS.map { FacultySource(it.college, it.host, it.columns, it.aliases) }

    /** 键 = 教务 departmentName 的各种写法（含全名），值 = 站点 */
    private val BY_ALIAS: Map<String, FacultySource> = buildMap {
        SOURCES.forEach { s -> s.aliases.forEach { put(it, s) } }
    }

    val all: List<FacultySource> get() = SOURCES

    /**
     * 教务院系（`土环` / `土地与环境学院` …）→ 官网来源；没有师资栏目的返回 null。
     * 匹配顺序：精确别名 → 去掉「学院/部/系」后缀 → 互相包含（宽松兜底）。
     */
    fun of(departmentName: String): FacultySource? {
        val raw = departmentName.trim().replace(" ", "")
        if (raw.isBlank()) return null
        BY_ALIAS[raw]?.let { return it }
        val stripped = raw.removeSuffix("学院").removeSuffix("部").removeSuffix("系")
        BY_ALIAS[stripped]?.let { return it }
        return SOURCES.firstOrNull { s ->
            s.aliases.any { raw.contains(it) || it.contains(raw) }
        }
    }
}
