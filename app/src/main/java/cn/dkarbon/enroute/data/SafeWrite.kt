package cn.dkarbon.enroute.data

import java.io.File
import java.io.FileOutputStream

/**
 * 同目录原子写：`.tmp` → 落盘 → 改名，改名失败就回滚旧文件。
 *
 * 为什么必须统一（2026-11-27）：以前各 Store 各写各的，其中几处是
 * 「先删旧文件，再 rename」，[TimetableStore] 更是直接覆盖写 —— 断电、进程被杀、
 * 或写入途中手机没电，留下的就是一个**空文件或半截 JSON**。下次启动解析失败，
 * 上层拿到 null，界面显示「还没有课表」，而用户其实刚导过课表。
 *
 * 做法与 [androidx.core.util.AtomicFile] 同思路，但不引依赖、并多做两件事：
 * ① `fd.sync()`：只写进页缓存不算落盘，掉电就没了；
 * ② 保留一份 `.bak`：改名失败（跨文件系统、权限、SELinux 偶尔会拒）时能退回旧内容。
 */
internal object SafeWrite {

    /** 写文本。返回是否成功；失败时**旧文件保持原样**。 */
    fun text(file: File, text: String): Boolean {
        val dir = file.parentFile ?: return false
        if (!dir.exists() && !dir.mkdirs()) return false
        val tmp = File(dir, file.name + ".tmp")
        val bak = File(dir, file.name + ".bak")
        return runCatching {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            // 先挪开旧文件：rename 在同目录是原子的，但「覆盖」不是
            if (file.exists()) {
                bak.delete()
                if (!file.renameTo(bak)) {
                    // 挪不动就不动它 —— 直接改名覆盖，失败也不会毁掉旧内容
                    bak.delete()
                }
            }
            val ok = tmp.renameTo(file)
            if (ok) {
                bak.delete()
            } else {
                if (bak.exists() && !file.exists()) bak.renameTo(file)
                tmp.delete()
            }
            ok
        }.getOrDefault(false)
    }

    /**
     * 读文本，主文件缺失或为空时退回 `.bak`。
     *
     * 「为空」也算缺失：一次写坏的半截文件就是空文件，退回备份比返回空串诚实。
     */
    fun read(file: File): String? {
        val main = runCatching { if (file.isFile) file.readText() else null }.getOrNull()
        if (!main.isNullOrBlank()) return main
        val bak = File(file.parentFile, file.name + ".bak")
        return runCatching { if (bak.isFile) bak.readText() else null }.getOrNull()
    }

    /** 删主文件时连备份一起删（清缓存/清数据用，别留下能被 read() 复活的残片） */
    fun delete(file: File): Boolean {
        File(file.parentFile, file.name + ".bak").delete()
        File(file.parentFile, file.name + ".tmp").delete()
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }
}
