package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import cn.dkarbon.enroute.R

/**
 * 一门课 = 一份食物（2026-11-11 主人设定）。
 *
 * 小幽灵沿竖线往下走，一路把这些食物吃掉 —— 所以「课程卡」在视觉上
 * 首先是一份**能吃的东西**，文字信息是它的注释。
 *
 * ## 素材来源（不是手绘，是网上取的）
 * **Twemoji** —— [jdecked/twemoji](https://github.com/jdecked/twemoji)，**CC-BY 4.0**：
 * 允许商用、允许修改、要求署名。由 `.tmp/food2vec.ps1` 抓 SVG 转成
 * Android VectorDrawable（16 个文件合计 28.6KB，比一张 512px PNG 还小）。
 *
 * 选材三条标准（都为了「26dp 下还能认出来」）：
 *   1. **轮廓差异大** —— 苹果 vs 草莓在缩略图上会糊，所以同色系的只留一个主形
 *   2. **颜色均匀铺开** —— 相邻两节课不撞色，扫一眼就能靠颜色分段
 *   3. **都是「一口一个」** —— 符合「幽灵吃掉」的叙事，不是一盘菜
 *
 * ## 想换成别的素材
 * 改 `.tmp/food2vec.ps1` 里的 `$foods` 表（Twemoji 码点 → 文件名），
 * 重跑脚本，再改这里加一个枚举项即可。用别的图标集也同理，
 * 只要满足「单色或扁平多色 path」就能转成 vector。
 */
enum class Food(val label: String) {
    Apple("苹果"),
    Strawberry("草莓"),
    Peach("桃"),
    Orange("橘子"),
    Banana("香蕉"),
    Grapes("葡萄"),
    Watermelon("西瓜"),
    Carrot("胡萝卜"),
    Bread("面包"),
    Croissant("可颂"),
    Cookie("饼干"),
    Donut("甜甜圈"),
    Cake("蛋糕"),
    Chocolate("巧克力"),
    Sushi("寿司"),
    Milk("牛奶");

    val resId: Int get() = when (this) {
        Apple -> R.drawable.food_apple
        Strawberry -> R.drawable.food_strawberry
        Peach -> R.drawable.food_peach
        Orange -> R.drawable.food_orange
        Banana -> R.drawable.food_banana
        Grapes -> R.drawable.food_grapes
        Watermelon -> R.drawable.food_watermelon
        Carrot -> R.drawable.food_carrot
        Bread -> R.drawable.food_bread
        Croissant -> R.drawable.food_croissant
        Cookie -> R.drawable.food_cookie
        Donut -> R.drawable.food_donut
        Cake -> R.drawable.food_cake
        Chocolate -> R.drawable.food_chocolate
        Sushi -> R.drawable.food_sushi
        Milk -> R.drawable.food_milk
    }

    companion object {
        /** 同一门课永远是同一份食物（按课程号取，改名不换） */
        fun of(key: String): Food {
            val h = key.hashCode() and 0x7fffffff
            return entries[h % entries.size]
        }
    }
}

/**
 * 画一份食物。
 *
 * @param eaten 被吃掉的比例（0..1）。先缩小、再变淡 ——
 *              这样「幽灵吃掉一半」在缩略图尺度上也看得出来。
 *              刻意不做逐帧动画：一屏十几张卡，动起来是灾难。
 */
@Composable
fun FoodIcon(food: Food, modifier: Modifier = Modifier, eaten: Float = 0f) {
    val e = eaten.coerceIn(0f, 1f)
    val scale = 1f - e * 0.28f
    val alpha = 1f - e * 0.75f

    Image(
        painter = painterResource(food.resId),
        contentDescription = food.label,
        modifier = modifier
            .scale(scale)
            .alpha(alpha.coerceAtLeast(0.12f)),
    )
}
