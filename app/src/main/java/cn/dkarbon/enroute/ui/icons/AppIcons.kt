package cn.dkarbon.enroute.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 少数几个 `material-icons-core` 里没有、但我们确实要用的图标。
 *
 * 为什么自己画而不是留 `material-icons-extended`：那个依赖是几十 MB 的矢量数据，
 * debug 构建不剥离会明显拖慢装包；而我们真正超出 core 的只有这些符号。
 *
 * 几何口径：
 * - 画布 24dp，与 core 一致，所以 `Icon(..., modifier.size(19.dp))` 的观感不变
 * - 描边类只画轮廓（fill 自己一个闭合子路径），实心类整体填充
 * - 颜色一律用 [Color.Black]，由 `Icon(tint = ...)` 覆盖，不要在这里写死主题色
 *
 * ## 每个符号备三版（2026-11-20，主人要求）
 *
 * 「预备三版、默认用一版、之后方便替换」——所以每个符号都有 V1/V2/V3 三个几何家族：
 *
 * | 变体 | 家族 | 特征 | 适合 |
 * |---|---|---|---|
 * | [IconVariant.GEOMETRIC] | 折角几何 | 直角、2px 实心笔画、方收口 | 默认。贴合账页的硬朗版式 |
 * | [IconVariant.ROUND] | 圆润双线 | 1.8px 描边、圆端帽、圆角 | 弹层、空态、需要亲和感的场合 |
 * | [IconVariant.BLOCK] | 块面切割 | 只填块面，细节靠负形切出 | 大尺寸（≥28dp）、徽章、启动图 |
 *
 * 切换方式（不用改调用点）：
 * ```kotlin
 * IconVariants.variant = IconVariant.ROUND   // 全局换一版
 * ```
 * 公开的 `AppIcons.BookmarkOutline` 等属性会在首次访问时按当时选中的变体构建并缓存。
 *
 * 三版的取舍与画法见 `docs/设计-图标三版方案.md`，并排预览见 `docs/design/图标三版.html`。
 */
enum class IconVariant {
    /** 折角几何：直角、实心笔画。默认。 */
    GEOMETRIC,

    /** 圆润双线：描边、圆端帽。 */
    ROUND,

    /** 块面切割：整块实心，细节用负形。 */
    BLOCK,
}

/** 全局图标变体开关。默认 [IconVariant.GEOMETRIC]，可随时替换。 */
object IconVariants {
    @Volatile
    var variant: IconVariant = IconVariant.GEOMETRIC
}

object AppIcons {

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    /** 实心/挖空类：按 fillType 填黑。 */
    private fun vector(name: String, vararg paths: Pair<String, PathFillType>): ImageVector {
        val b = builder(name)
        paths.forEach { (data, fillType) ->
            b.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = fillType,
            )
        }
        return b.build()
    }

    /** 描边类：只给 stroke，不给 fill（[IconVariant.ROUND] 用）。线宽固定 1.8，见设计文档 §5.3。 */
    private fun stroked(
        name: String,
        vararg paths: String,
    ): ImageVector {
        val b = builder(name)
        paths.forEach { data ->
            b.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    // ────────────────────────────── 文稿／今天／星标／未读（历史遗留，只有一版） ──────────────────────────────

    /** 文稿／资讯。外框 + 三条不等长横线。 */
    val Article: ImageVector by lazy {
        vector(
            name = "Article",
            "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z" to PathFillType.NonZero,
            "M17 9H7V7h10v2z" to PathFillType.NonZero,
            "M17 13H7v-2h10v2z" to PathFillType.NonZero,
            "M14 17H7v-2h7v2z" to PathFillType.NonZero,
        )
    }

    /** 今天／本周。日历外框 + 右侧装订齿 + 一个填实的当天方块。 */
    val Today: ImageVector by lazy {
        vector(
            name = "Today",
            "M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z" to PathFillType.NonZero,
            "M7 10h5v5H7z" to PathFillType.NonZero,
        )
    }

    /** 收藏（未选中）。五角星轮廓。 */
    val StarBorder: ImageVector by lazy {
        vector(
            name = "StarBorder",
            "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z" to PathFillType.NonZero,
        )
    }

    /** 未读（未选中）。信封轮廓 + 右上未读点。 */
    val MarkEmailUnread: ImageVector by lazy {
        vector(
            name = "MarkEmailUnread",
            "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8l8 5 8-5v10zm-8-7L4 6h16l-8 5z" to PathFillType.NonZero,
            "M19 3a3 3 0 100 6 3 3 0 000-6z" to PathFillType.NonZero,
        )
    }

    /** 未读（选中）。同上，信封整体填实，仅留出折页。 */
    val MarkEmailUnreadFilled: ImageVector by lazy {
        vector(
            name = "MarkEmailUnreadFilled",
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 6h16l-8 5z" to PathFillType.EvenOdd,
            "M19 3a3 3 0 100 6 3 3 0 000-6z" to PathFillType.NonZero,
        )
    }

    // ────────────────────────────── 书签（收藏）：三版 + 描边/填实两态 ──────────────────────────────

    /** 书签·折角几何·轮廓。 */
    private val BookmarkOutlineV1: ImageVector by lazy {
        vector(
            name = "BookmarkOutlineV1",
            "M5 2H19V22L12 17.2 5 22ZM7 4.7H17V18.2L12 14.5 7 18.2Z" to PathFillType.EvenOdd,
        )
    }

    /** 书签·圆润双线·轮廓。 */
    private val BookmarkOutlineV2: ImageVector by lazy {
        stroked(
            name = "BookmarkOutlineV2",
            "M6.9 3.5h10.2a2 2 0 0 1 2 2v15.2l-7.1-5-7.1 5V5.5a2 2 0 0 1 2-2z",
        )
    }

    /** 书签·块面切割·轮廓：外块与内块之间留 2.2 的空隙。 */
    private val BookmarkOutlineV3: ImageVector by lazy {
        vector(
            name = "BookmarkOutlineV3",
            "M4.5 2H19.5V22.5L12 17.1 4.5 22.5ZM6.9 5H17.1V18.2L12 14.7 6.9 18.2Z" to PathFillType.EvenOdd,
        )
    }

    /** 书签·折角几何·填实。 */
    private val BookmarkFilledV1: ImageVector by lazy {
        vector(
            name = "BookmarkFilledV1",
            "M5 2H19V22L12 17.2 5 22Z" to PathFillType.NonZero,
        )
    }

    /** 书签·圆润双线·填实。 */
    private val BookmarkFilledV2: ImageVector by lazy {
        vector(
            name = "BookmarkFilledV2",
            "M6.9 2.8h10.2a2.7 2.7 0 0 1 2.7 2.7v15.9L12 16.2l-7.8 5.2V5.5a2.7 2.7 0 0 1 2.7-2.7z" to PathFillType.NonZero,
        )
    }

    /** 书签·块面切割·填实：整块实心，中间留一道竖缝做负形。 */
    private val BookmarkFilledV3: ImageVector by lazy {
        vector(
            name = "BookmarkFilledV3",
            "M4.5 2H19.5V22.5L12 17.1 4.5 22.5ZM11.1 5.2h1.8v6.2h-1.8z" to PathFillType.EvenOdd,
        )
    }

    /** 书签轮廓（收藏未选中）。当前变体由 [IconVariants.variant] 决定。 */
    val BookmarkOutline: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> BookmarkOutlineV1
            IconVariant.ROUND -> BookmarkOutlineV2
            IconVariant.BLOCK -> BookmarkOutlineV3
        }

    /** 书签填实（收藏已选中）。 */
    val BookmarkFilled: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> BookmarkFilledV1
            IconVariant.ROUND -> BookmarkFilledV2
            IconVariant.BLOCK -> BookmarkFilledV3
        }

    // ────────────────────────────── 内网提示（校园网 / VPN 不可达） ──────────────────────────────

    private val NetworkOffV1: ImageVector by lazy {
        vector(
            name = "NetworkOffV1",
            "M4 16H6.4V20H4Z" to PathFillType.NonZero,
            "M10.8 12H13.2V20H10.8Z" to PathFillType.NonZero,
            "M17.6 8H20V20H17.6Z" to PathFillType.NonZero,
            "M4.6 3.8 20.2 19.4 18.8 20.8 3.2 5.2Z" to PathFillType.NonZero,
        )
    }

    private val NetworkOffV2: ImageVector by lazy {
        stroked(
            name = "NetworkOffV2",
            "M5.2 16.6v2.8",
            "M12 12.2v7.2",
            "M18.8 7.6v11.8",
            "M4.4 4.2 19.6 19.4",
        )
    }

    private val NetworkOffV3: ImageVector by lazy {
        vector(
            name = "NetworkOffV3",
            "M3.4 15.4H6.6V20.4H3.4ZM10.4 11.4H13.6V20.4H10.4ZM17.4 7.4H20.6V20.4H17.4Z" to PathFillType.NonZero,
            "M3.8 3.4 20.6 20.2 19 21.8 2.2 5Z" to PathFillType.EvenOdd,
        )
    }

    /** 内网不可达（校园网 / mangoVPN 提示用）。 */
    val NetworkOff: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> NetworkOffV1
            IconVariant.ROUND -> NetworkOffV2
            IconVariant.BLOCK -> NetworkOffV3
        }

    
}
