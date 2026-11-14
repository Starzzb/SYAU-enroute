package cn.dkarbon.enroute.data

/**
 * **示例数据（仅调试包）** —— 用真机抓到的 payload 做 fixture（2026-11-13）。
 *
 * ## 为什么需要它
 * 考试/成绩接口只在**校园网 + URP 登录态**下可达，而登录态是仅内存的（冷启动即失效）。
 * 于是「不在校园网 / 没登录」时，这两页永远只能看到失败态 —— 版式、时间轴、
 * 学期分组这些**根本没法在真机上自查**。这个入口就是给 QA 用的：一键把两页灌满，
 * 看版式、看层级、看折叠，不用连校园网。
 *
 * ## 为什么分 `src/debug` / `src/release` 两份
 * fixture 里是真实响应原文（含课程号、课序号这类能反推身份的信息）。
 * 放 `src/debug` 打包时 release 变体根本不会编译它 —— 交付包里既没有数据也没有按钮，
 * 而设置页只靠 [available] 决定显不显示入口，不需要 `if (BuildConfig.DEBUG)` 散落各处。
 *
 * ## 数据是「真的」，但只有样本
 * 考试 4 条 = `urp_exams.json` 全量（2026-11-15 / 12-15 / 12-28 / 12-29 四场）。
 * 成绩只有 **2 条**：真机那次抓到 55 条，但原文含学号相关字段，按项目红线
 * （`docs/URP-学生端接口清单.md`「入仓前必须脱敏」）不入仓，只留这两条已脱敏的。
 * 所以示例成绩页只能演示**一个学期**——学期分组的多组排序由单测覆盖，不靠示例。
 */
object AcademicSample {

    /** 设置页据此决定是否显示「载入示例数据」入口 */
    const val available: Boolean = true

    const val EXAMS_JSON: String = """
[{"title":"形势与政策Ⅴ\n10:00-11:40\n主校\n线上考场\n线上虚拟考场1\n","id":"0","start":"2026-11-15 10:00","end":"2026-11-15 11:40","color":"#3a87ad"},
{"title":"不动产估价\n08:00-09:40\n主校\n第二教学楼\n201\n","id":"1","start":"2026-12-15 08:00","end":"2026-12-15 09:40","color":"#3a87ad"},
{"title":"国土空间规划\n08:00-09:40\n主校\n第二教学楼\n402\n","id":"2","start":"2026-12-28 08:00","end":"2026-12-28 09:40","color":"#3a87ad"},
{"title":"地球表层系统概论\n08:00-09:40\n主校\n第二教学楼\n401\n","id":"3","start":"2026-12-29 08:00","end":"2026-12-29 09:40","color":"#3a87ad"}]
    """

    const val SCORES_JSON: String = """
{"list":{"pageSize":300,"pageNum":1,"pageContext":{"totalCount":2},"records":[
  {"ZXJXJHH":"2024-2025-1-1","ZXJXJHM":"2024-2025学年秋","KCH":"901010216","KXH":"02","KSSJ":"20250101",
   "LRZTDM":"05","KCSXDM":"002","KSLXDM":"","CJLRFSDM":"001","KCCJ":"73","DJCJ":"-75","WTGYYDM":"",
   "KCM":"生态学基础","YWKCM":"Fundamentals of Ecology","XF":2,"XS":32,"KCSXMC":"限修课",
   "KCLXMC":"","DJM":"中","WTGYYSM":"","RN":1},
  {"ZXJXJHH":"2025-2026-2-1","ZXJXJHM":"2025-2026学年春","KCH":"901040337","KXH":"01","KSSJ":"20260701",
   "LRZTDM":"05","KCSXDM":"002","KSLXDM":"","CJLRFSDM":"001","KCCJ":"90","DJCJ":"-95","WTGYYDM":"",
   "KCM":"应用遥感技术","YWKCM":"Application of Remote Sensing Technology","XF":2,"XS":32,"KCSXMC":"限修课",
   "KCLXMC":"","DJM":"优","WTGYYSM":"","RN":2}]}}
    """

    fun exams(): List<ExamItem> = AcademicApi.parseExamsJson(EXAMS_JSON)

    fun scores(): List<ScoreItem> = AcademicApi.parseScoresJson(SCORES_JSON)
}
