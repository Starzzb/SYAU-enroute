package cn.dkarbon.enroute.ui

import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.HistorySemesters
import cn.dkarbon.enroute.data.ProbeResult
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.Timetable
import cn.dkarbon.enroute.data.UrpHomeApi.BuildingOption
import cn.dkarbon.enroute.data.UrpHomeApi.CampusOption
import cn.dkarbon.enroute.data.UrpHomeApi.ClassroomInfo
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.FreeRooms
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule
import cn.dkarbon.enroute.data.curriculum.CurriculumPlan
import cn.dkarbon.enroute.data.curriculum.PlanIndex
import cn.dkarbon.enroute.data.curriculum.PlanIndexEntry
import cn.dkarbon.enroute.data.curriculum.Syllabus
import cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry
import cn.dkarbon.enroute.data.faculty.FacultyInfo
import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapPackage

/**
 * 界面状态（单一来源）。
 *
 * 名字特意保持 `AppState` 而不是 `UiState`：这个 App 只有课表一件事，
 * 考试/成绩是它的延伸，全部往这里加字段，不要搞多个 state 流互相打架。
 */
data class AppState(
    // ── 课表 ──
    val timetable: Timetable? = null,
    val timetableLoading: Boolean = false,
    /** 导入/抓取的结果提示（显示在导入面板里，不弹窗） */
    val timetableMsg: String = "",
    /** 当前查看第几周；-1 = 用学期起止日估算 */
    val timetableWeek: Int = -1,

    // ── 会话（cookie 加密存档，冷启动恢复并验活；见 data/SessionStore）──
    /**
     * 登录态三态（票 04）：未登录 / 已登录（会话有效）/ 已登录但会话已过期。
     *
     * 以前这里是布尔 `loggedIn`，分不清「从没登录」和「登过但过期」，
     * 两种情况在界面上都是「暂缺」，学生只能靠重登碰运气。
     */
    val sessionState: SessionState = SessionState.None,
    /**
     * 恢复/验活的一句说明（空串 = 没什么要说的）。
     * 典型取值见 [cn.dkarbon.enroute.ui.SessionText]，只做展示，不参与任何判断。
     */
    val sessionCheckMsg: String = "",
    /** 登录用户姓名（从门户页「欢迎您，XXX」解析，可能为空） */
    val userName: String = "",

    // ── 静默续期（票 14）──
    /** 是否开启「保持登录（自动续期）」—— 默认关闭 */
    val credEnabled: Boolean = false,
    /** 设置页那一行状态（**不含任何凭据内容**） */
    val credStatus: String = "",
    /** 首次登录成功后的一次性说明卡 */
    val credPromptVisible: Boolean = false,
    /** 自动续期正在进行（SessionText 的「续期中…」） */
    val reloginRunning: Boolean = false,
    /** 续期结果一句话（成功不留痕，失败要说人话） */
    val reloginMsg: String = "",

    // ── 考试 ──
    val exams: List<ExamItem> = emptyList(),
    val examsLoading: Boolean = false,
    val examsMsg: String = "",
    /** 考试数据的上次成功抓取时刻（epoch ms；0 = 从没抓过，页顶据此标「数据取自 …」） */
    val examsFetchedAt: Long = 0L,

    // ── 成绩 ──
    val scores: List<ScoreItem> = emptyList(),
    val scoresLoading: Boolean = false,
    val scoresMsg: String = "",
    /** 成绩明细的上次成功抓取时刻（epoch ms；0 = 从没抓过） */
    val scoresFetchedAt: Long = 0L,

    // ── 诊断（仅 debug 面板用）──
    val probe: List<ProbeResult> = emptyList(),
    val probing: Boolean = false,
    val probeMsg: String = "",

    // ── 学业主页（2026-11-13 接入的四支接口）──
    /** 学籍身份（学号/姓名/院系/专业/层次…） */
    val profile: StudentProfile = StudentProfile(),
    /** 培养方案学分进度（学校端算好的需修/已得/门数） */
    val credit: CreditProgress = CreditProgress(),
    /** 成绩统计（按学校绩点加权 + 通过门数） */
    val scoreStats: ScoreStats = ScoreStats(),
    val homeLoading: Boolean = false,
    val homeMsg: String = "",
    /**
     * 「更新于：身份 3 天前 · 绩点 2 小时前」这类小字（票 24 的三档缓存）。
     *
     * 空串 = 本机还没有任何教务缓存（**不显示假时间**）。
     * 用途：显示的是缓存时，用户能一眼看出「这是多会儿的数据」——
     * 这正是「失败不覆盖旧值」那条纪律的配套：给旧数据，就必须标出它的年纪。
     */
    val cacheUpdatedLabel: String = "",
    /** 学分进度 + 绩点（每日档）的上次成功抓取时刻（epoch ms；0 = 从没抓过，页顶「数据取自 …」用） */
    val homeDailyFetchedAt: Long = 0L,

    // ── 空闲教室 ──
    val campuses: List<CampusOption> = emptyList(),
    val buildings: List<BuildingOption> = emptyList(),
    val freeRooms: FreeRooms = FreeRooms(),
    val roomsLoading: Boolean = false,
    val roomsMsg: String = "",
    /** 当前选中的校区号（空 = 还没选/没拿到列表） */
    val roomsCampusNo: String = "",
    /** 当前选中的教学楼号 */
    val roomsBuildingNo: String = "",
    /** 校区/教学楼列表的上次成功抓取时刻（epoch ms；0 = 从没抓过，页顶据此标「数据取自 …」） */
    val roomsListsFetchedAt: Long = 0L,
    /** 当前查看的节次 1..12；0 表示"按现在时间自动取" */
    val roomsSession: Int = 0,
    /** 0 今天 / 1 明天 / 2 后天 */
    val roomsDayPlus: Int = 0,

    // ── 教室详情（票 03：从空闲教室下钻）──
    /** 打开中的教室房号；空串 = 浮层关着 */
    val roomDetailRoom: String = "",
    val roomDetailLoading: Boolean = false,
    /** 检索到的教室档案；null = 没查到（原因见 roomDetailMsg） */
    val roomDetailInfo: ClassroomInfo? = null,
    /** 一句话状态：查询中 / 没查到（含覆盖面）/ 会话失效… */
    val roomDetailMsg: String = "",
    /** 服务器说这个学期一共有多少间教室（交代检索覆盖面） */
    val roomDetailTotal: Int = 0,
    /** 这次实际翻了多少条 */
    val roomDetailScanned: Int = 0,

    // ── 查教师 ──
    val teachers: List<TeacherHit> = emptyList(),
    val teacherQuery: String = "",
    val teacherLoading: Boolean = false,
    val teacherMsg: String = "",

    // ── 教师详情（票 08：新开页，课表 + 官网公开资料）──
    /** 打开中的教师工号；空串 = 没打开详情页 */
    val teacherDetailNo: String = "",
    val teacherDetailName: String = "",
    val teacherDetailDept: String = "",
    val teacherDetailTitle: String = "",
    val teacherDetailSex: String = "",
    /** 详情页对应的学期号（`2026-2027-1-1`），显示与回执都用它 */
    val teacherDetailTerm: String = "",
    val teacherScheduleLoading: Boolean = false,
    /** TA 这学期的课表（空 = 没排课或没拿到，差别看 [teacherScheduleMsg]） */
    val teacherSchedule: TeacherSchedule = TeacherSchedule(),
    /** 课表块状态：`""` 正常 / 「这位老师本学期没排课」/ 登录态或网络提示 */
    val teacherScheduleMsg: String = "",
    val facultyLoading: Boolean = false,
    /** 官网公开资料（null = 没抓到，原因看 [facultyMsg]） */
    val facultyInfo: FacultyInfo? = null,
    val facultyMsg: String = "",
    /** 展示的是旧缓存（刷新失败，要标日期） */
    val facultyStale: Boolean = false,
    /** 官网同名候选原文地址（不替用户猜） */
    val facultyCandidates: List<String> = emptyList(),

    // ── 培养方案（票 09：内置资产，离线）──
    /** 资产索引（年份 → 专业条目） */
    val planIndex: PlanIndex? = null,
    /** 当前方案版本年份（`2024` / `2025`） */
    val planYear: String = "",
    /** 当前专业名 */
    val planMajor: String = "",
    /** 同一年同一专业可能有多条（普通班/实验班），这里是候选 */
    val planEntries: List<PlanIndexEntry> = emptyList(),
    /** 当前选中的条目（决定读哪个资产文件） */
    val planEntryId: String = "",
    /** 解析好的方案；null = 还没选/没读到 */
    val plan: CurriculumPlan? = null,
    val planLoading: Boolean = false,
    /** 一句话状态：加载中 / 没读到 / 定位不到专业请手动选 */
    val planMsg: String = "",

    // ── 课程大纲（票 10）──
    /** 大纲索引（2066 门，一次读入内存） */
    val syllabusIndex: List<SyllabusIndexEntry> = emptyList(),
    /** 搜索框内容 */
    val syllabusQuery: String = "",
    /** 类型筛选（空 = 全部；理论课/实验课/实践课） */
    val syllabusType: String = "",
    /** 大纲按学院筛（空 = 全部 14 个学院） */
    /**
     * 大纲已选的学院（**多选**，空集 = 全部 14 个学院）。
     *
     * 2026-11-21 主人指定多选：想同时看「农学院 + 园艺学院」的课时不用来回切。
     */
    val syllabusColleges: Set<String> = emptySet(),
    /** 过滤后的列表 */
    val syllabusList: List<SyllabusIndexEntry> = emptyList(),
    val syllabusIndexLoading: Boolean = false,
    /** 索引状态：没读到资产 / 正常为空 */
    val syllabusIndexMsg: String = "",
    /** 打开中的大纲（索引 id）；空 = 浮层关着 */
    val syllabusOpenId: String = "",
    val syllabusDetail: Syllabus? = null,
    val syllabusDetailLoading: Boolean = false,
    /** 大纲浮层的一句话：没有这门课 / 命中多条候选 / 读失败 */
    val syllabusDetailMsg: String = "",
    /** 命中多条时的候选 */
    val syllabusCandidates: List<SyllabusIndexEntry> = emptyList(),

    // ── 历年课表（票 11）──
    /** 服务器给的学期候选（最近 5 个，含当前；不写死） */
    val historyTerms: List<HistorySemesters.Option> = emptyList(),
    /** 当前查看的学期号（`2024-2025-1-1`） */
    val historyPlanCode: String = "",
    val historyTimetable: Timetable? = null,
    val historyLoading: Boolean = false,
    /** 一句话状态：没登录 / 该学期没排课 / 网络失败 */
    val historyMsg: String = "",
    /** 这次是从本地缓存读出来的 */
    val historyFromCache: Boolean = false,
    /** 缓存抓取时间（ISO），界面标「抓于 MM-DD」 */
    val historyFetchedAt: String = "",

    // ── 校园地图（票 12）──
    /** 读进来的地图包（外部文件包优先，坏了回退内置） */
    val mapPackage: MapPackage? = null,
    val mapLoading: Boolean = false,
    /** 加载状态/回退提示（external 坏 → 回退内置时必须显示） */
    val mapMsg: String = "",
    /** 用户标注（edits.json 的内存镜像；改一次存一次） */
    val mapEdits: MapEdits = MapEdits(),
    /** 当前选中的要素 id；空 = 没选 */
    val mapSelectedId: String = "",
    /** 换图后在新版里找不到的标注 id（绝不静默丢弃） */
    val mapOrphans: List<String> = emptyList(),
    /** 导出结果的一句话 */
    val mapExportMsg: String = "",
    val mapExportGeoJson: String = "",
    val mapExportCsv: String = "",
    val mapExportGeoName: String = "",
    val mapExportCsvName: String = "",

    // ── 设置 ──
    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    /** 视觉风格：0 硬朗农业 / 2 校报头版（1 学院纸本已下线，仅做老数据回落）；与 themeMode 正交 */
    val styleSet: Int = 0,

    // ── 课表卡片（m39）──
    // 把 m34 拔掉入口后仍留在代码里的那两套课表主体接回设置页。
    // 全部默认 = 今天的行为，老用户升级视觉零变化。

    /** 版式：0 = 侵蚀纸卡 / 1 = 账页格 */
    val ttCardLayout: Int = 0,
    /** 底色来源：0 = 四色系 / 1 = 单墨 / 2 = 无色 */
    val ttCardTint: Int = 0,
    val ttCardSpine: Boolean = true,
    val ttCardErode: Boolean = true,
    val ttCardGhost: Boolean = true,
    val ttCardFood: Boolean = true,
    val ttCardNowRing: Boolean = true,
    // ── 课表槽位化（m41）──
    /** 选中的预设下标 */
    val ttPreset: Int = 0,
    /** 覆盖位：-1 跟随预设（顺序同 TtOverrides） */
    val ttOvShape: Int = -1,
    val ttOvDecor: Int = -1,
    val ttOvMeta: Int = -1,
    val ttOvBadge: Int = -1,
    val ttOvErosion: Int = -1,
    val ttOvMotion: Int = -1,
    val ttOvTone: Int = -1,
    val ttOvHeader: Int = -1,
    val ttOvGrid: Int = -1,
    val ttOvBackdrop: Int = -1,
    val ttOvBgMotion: Int = -1,
    /** 取色来源：0 固定农业色板 / 1 跟随壁纸取色（仅 Android 12+ 生效）/ 2 自定义配色 */
    val colorSource: Int = 0,
    /** 自定义配色的四个槽（票 25）：浅色/深色各自独立，0 = 该槽未设置 */
    val customPalette: cn.dkarbon.enroute.ui.theme.CustomPalette =
        cn.dkarbon.enroute.ui.theme.CustomPalette.NONE,

    // ── 调试日志（票 20）──
    /** 是否保存日志到本机（开关状态） */
    val debugFileEnabled: Boolean = true,
    /** 「已占用 412KB · 保留 7 天」 */
    val debugFileUsage: String = "",

    // ── 自定义课程（票 19）──
    /** 本机存的自定义课程（只存本机，不上传） */
    val customCourses: List<cn.dkarbon.enroute.data.custom.CustomCourse> = emptyList(),
    /** 正在编辑的那门（null = 不在编辑页） */
    val customEditing: cn.dkarbon.enroute.data.custom.CustomCourse? = null,
    /** 编辑页是否打开 */
    val customEditorOpen: Boolean = false,
    /** 编辑页/列表的一句话提示 */
    val customMsg: String = "",
    /** 上课提醒总开关（默认开） */
    val remindersEnabled: Boolean = true,

    /** 「清除所有数据」的结果说明（只在设置页显示一次） */
    val wipeMsg: String = "",

    /** 一次性提示（toast），消费后清空 */
    val toast: String? = null,
) {
    /**
     * 有可用的会话 = 已登录且没被判过期。
     *
     * 保留这个名字是为了不动考试/成绩/空闲教室等已在用的调用点；
     * 新代码请直接看 [sessionState]，别再用布尔（那就是票 04 要消灭的歧义）。
     */
    val loggedIn: Boolean get() = sessionState == SessionState.Active
}

