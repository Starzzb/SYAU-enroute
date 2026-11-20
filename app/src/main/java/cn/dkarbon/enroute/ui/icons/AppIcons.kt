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

    // ────────────────────────────── 打开原网页（打开网址 / 源站跳转） ──────────────────────────────

    private val OpenInNewV1: ImageVector by lazy {
        vector(
            name = "OpenInNewV1",
            "M3.5 3.5H20.5V20.5H3.5ZM5.5 5.5V18.5H18.5V5.5Z" to PathFillType.EvenOdd,
            "M13.6 3.5H20.5V10.4H18.5V6.9L14.2 11.2 12.8 9.8 17.1 5.5H13.6Z" to PathFillType.NonZero,
        )
    }

    private val OpenInNewV2: ImageVector by lazy {
        stroked(
            name = "OpenInNewV2",
            "M14 4.2h5.8V10",
            "M19.4 4.6 11.2 12.8",
            "M17.8 13.6v4.2a2 2 0 0 1-2 2H6.4a2 2 0 0 1-2-2V7.4a2 2 0 0 1 2-2h4.2",
        )
    }

    private val OpenInNewV3: ImageVector by lazy {
        vector(
            name = "OpenInNewV3",
            "M4.2 4.2H20V20H4.2ZM6.6 6.6V17.6H17.6V6.6Z" to PathFillType.EvenOdd,
            "M13 3.2H20.8V11H18.4V7.4L14.4 11.4 12.6 9.6 16.6 5.6H13Z" to PathFillType.NonZero,
        )
    }

    /** 打开原网页（外链）。 */
    val OpenInNew: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> OpenInNewV1
            IconVariant.ROUND -> OpenInNewV2
            IconVariant.BLOCK -> OpenInNewV3
        }

    // ────────────────────────────── 反馈（意见 / 提建议） ──────────────────────────────

    private val FeedbackV1: ImageVector by lazy {
        vector(
            name = "FeedbackV1",
            "M3 4H21V16.5H9.4L3 22ZM5 6V14.5H18.6L19 14.1V6Z" to PathFillType.EvenOdd,
            "M7.4 8.4H16.6V10.4H7.4Z" to PathFillType.NonZero,
        )
    }

    private val FeedbackV2: ImageVector by lazy {
        stroked(
            name = "FeedbackV2",
            "M6.2 4.2h11.6a2 2 0 0 1 2 2v7.4a2 2 0 0 1-2 2h-7.4L5.6 20v-3.4a2 2 0 0 1-1.4-2V6.2a2 2 0 0 1 2-2z",
        )
    }

    private val FeedbackV3: ImageVector by lazy {
        vector(
            name = "FeedbackV3",
            "M2.6 3.6H21.4V16.2H9.6L2.6 22.4Z" to PathFillType.NonZero,
            "M7.4 9.6h9.2v2.2H7.4Z" to PathFillType.EvenOdd,
        )
    }

    /** 反馈（提建议 / 反馈问题）。 */
    val Feedback: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> FeedbackV1
            IconVariant.ROUND -> FeedbackV2
            IconVariant.BLOCK -> FeedbackV3
        }

    // ────────────────────────────── 导出（另存为 / 导出标注） ──────────────────────────────

    private val DownloadV1: ImageVector by lazy {
        vector(
            name = "DownloadV1",
            "M11 3H13V11.2L16.2 8 17.6 9.4 12 15 6.4 9.4 7.8 8 11 11.2Z" to PathFillType.NonZero,
            "M4 15H6V19H18V15H20V21H4Z" to PathFillType.NonZero,
        )
    }

    private val DownloadV2: ImageVector by lazy {
        stroked(
            name = "DownloadV2",
            "M12 3.6v9.8",
            "M8.2 9.8 12 13.6l3.8-3.8",
            "M5 16.6v1.6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1.6",
        )
    }

    private val DownloadV3: ImageVector by lazy {
        vector(
            name = "DownloadV3",
            "M9.6 3.2H14.4V10.6H18.4L12 17.6 5.6 10.6H9.6Z" to PathFillType.NonZero,
            "M3.4 19.2H20.6V21.6H3.4Z" to PathFillType.NonZero,
        )
    }

    /** 导出（下载 / 另存）。 */
    val Download: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> DownloadV1
            IconVariant.ROUND -> DownloadV2
            IconVariant.BLOCK -> DownloadV3
        }

    // ────────────────────────────── 保存（确认写入） ──────────────────────────────

    private val SaveV1: ImageVector by lazy {
        vector(
            name = "SaveV1",
            "M4 3H20V21H4ZM6.4 5.4H13.6V10H6.4ZM6.4 13H17.6V18.6H6.4Z" to PathFillType.EvenOdd,
        )
    }

    private val SaveV2: ImageVector by lazy {
        stroked(
            name = "SaveV2",
            "M5.4 4.2h9.6l4 4V19a1.4 1.4 0 0 1-1.4 1.4H6.8A1.4 1.4 0 0 1 5.4 19Z",
            "M9 4.4v4.6h6",
            "M8.6 13.4h6.8",
        )
    }

    private val SaveV3: ImageVector by lazy {
        vector(
            name = "SaveV3",
            "M3.6 3.6H20.4V20.4H3.6ZM6.2 6.2H12.6V9.8H6.2ZM6.2 12.6H17.4V17.8H6.2Z" to PathFillType.EvenOdd,
        )
    }

    /** 保存（确认写入）。 */
    val Save: ImageVector
        get() = when (IconVariants.variant) {
            IconVariant.GEOMETRIC -> SaveV1
            IconVariant.ROUND -> SaveV2
            IconVariant.BLOCK -> SaveV3
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

    // ────────────────────────────── 供预览/调试：按名字取三版 ──────────────────────────────

    /**
     * 取指定符号与变体的图标，给 `docs/design/图标三版.html` 的实机对照页与日后换版调试用。
     *
     * 调用点仍应该写 `AppIcons.BookmarkOutline` 这类具名属性；这里只是把「三版并排」这件事
     * 做成可枚举的，避免为了试一版而改一堆文件。
     */
    fun variantOf(symbol: IconSymbol, variant: IconVariant): ImageVector = when (symbol) {
        IconSymbol.BookmarkOutline -> when (variant) {
            IconVariant.GEOMETRIC -> BookmarkOutlineV1
            IconVariant.ROUND -> BookmarkOutlineV2
            IconVariant.BLOCK -> BookmarkOutlineV3
        }
        IconSymbol.BookmarkFilled -> when (variant) {
            IconVariant.GEOMETRIC -> BookmarkFilledV1
            IconVariant.ROUND -> BookmarkFilledV2
            IconVariant.BLOCK -> BookmarkFilledV3
        }
        IconSymbol.OpenInNew -> when (variant) {
            IconVariant.GEOMETRIC -> OpenInNewV1
            IconVariant.ROUND -> OpenInNewV2
            IconVariant.BLOCK -> OpenInNewV3
        }
        IconSymbol.Feedback -> when (variant) {
            IconVariant.GEOMETRIC -> FeedbackV1
            IconVariant.ROUND -> FeedbackV2
            IconVariant.BLOCK -> FeedbackV3
        }
        IconSymbol.Download -> when (variant) {
            IconVariant.GEOMETRIC -> DownloadV1
            IconVariant.ROUND -> DownloadV2
            IconVariant.BLOCK -> DownloadV3
        }
        IconSymbol.Save -> when (variant) {
            IconVariant.GEOMETRIC -> SaveV1
            IconVariant.ROUND -> SaveV2
            IconVariant.BLOCK -> SaveV3
        }
        IconSymbol.NetworkOff -> when (variant) {
            IconVariant.GEOMETRIC -> NetworkOffV1
            IconVariant.ROUND -> NetworkOffV2
            IconVariant.BLOCK -> NetworkOffV3
        }
    }
}

/** 有三版几何的符号清单（与 [AppIcons.variantOf] 一一对应）。 */
enum class IconSymbol {
    BookmarkOutline,
    BookmarkFilled,
    OpenInNew,
    Feedback,
    Download,
    Save,
    NetworkOff,
}
