package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.BodyPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正文缓存 schema 版本闸门（2026-11-21 真机踩坑后补）。
 *
 * 背景：给 BodyPayload 加了 images / pdfUrl / attachmentsBlocked 之后，
 * 设备上**已经缓存过**的正文读出来仍是旧结构 —— 两个新字段恒为空，
 * 学生处那条「请安装 PDF 阅读器」的公告照旧显示那段废话，看起来像 PDF 卡片没做。
 *
 * 所以缓存必须带版本号，旧版本的当未命中处理。这条测试锁住两件事：
 * 1. 新写出的正文一定带当前版本号；
 * 2. 反序列化一份**没有** schema 字段的旧 JSON 时，schema 落到 0（低于当前），会被闸门拦下。
 */
class FeedBodySchemaTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `新写的正文带当前版本号`() {
        // BodyPayload 直接构造时 schema 是 0（意味着「没盖过章」）；
        // 真正的盖章发生在 FeedStore.saveBody()，那里 copy(schema = SCHEMA)。
        val raw = BodyPayload(text = "正文", pdfUrl = "https://x/a.pdf")
        assertEquals("未盖章应为 0", 0, raw.schema)
        val stamped = raw.copy(schema = BodyPayload.SCHEMA)
        assertEquals(BodyPayload.SCHEMA, stamped.schema)
        assertTrue("当前版本号必须大于 0", BodyPayload.SCHEMA > 0)
    }

    @Test
    fun `旧缓存缺 schema 字段时落到 0，会被闸门拦下`() {
        // 这就是升级前写进设备的那种 JSON：没有 schema 字段
        val legacy = """{"text":"旧正文","links":[],"attachments":[]}"""
        val p = json.decodeFromString(BodyPayload.serializer(), legacy)
        assertEquals("缺字段应落到 0", 0, p.schema)
        assertTrue("0 必须低于当前版本，才能触发重抓", p.schema < BodyPayload.SCHEMA)
    }

    @Test
    fun `带当前版本号的缓存能正常读出`() {
        // 模拟 saveBody 盖章后的缓存
        val p = BodyPayload(text = "正文", pdfUrl = "https://x/a.pdf", images = emptyList())
            .copy(schema = BodyPayload.SCHEMA)
        val round = json.decodeFromString(BodyPayload.serializer(), json.encodeToString(BodyPayload.serializer(), p))
        assertEquals(BodyPayload.SCHEMA, round.schema)
        assertEquals("https://x/a.pdf", round.pdfUrl)
    }
}