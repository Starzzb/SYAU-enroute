package cn.dkarbon.enroute.ui.timetable

import androidx.compose.runtime.Immutable

/**
 * 课表格子的「槽位」取值（m41）。
 *
 * 一个格子 = 七条正交轴叠出来，换一条就换一种长相，不用换组件：
 * shape × decor × meta × badge × erosion × motion × tone。
 * 布局侧另有 header / grid / backdrop / bgMotion 四条轴。
 *
 * 全部用 Int 存：设置页的 SegRow 直接按序号选中，
 * 存进 SharedPreferences 也不用序列化。**序号就是契约，改动要同步改测试。**
 */
@Immutable
data class TtSlots(
    /** 0 方卡 / 1 便签 / 2 细线 / 3 色块 / 4 标签 */
    val shape: Int = 0,
    /** 0 无 / 1 左脊 / 2 顶帽 / 3 角点 / 4 底线 / 5 淡底 / 6 斜纹 */
    val decor: Int = 1,
    /** 0 三行 / 1 同行 / 2 底栏 / 3 只课名 */
    val meta: Int = 0,
    /** 0 无 / 1 折角 / 2 斜纹带 */
    val badge: Int = 0,
    /** 0 不褪 / 1 上褪 / 2 斜线 / 3 整块淡 */
    val erosion: Int = 1,
    /** 0 关 / 1 呼吸 / 2 褪色呼吸 */
    val motion: Int = 0,
    /** 0 四色 / 1 单墨 / 2 无色 */
    val tone: Int = 0,
    /** 0 紧凑 / 1 标准 / 2 舒展 */
    val header: Int = 1,
    /** 0 紧凑 / 1 标准 / 2 宽松（行高） */
    val grid: Int = 1,
    /** 0 无 / 1 横线 / 2 田垄 / 3 复相 / 4 纯色 */
    val backdrop: Int = 0,
    /** 背景缓慢移动（与 backdrop 正交：关动效 ≠ 换纯色） */
    val bgMotion: Boolean = false,
    /** 超立方体：0 不显示 / 1 淡线 / 2 亮线 */
    val tess: Int = 1,
    /** 它的动法：0 自由旋转 / 1 跟手横滑 / 2 反向双转 / 3 定格 / 4 正交投影 */
    val tessMode: Int = 0,
)

/** 覆盖位序号。设置页、ViewModel、渲染层共用这一份，不各写各的。 */
object TtAxis {
    const val SHAPE = 0
    const val DECOR = 1
    const val META = 2
    const val BADGE = 3
    const val EROSION = 4
    const val MOTION = 5
    const val TONE = 6
    const val HEADER = 7
    const val GRID = 8
    const val BACKDROP = 9
    const val BG_MOTION = 10
    /** 超立方体本体（不显示 / 淡线 / 亮线） */
    const val TESS = 11
    /** 它的动法 —— TESS 的子轴：TESS=0 时整条锁死在「定格」 */
    const val TESS_MODE = 12
    /** 每条轴的取值个数 —— 序号 0..n-1，与 [options] 一一对应 */
    val sizes = intArrayOf(5, 7, 4, 3, 4, 3, 3, 3, 3, 5, 2, 3, 5)
    val names = arrayOf(
        "形状", "装饰", "字段", "角标", "侵蚀", "动效", "底色",
        "页头", "行距", "底纹", "底纹动效", "超立方体", "转动逻辑",
    )
    val options = arrayOf(
        arrayOf("方卡", "便签", "细线", "色块", "标签"),
        arrayOf("无", "左脊", "顶帽", "角点", "底线", "淡底", "斜纹"),
        arrayOf("三行", "同行", "底栏", "只课名"),
        arrayOf("无", "折角", "斜纹带"),
        arrayOf("不褪", "上褪", "斜线", "整块淡"),
        arrayOf("关", "呼吸", "褪色呼吸"),
        arrayOf("四色", "单墨", "无色"),
        arrayOf("紧凑", "标准", "舒展"),
        arrayOf("紧凑", "标准", "宽松"),
        arrayOf("无", "横线", "田垄", "复相", "纯色"),
        arrayOf("关", "缓移"),
        arrayOf("不显示", "淡线", "亮线"),
        arrayOf("自由旋转", "跟手横滑", "反向双转", "定格", "正交投影"),
    )

    /**
     * 子轴关系：把 [TESS_MODE] 标成 [TESS] 的子项。
     *
     * 设置页据此把「转动逻辑」缩进一级、并在超立方体不显示时把整行置灰 ——
     * 关系写在这里而不是界面里，是因为渲染层同样要按它做钳制（[TtPresets.sanitize]）。
     */
    fun parentOf(axis: Int): Int = if (axis == TESS_MODE) TESS else -1
}

/** 预设套数（ViewModel 里做上限钳制用） */
const val TtPresetCount = 5

/** decor = 左脊的序号，migration 要用 */
const val TtDecorSpine = 1

/** 一档预设：名字 + 整套槽位取值。预设 = 「一次把 13 条轴写成某一组具体值」。 */
@Immutable
data class TtPreset(
    val id: String,
    val label: String,
    val note: String,
    val slots: TtSlots,
    /**
     * 版式标记：0 常规 / 1 账页。
     *
     * 只影响与格子无关的版面（页头质感、未排课行），**格子一律由 [slots] 决定** ——
     * m41b 之前它是 `tt_card_layout` 这个独立开关，会让格子绕开 slots 走另一条渲染分支，
     * 结果设置页怎么调格子都不变。现在它只是预设的一部分。
     */
    val layout: Int = 0,
)

/**
 * 槽位覆盖：0..n = 具体取值，-1 = 沿用预设值。
 *
 * 主人 2026-11-21 定：「五个都暴露」—— 五套预设全部进设置页，每套下面还能逐轴改。
 * 2026-11-23 再定：界面里不再有「跟随」这一枚 —— 预设点下去就把 13 条轴写成具体值
 * （[TtPresets.explicit]），所以正常运行时这里不会出现 -1；-1 只是 [TtPresets.resolve]
 * 的兜底分支，供老数据与测试使用。
 */
@Immutable
data class TtOverrides(
    val shape: Int = -1,
    val decor: Int = -1,
    val meta: Int = -1,
    val badge: Int = -1,
    val erosion: Int = -1,
    val motion: Int = -1,
    val tone: Int = -1,
    val header: Int = -1,
    val grid: Int = -1,
    val backdrop: Int = -1,
    /** -1 跟随 / 0 关 / 1 开 */
    val bgMotion: Int = -1,
    val tess: Int = -1,
    val tessMode: Int = -1,
)

object TtPresets {

    /** 五套预设，顺序即设置页顺序。第 0 套 = 改造前的样子。 */
    val ALL: List<TtPreset> = listOf(
        TtPreset(
            id = "paper",
            label = "纸卡",
            note = "侵蚀纸卡 + 四色分科，改造前的老样子",
            slots = TtSlots(
                shape = 0, decor = 1, meta = 0, badge = 0,
                erosion = 1, motion = 0, tone = 0,
                header = 1, grid = 1, backdrop = 0, bgMotion = false,
                tess = 1, tessMode = 0,
            ),
        ),
        TtPreset(
            id = "ledger",
            label = "账页",
            note = "横线格 + 竖脊，字距更紧，一屏多一节",
            layout = 1,
            slots = TtSlots(
                shape = 3, decor = 4, meta = 1, badge = 0,
                erosion = 0, motion = 0, tone = 1,
                header = 0, grid = 0, backdrop = 1, bgMotion = false,
                tess = 2, tessMode = 0,
            ),
        ),
        TtPreset(
            id = "dense",
            label = "密排",
            note = "细线框 + 只课名，一屏十一二节",
            slots = TtSlots(
                shape = 2, decor = 0, meta = 3, badge = 0,
                erosion = 0, motion = 0, tone = 1,
                header = 0, grid = 0, backdrop = 0, bgMotion = false,
                tess = 0, tessMode = 3,
            ),
        ),
        TtPreset(
            id = "agenda",
            label = "日程",
            note = "便签 + 顶帽 + 底栏，像手写日程本",
            slots = TtSlots(
                shape = 1, decor = 2, meta = 2, badge = 0,
                erosion = 3, motion = 1, tone = 0,
                header = 2, grid = 2, backdrop = 2, bgMotion = false,
                tess = 1, tessMode = 1,
            ),
        ),
        TtPreset(
            id = "quiet",
            label = "安静",
            note = "标签 + 淡底 + 无色，页面几乎只剩字",
            slots = TtSlots(
                shape = 4, decor = 5, meta = 0, badge = 0,
                erosion = 0, motion = 0, tone = 2,
                header = 1, grid = 1, backdrop = 3, bgMotion = true,
                tess = 0, tessMode = 3,
            ),
        ),
    )

    fun of(index: Int): TtPreset = ALL[index.coerceIn(0, ALL.size - 1)]

    fun indexOf(id: String): Int = ALL.indexOfFirst { it.id == id }.coerceAtLeast(0)

    /** 某套预设的版式标记（0 常规 / 1 账页） */
    fun layoutOf(index: Int): Int = of(index).layout

    /**
     * 把一套槽位展开成 13 条**显式**覆盖值。
     *
     * 2026-11-23 主人定：设置页里的预设不是「跟随某个基准」，而是「选择对应字段的效果组合」。
     * 所以点预设 = 直接把这些值逐条写下去，界面任何一行都能读出一个确定值。
     */
    fun explicit(slots: TtSlots): TtOverrides = TtOverrides(
        shape = slots.shape,
        decor = slots.decor,
        meta = slots.meta,
        badge = slots.badge,
        erosion = slots.erosion,
        motion = slots.motion,
        tone = slots.tone,
        header = slots.header,
        grid = slots.grid,
        backdrop = slots.backdrop,
        bgMotion = if (slots.bgMotion) 1 else 0,
        tess = slots.tess,
        tessMode = slots.tessMode,
    )

    /** 预设 + 覆盖 → 最终槽位，并就地消掉互斥组合。 */
    fun resolve(presetIndex: Int, o: TtOverrides): TtSlots {
        val base = of(presetIndex).slots
        fun pick(ov: Int, fallback: Int, max: Int): Int =
            if (ov < 0) fallback else ov.coerceIn(0, max)
        var s = base.copy(
            shape = pick(o.shape, base.shape, 4),
            decor = pick(o.decor, base.decor, 6),
            meta = pick(o.meta, base.meta, 3),
            badge = pick(o.badge, base.badge, 2),
            erosion = pick(o.erosion, base.erosion, 3),
            motion = pick(o.motion, base.motion, 2),
            tone = pick(o.tone, base.tone, 2),
            header = pick(o.header, base.header, 2),
            grid = pick(o.grid, base.grid, 2),
            backdrop = pick(o.backdrop, base.backdrop, 4),
            bgMotion = if (o.bgMotion < 0) base.bgMotion else o.bgMotion == 1,
            tess = pick(o.tess, base.tess, 2),
            tessMode = pick(o.tessMode, base.tessMode, 4),
        )
        s = sanitize(s)
        return s
    }

    /**
     * 互斥消解。规则不是审美偏好，是**算得出来的冲突**：
     *   · 斜纹带贴顶边，会吃掉「只课名」以外的任何一行——所以带子出现时 meta 必须只课名；
     *   · 折角与角点抢同一个右上角，两条都要就互相盖住；
     *   · 细线框没有填充面，淡底与斜纹没有意义（画了看不出，只会以为没生效）；
     *   · 无色 + 色块 = 色块退化成描边（否则「无色」这个选项等于没作用）。
     */
    fun sanitize(s: TtSlots): TtSlots {
        var r = s
        if (r.badge == 2) r = r.copy(meta = 3)
        if (r.decor == 3 && r.badge == 1) r = r.copy(badge = 0)
        if (r.shape == 2 && (r.decor == 5 || r.decor == 6)) r = r.copy(decor = 0)
        if (r.tone == 2 && r.shape == 3) r = r.copy(shape = 0)
        // 超立方体那两条**不做**互斥：不显示时把动法强制定格，等于用户开回来时
        // 发现选的档被偷偷换成定格、值被吃掉了（整合稿 §3「子轴：不是互斥，是锁」）。
        // 锁只在设置页那一行上表达：整行置灰、点不动、不显示选中值。
        return r
    }
}
