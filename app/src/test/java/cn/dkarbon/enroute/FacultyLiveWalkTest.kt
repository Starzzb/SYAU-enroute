package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.faculty.FacultyParser
import cn.dkarbon.enroute.data.feed.FeedHttp
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 对真实学院官网跑一遍「栏目页 → 翻页/兄弟栏 → 详情页 → 抽正文」。
 *
 * 默认跳过：只有设了环境变量 `ENROUTE_LIVE=1` 才真的发请求。理由是这条测试打的是公网站点，
 * 不该在每次 `build-test` 里都跑，也不该在没网的环境里把构建拖红。
 *
 * 它和 `FacultyRepository` 用的是同一条路（BFS 队列 + 同一个预算上限），只是把 Android 的
 * 缓存层换成内存，用来证明「配置里没写副高栏、副高栏还分页」这两个坑确实被后来的代码填上了。
 */
class FacultyLiveWalkTest {

    private val name = "蒋卓东"
    private val start = "https://thxy.syau.edu.cn/szdw/jsxl/fgjzc.htm"

    @Test
    fun 土环副高栏翻到第二页能找到蒋卓东并抽出正文() {
        assumeTrue("实测默认跳过：设 ENROUTE_LIVE=1 才跑", System.getenv("ENROUTE_LIVE") == "1")

        val queue = ArrayDeque<String>()
        queue.addLast(start)
        val visited = mutableSetOf<String>()
        var budget = 10
        var detail: String? = null

        while (queue.isNotEmpty() && budget-- > 0 && detail == null) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            val resp = FeedHttp.get(url, 20_000)
            println("[LIVE] GET $url → ${resp.status} ${resp.body.length}B")
            assertTrue("$url 应返回 2xx，实际 ${resp.status}", resp.status in 200..299)

            val links = FacultyParser.findDetailLinks(resp.body, name, url)
            if (links.isNotEmpty()) {
                detail = links.first()
                break
            }
            queue += FacultyParser.pagination(resp.body, url)
            queue += FacultyParser.siblingColumns(resp.body, url)
        }

        val found = detail
        assertTrue("队列走完前应找到 $name 的详情页（实际访问过 $visited）", found != null)
        println("[LIVE] detail=$found")

        val page = FeedHttp.get(found!!, 20_000)
        assertTrue("详情页应返回 2xx，实际 ${page.status}", page.status in 200..299)
        val body = FacultyParser.extractBody(page.body)
        assertTrue("详情页应抽得出正文容器", body != null)
        val text = body!!.text
        println("[LIVE] 正文长度=${text.length}")
        println("[LIVE] 正文前 800 字：\n" + text.take(800))

        assertTrue("正文应含姓名", text.contains(name))
        assertTrue("正文应含研究相关段落", text.contains("研究"))
        // 2026-11-17 起不做隐私删改：官网公开的对外联系方式原样保留
        val images = FacultyParser.images(page.body, found!!, limit = 4)
        println("[LIVE] 正文图片数=${images.size}")
        images.forEach { println("[LIVE]   img $it") }
        assertTrue("应能抽出至少一张正文图片", images.isNotEmpty())
    }
}
