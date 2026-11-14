package cn.dkarbon.enroute.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 形状 token（票 31 · 账页重构 v2 批次 1）。
 *
 * ## 为什么要有这个文件
 * 之前全仓的圆角是**散在各处的字面量**：`RoundedCornerShape(6.dp)`、`RoundedCornerShape(999.dp)`、
 * `CircleShape`、`RoundedCornerShape(0.dp)`……同一个"按钮"在不同页面能是 0、6、999 三种样子，
 * 而且**改不干净**——只要允许业务代码写 `RoundedCornerShape(6.dp)`，明天就还能偷偷加一个。
 *
 * 所以这里做两件事：
 * 1. **只导出语义 [Shape]，不导出 Dp**：业务代码拿 `Form.current.shapes.card`，拿不到"半径是多少"，
 *    自然也就无法在旁边写一个自己的数字；
 * 2. 配套 [cn.dkarbon.enroute.ShapeTokenTest] 扫源码兜底（见该测试文件头的"为什么正则只是兜底"）。
 *
 * ## 八个语义档（不再有"小/中/大"这种尺寸名）
 * | 档 | 用在哪 | HARD | LEDGER |
 * |---|---|---|---|
 * | [page] | 页面块、通栏列表、表格行、分割区 | 0 | 0 |
 * | [badge] | 徽标、角标、小色块、缩略图 | 0 | 6dp |
 * | [control] | **按钮**、输入框、chips、搜索条 | 0 | 6dp |
 * | [card] | 通用卡片、便签 | 0 | 10dp |
 * | [courseCard] | 课表课程卡（HARD 4dp 是主人点名的局部例外） | 4dp | 10dp |
 * | [sheet] | 对话框、底单、浮层 | 0 | 14dp |
 * | [pill] | 胶囊（**按高度百分比**，任意高度恒为整圆） | 0 | 50% |
 * | [circle] | 正圆：头像、6dp 状态点 | 圆 | 圆 |
 *
 * ## 两条硬规矩
 * - **胶囊不用 999dp**。999 是"足够大的常数"，在 3dp 高的进度条上毫无意义、在 48dp 高的按钮上
 *   又正好等于半径上限 —— 换个高度就要重算。改成 `RoundedCornerShape(50)`（百分比），
 *   高度多少都正好是半高，这才是"胶囊"的定义。
 * - [circle] 与 [pill] 不是同一件事：circle 是"正圆"（头像、状态点，宽高相等时才成立），
 *   pill 是"两端半圆的矩形"（胶囊）。混用就会出现"把 48dp 按钮画成 48dp 正圆"这类事故。
 */
@Immutable
data class ShapeSet(
    val page: Shape,
    val badge: Shape,
    val control: Shape,
    val card: Shape,
    val courseCard: Shape,
    val sheet: Shape,
    val pill: Shape,
    val circle: Shape,
) {
    companion object {

        /** 硬朗农业：除了课卡那 4dp（主人点名的例外），其余一律方角 */
        val Hard = ShapeSet(
            page = RectangleShape,
            badge = RectangleShape,
            control = RectangleShape,
            card = RectangleShape,
            courseCard = RoundedCornerShape(4.dp),
            sheet = RectangleShape,
            pill = RectangleShape,
            circle = CircleShape,
        )

        /** 账页：便签 10、按钮/徽标 6、浮层 14；胶囊按百分比 */
        val Ledger = ShapeSet(
            page = RectangleShape,
            badge = RoundedCornerShape(6.dp),
            control = RoundedCornerShape(6.dp),
            card = RoundedCornerShape(10.dp),
            courseCard = RoundedCornerShape(10.dp),
            sheet = RoundedCornerShape(14.dp),
            pill = RoundedCornerShape(percent = 50),
            circle = CircleShape,
        )

        fun of(editorial: Boolean): ShapeSet = if (editorial) Ledger else Hard
    }
}

/**
 * 单测用的形状比对：把语义档和"它应该是哪个形状"当等式验。
 *
 * 不提供"取半径是多少 dp"的 API —— 那等于把数字又发回给业务层，绕一圈又回到老路。
 * 要验值就验**形状本身相等**（`Shape` 的实现类都实现了 `equals`，百分比与 dp 也不会混）。
 */
internal fun Shape.sameAs(other: Shape): Boolean = this == other
