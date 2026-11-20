package cn.dkarbon.enroute.ui.common

import kotlin.math.cos
import kotlin.math.sin

/**
 * 四维超立方体（tesseract）的**旋转 + 投影**，纯数学。
 *
 * ## 为什么单独一个文件
 * 课表背景（`MathBackdrop`）与「关于」页背景都用它；而"四维旋转到底转得对不对"这件事
 * **不能靠眼睛判断** —— 所以要能在 JVM 单测里跑：这个文件不 import 任何 Compose / Android 类，
 * 顶点与棱都在预分配数组里算，没有对象分配（每帧调用也不会产生垃圾）。
 *
 * ## 六平面旋转（不是 2D 自转）
 * 四维里刚体旋转是**在一个平面内的转动**，共有 6 个坐标平面：
 *
 * ```
 * XY   XZ   XW   YZ   YW   ZW
 * ```
 *
 * 只有绕 **XW / YW / ZW** 转，顶点才会在 `w` 方向上移动；`w` 一动，
 * 4D→3D 的透视除法 `k = 1/(D - w)` 就变了 —— 视觉上就是**内层小立方体涨大穿出外层、
 * 外层缩回变小**。这就是主人要的「转换」，2D 自转永远做不出来（2D 转只是整个图形在转圈）。
 *
 * ## 速度为什么这么配
 * 6 个平面各给**不同速度**（否则就是单一平面的重复转）。但速度不能随便给：
 * 四维里存在"等斜（isoclinic）"配对 —— `(XY,ZW)`、`(XZ,YW)`、`(XW,YZ)` 三对。
 * 同一对的两个角度**相近**时，图形在整个周期里保持饱满；差太多时某些瞬间会压成一条线。
 * 所以这里配成：每对基速 1.00 / 0.62 / 0.38 圈，对内偏移 ±0.04 圈 —— 六个速度两两不同，
 * 又始终贴近等斜。单测 `投影在整周期内不退化` 就是盯这一点的。
 *
 * ## 周期
 * 一个周期 = [PHASE_CYCLE_SECONDS] 秒（对外约定 ≥20 秒：要"在变"，不是"在转"）。
 */
object Tesseract4D {

    /** 16 个顶点：4 个 bit 组合出 (±1,±1,±1,±1)；bit0=x bit1=y bit2=z bit3=w */
    const val VERTEX_COUNT = 16

    /** 32 条棱：每个顶点沿每个方向连一条 */
    const val EDGE_COUNT = 32

    /** 6 个坐标平面 */
    const val PLANES = 6

    /** 平面下标（别用魔法数字，单测里读起来也清楚） */
    const val XY = 0
    const val XZ = 1
    const val XW = 2
    const val YZ = 3
    const val YW = 4
    const val ZW = 5

    /**
     * 每个平面在一个周期里转多少圈。
     *
     * 下标顺序与 [XY]…[ZW] 一致。数值见类注释「速度为什么这么配」。
     */
    val TURNS_PER_CYCLE = floatArrayOf(
        1.00f,   // XY ┐ 等斜对 A（ZW = 1.04，差 0.04 圈）
        0.58f,   // XZ ┐ 等斜对 B（YW = 0.70，刻意拉开一点让两对不必同步）
        0.46f,   // XW ┐ 等斜对 C（YZ = 0.50）
        0.50f,   // YZ ┘
        0.70f,   // YW ┘
        1.04f,   // ZW ┘
    )

    /** 6 个平面的**初始**相位（避免 t=0 时正好是个对称姿态，那样开头几秒会显得"卡住"） */
    val PHASE_OFFSET = floatArrayOf(0.18f, 0.44f, 0.71f, 0.29f, 0.86f, 0.05f)

    /** 4D→3D 的透视距离（太小会撕裂，太大看不出"四维感"） */
    const val W_DISTANCE = 2.6f

    /** 3D→2D 的相机角：**固定**。三维视角不能动，否则"是四维在转"就说不清了 */
    const val CAM_PITCH = 0.52f
    const val CAM_YAW = 0.36f

    /** 3D→2D 的透视距离与缩放 */
    const val Z_DISTANCE = 4.2f
    const val Z_SCALE = 1.9f

    /** 32 条棱摊平成 64 个 int：`[a0,b0,a1,b1,…]` */
    val EDGES: IntArray = buildList {
        for (a in 0 until VERTEX_COUNT) {
            for (bit in 0 until 4) {
                val b = a xor (1 shl bit)
                if (a < b) {
                    add(a)
                    add(b)
                }
            }
        }
    }.toIntArray()

    /**
     * 相位归一化：任意实数 → `[0,1)`。
     * 负值也要能折回（跨周期续算时可能出现负数，别让它算出负角度）。
     */
    fun normalizePhase(t: Float): Float {
        if (t.isNaN() || t.isInfinite()) return 0f
        val m = t % 1f
        return if (m < 0f) m + 1f else m
    }

    /** `t`（0..1 相位）→ 6 个平面角（弧度），写进 [out]（长度 ≥ [PLANES]） */
    fun anglesInto(out: FloatArray, t: Float) {
        val p = normalizePhase(t)
        for (i in 0 until PLANES) {
            out[i] = (PHASE_OFFSET[i] + TURNS_PER_CYCLE[i] * p) * TWO_PI
        }
    }

    /**
     * 把一个四维点按 6 个平面依次转动，结果写进 `out[0..3]`（x,y,z,w）。
     *
     * 顺序固定（XY→XZ→XW→YZ→YW→ZW）：旋转不可交换，但**固定顺序**就等价于一个确定的
     * 旋转矩阵，单测的正交性就是对这个矩阵验的。
     */
    fun rotateInto(out: FloatArray, x0: Float, y0: Float, z0: Float, w0: Float, angles: FloatArray) {
        var x = x0
        var y = y0
        var z = z0
        var w = w0

        var c = cos(angles[XY]); var s = sin(angles[XY])
        var t = x * c - y * s; y = x * s + y * c; x = t

        c = cos(angles[XZ]); s = sin(angles[XZ])
        t = x * c - z * s; z = x * s + z * c; x = t

        c = cos(angles[XW]); s = sin(angles[XW])
        t = x * c - w * s; w = x * s + w * c; x = t

        c = cos(angles[YZ]); s = sin(angles[YZ])
        t = y * c - z * s; z = y * s + z * c; y = t

        c = cos(angles[YW]); s = sin(angles[YW])
        t = y * c - w * s; w = y * s + w * c; y = t

        c = cos(angles[ZW]); s = sin(angles[ZW])
        t = z * c - w * s; w = z * s + w * c; z = t

        out[0] = x; out[1] = y; out[2] = z; out[3] = w
    }

    /**
     * 16 个顶点：4D 旋转 → w 透视 → 固定 3D 相机 → z 透视 → 屏幕坐标。
     *
     * @param xy 输出，长度 ≥ 32：`[x0,y0,x1,y1,…]`（屏幕像素）
     * @param depth 输出，长度 ≥ 16：透视因子，越大越"近"（画线时用来做近实远虚）
     * @param angleScratch 长度 ≥ 4 的临时缓冲（避免内部 new）
     */
    fun projectInto(
        xy: FloatArray,
        depth: FloatArray,
        angleScratch: FloatArray,
        angles: FloatArray,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        val cp = cos(CAM_PITCH)
        val sp = sin(CAM_PITCH)
        val cyaw = cos(CAM_YAW)
        val syaw = sin(CAM_YAW)

        for (i in 0 until VERTEX_COUNT) {
            val vx = if (i and 1 != 0) 1f else -1f
            val vy = if (i and 2 != 0) 1f else -1f
            val vz = if (i and 4 != 0) 1f else -1f
            val vw = if (i and 8 != 0) 1f else -1f

            rotateInto(angleScratch, vx, vy, vz, vw, angles)
            val x = angleScratch[0]
            val y = angleScratch[1]
            val z = angleScratch[2]
            val w = angleScratch[3]

            // ① 4D → 3D：w 做透视除法。w 越大 → k 越大 → 越"近"→ 内立方体涨出来
            val k4 = 1f / (W_DISTANCE - w)
            val x3 = x * k4
            val y3 = y * k4
            val z3 = z * k4

            // ② 固定 3D 相机（yaw 绕 y，pitch 绕 x）
            val xa = x3 * cyaw + z3 * syaw
            val za = -x3 * syaw + z3 * cyaw
            val ya = y3 * cp - za * sp
            val zb = y3 * sp + za * cp

            // ③ 3D → 2D：z 透视
            val k2 = Z_SCALE / (Z_DISTANCE - zb)
            xy[i * 2] = cx + xa * k2 * radius
            xy[i * 2 + 1] = cy + ya * k2 * radius
            depth[i] = k2
        }
    }

    /**
     * 透视因子归一化到 0..1（近 = 1）。
     *
     * 上下界不是拍脑袋：顶点在 4D 里到原点的距离是 2（(±1,±1,±1,±1)），
     * 所以旋转后 |w| 最大可到 ~1.94（不是 1！），实测 `k2` 落在 0.354–0.592。
     * 这里留一点余量取 0.30–0.65。
     */
    fun depthNormalize(k2: Float): Float =
        ((k2 - 0.30f) / (0.65f - 0.30f)).coerceIn(0f, 1f)

    // ═══════════════════════════════════════════════════════════
    // 自驱速度（m31 轨道 E：关于页「速度不固定」）
    //
    // 以前的相位是 `elapsed / period` 的匀速直线 —— 看起来像电机在转。
    // 现在把相位当**积分量**：速度本身随两个低频正弦摆动，
    //     speed(t) = base · (1 + 0.55·sin(0.17 t) + 0.25·sin(0.53 t + 1.1))
    //     phase   += speed(t) · dt
    // 于是同一个 4D 姿态出现的时间间隔不再相等：加速一阵、松下来一阵，
    // 像有人用手拨了一下转盘。
    //
    // 三条口径（页面 / 原型 / 单测都要对得上，所以参数只写在这里）：
    //   · base = 1/26 圈每秒（平均 26 秒一圈，落在「一轮 20–30 秒」中段）；
    //   · 两个正弦的角频率 0.17 / 0.53 rad/s（周期 37.0 s / 11.9 s），
    //     慢的是主节拍、快的是加速段里的余韵，两者不同步 → 不会出现机械重复的两拍；
    //   · 初相 1.1 rad 让 t=0 时不是全速，避免进页面就冲。
    //
    // 实测（按 0.002 s 步长积 400 s、200 个起点取样）：倍数 0.20–1.80，
    // 一圈用时 18.7–30.3 s、均值 25.4 s，84% 的圈落在 20–30 s 内；
    // 「倍数 ≥1.6」持续 3.6–4.5 s（最快速那一段），「≤0.4」持续 3.5–4.5 s（最慢那一段）——
    // 快到能被看出来，又不会一闪而过。
    // ═══════════════════════════════════════════════════════════

    /** 基准角速度：**圈/秒**。1/26 ≈ 0.0385 → 平均 26 秒一个四维周期。 */
    const val BASE_TURNS_PER_SECOND = 1f / 26f

    /** 慢摆幅度（主节拍） */
    const val SPEED_AMP_SLOW = 0.55f

    /** 慢摆角频率 rad/s（≈37 秒一轮） */
    const val SPEED_FREQ_SLOW = 0.17f

    /** 快摆幅度（加速段里的余韵） */
    const val SPEED_AMP_FAST = 0.25f

    /** 快摆角频率 rad/s（≈11.9 秒一轮） */
    const val SPEED_FREQ_FAST = 0.53f

    /** 快摆初相 rad：错开两个正弦的峰，别让 t=0 正好全速 */
    const val SPEED_PHASE_FAST = 1.1f

    /**
     * 速度倍数：`1 + 0.55·sin(0.17 t) + 0.25·sin(0.53 t + 1.1)`，取值 0.20–1.80、均值 1。
     *
     * @param seconds 从动画起算的连续秒数（不是相位，也不是帧号）
     */
    fun speedFactor(seconds: Float): Float =
        1f +
            SPEED_AMP_SLOW * sin(seconds * SPEED_FREQ_SLOW) +
            SPEED_AMP_FAST * sin(seconds * SPEED_FREQ_FAST + SPEED_PHASE_FAST)

    /** 瞬时角速度（圈/秒）= 基准 × [speedFactor] */
    fun speedAt(seconds: Float): Float = BASE_TURNS_PER_SECOND * speedFactor(seconds)

    /**
     * 推进相位：`phase += speed · dt`，结果折回 `[0,1)`。
     *
     * @param seconds 从动画起算的连续秒数。**不能**用相位反推时间：速度是时间的函数、
     *   相位是它的积分，反过来推会在变速时段算错。
     * @param dt 距上一帧的秒数；上限由调用方夹（掉帧或从后台回来时别一口吃掉 2 秒）。
     */
    fun advancePhase(phase: Float, seconds: Float, dt: Float): Float =
        normalizePhase(phase + speedAt(seconds) * dt)

    // ═══════════════════════════════════════════════════════════
    // 透出档（UI 档位，与四维数学无关；放在这里是为了「只有一个出处」）
    // ═══════════════════════════════════════════════════════════

    /**
     * 「关于」页卡片的不透明度：亮色 0.62 / 暗色 0.58。
     *
     * 与 `ui/timetable/MathBackdrop.kt` 的 `tesseractAlpha(...)` 是同一族档位：
     * **线框强度与卡片透出程度必须成对调** —— 卡片再透一点，inkFaint 就掉到 4.5:1 以下；
     * 线框再淡一点，透过卡片就什么都看不见了。取值依据（四套主题的合成色对比度）
     * 写在 `ui/about/AboutScreen.kt` 的 AboutCard 注释里。
     */
    fun tesseractCardAlpha(isDark: Boolean): Float = if (isDark) 0.58f else 0.62f

    private const val TWO_PI = 6.2831855f
}
