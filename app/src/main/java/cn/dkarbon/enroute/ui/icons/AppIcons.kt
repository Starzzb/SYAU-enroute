package cn.dkarbon.enroute.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 少数几个 `material-icons-core` 里没有、但我们确实要用的图标。
 *
 * 为什么自己画而不是留 `material-icons-extended`：那个依赖是几十 MB 的矢量数据，
 * debug 构建不剥离会明显拖慢装包；而我们真正超出 core 的只有五个符号。
 *
 * 几何口径：
 * - 画布 24dp，与 core 一致，所以 `Icon(..., modifier.size(19.dp))` 的观感不变
 * - 描边类只画轮廓（fill 自己一个闭合子路径），实心类整体填充
 * - 颜色一律用 [Color.Black]，由 `Icon(tint = ...)` 覆盖，不要在这里写死主题色
 */
object AppIcons {

    private fun vector(name: String, vararg paths: Pair<String, PathFillType>): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { (data, fillType) ->
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = fillType,
            )
        }
        return builder.build()
    }

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
}
