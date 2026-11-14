package cn.dkarbon.enroute.ui.map

/**
 * 地图「持续开发中 + 共建引导」的**文案**（票 12 §8，纯函数，单测直接打）。
 *
 * 为什么抽出来：这三句话里的数字必须**从实际数据算**（主人明确要求「不许写死」），
 * 而且有三种状态（一个都没补 / 补了一部分 / 全齐）。写在 Composable 里只能上真机看，
 * 抽成纯函数就能拿单测钉住 —— 免得哪天顺手把 90 改成常量，界面开始骗人。
 *
 * 文案与 §8.1 一字不差，**不要自己改词**。
 */
object MapNudge {

    /** 提示条右侧的入口文案 */
    const val ACTION = "怎么补？"

    /**
     * 页内常驻提示条（不可关闭）。
     *
     * @param total  载入的 GeoJSON 要素总数
     * @param named  `name` 非空的要素数
     * @param kinded `kind` 非空的要素数
     */
    fun line(total: Int, named: Int, kinded: Int): String {
        if (total <= 0) return "地图仍在开发中：这一版还没有地块数据。"
        val n = named.coerceIn(0, total)
        val k = kinded.coerceIn(0, total)
        // 两个都补齐 → 降级为「感谢 + 欢迎纠错」，不再催促
        if (n >= total && k >= total) {
            return "地图仍在开发中：地块信息已由大家补全，如有错漏欢迎修正"
        }
        return "地图仍在开发中：$total 个地块中仅 $n 个有名称、$k 个有类别。欢迎一起补全"
    }

    /** 引导卡标题 / 副标题 / 五步 / 隐私小字（§8.2 原文） */
    const val GUIDE_TITLE = "帮大家补地块信息"
    const val GUIDE_SUBTITLE = "地图还在完善，你补的每一条都会让下一个人少走冤枉路。"
    const val GUIDE_FOOTNOTE =
        "你的标注先只存在本机；点「导出标注」并发送后才会离开手机。不含任何个人信息。"

    /** 五步：编号标题 + 一句说明（别写成长文） */
    val GUIDE_STEPS: List<Pair<String, String>> = listOf(
        "点中地块" to "在地图上点任何一块（教学楼、场馆、操场、绿地都行），底部会弹出编辑面板。",
        "填名称、选类别" to "名称写楼名（如「第二教学楼」）；类别决定它的颜色和图例分组。",
        "不够就加字段" to "点「＋ 添加字段」，可以加 层数／所属单位／开放时间 这类信息；" +
            "字段名是所有地块共用的，你加一次，别的楼也会多这一列（值留空）。",
        "改完自动保存" to "不用点「保存」，退出 App 也不会丢。",
        "想帮到所有人" to "点「导出标注」，把生成的 .geojson 发给开发者，下一版地图就会带上你的标注。",
    )

    /** 「开始标注」之后的一句提示（关掉卡片时给用户一个下一步） */
    const val START_HINT = "去点地图上任意一块"
}
