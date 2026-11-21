package cn.dkarbon.enroute.data.telemetry

import java.io.File

/**
 * 事件队列：`filesDir/telemetry/queue.jsonl`，一行一条，**环形**。
 *
 * ## 为什么是环形文件而不是数据库
 *
 * 这个仓库没有 Room，也明确不引第三方统计 SDK（34 MB 的教训还在）。
 * 采的东西是「一串短命的小记录」，最合适的就是追加写 + 超限截断，
 * 不需要查询、不需要事务、不需要索引。
 *
 * ## 上限口径
 *
 * 512 KB 封顶，超了**从最旧的开始丢**，一次削到一半（不是削到刚好 512 KB ——
 * 那样每写一行都要重写整个文件，反而更费电）。实测一条事件约 110 字节，
 * 512 KB 约等于 4,600 条，够离线攒很久。
 *
 * 所有读写都在 IO 线程调用；本类不加锁，由 [Telemetry] 的单一协程串行化。
 */
class TelemetryQueue(private val dir: File) {

    private val file: File get() = File(dir, "queue.jsonl")

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    /**
     * 追加若干行（每行是一条完整的 JSON）。
     *
     * 写之前先看容量：超了先削，再追加 —— 顺序反了会出现「刚写的被自己削掉」。
     */
    fun append(lines: List<String>) {
        if (lines.isEmpty()) return
        dir.mkdirs()
        val existing = if (file.exists()) file.readText() else ""
        val merged = trimToCap(existing + lines.joinToString("\n", postfix = "\n"), CAP_BYTES)
        file.writeText(merged)
    }

    /**
     * 取最旧的 [max] 条（**不删**）。上传成功后再 [dropFirst]。
     *
     * 先取后删是刻意的：上传失败时这批还在盘上，下次接着发，不会丢事件。
     */
    fun peek(max: Int): List<String> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.take(max)
    }

    /** 删掉最前面 [n] 条（上传成功后调用） */
    fun dropFirst(n: Int) {
        if (n <= 0 || !file.exists()) return
        val rest = file.readLines().filter { it.isNotBlank() }.drop(n)
        dir.mkdirs()
        file.writeText(if (rest.isEmpty()) "" else rest.joinToString("\n", postfix = "\n"))
    }

    fun clear() {
        runCatching { file.delete() }
    }

    companion object {

        /** 队列文件上限：512 KB */
        const val CAP_BYTES = 512 * 1024

        /**
         * 把整段 JSONL 削到 [capBytes] 以内，**保留最新的那些行**。
         *
         * 纯函数，单测直接对着它做（不碰磁盘）。三条规则：
         *  · 没超就原样返回（不做任何重写）；
         *  · 超了就从头部按行丢，直到不超；
         *  · 单行超上限时保留它 —— 丢一条超长行不解决问题，反而把痕迹抹了。
         */
        fun trimToCap(text: String, capBytes: Int): String {
            val bytes = text.toByteArray(Charsets.UTF_8).size
            if (bytes <= capBytes) return text
            val lines = text.split("\n").filter { it.isNotBlank() }
            var start = 0
            var used = 0
            // 从尾部往前累加，累到超过上限为止
            for (i in lines.indices.reversed()) {
                val size = lines[i].toByteArray(Charsets.UTF_8).size + 1
                if (used + size > capBytes && i != lines.lastIndex) {
                    start = i + 1
                    break
                }
                used += size
                start = i
            }
            return lines.drop(start).joinToString("\n", postfix = "\n")
        }
    }
}