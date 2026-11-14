package cn.dkarbon.enroute.data.map

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import java.io.File

/**
 * 标注落盘：`filesDir/map/edits.json`（**绝不写 assets**）。
 *
 * 三条纪律：
 * 1. **原子写**：`.tmp` → rename，掉电/被杀不会留下半个 JSON；
 * 2. 只在 IO 线程调用（ViewModel 统一包 `withContext(Dispatchers.IO)`）；
 * 3. 版本变了也**不丢标注**：按 `id` 合并，找不到的 id 留在文件里（见 [MapEditsOps.orphans]）。
 */
class MapEditsStore(ctx: Context) {

    private val dir = File(ctx.applicationContext.filesDir, "map")
    private val file = File(dir, "edits.json")
    private val exportDir = File(dir, "export")

    fun read(): MapEdits? {
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8) }
            .getOrNull()
            ?.let { MapEditsCodec.fromJson(it) }
    }

    fun write(edits: MapEdits): Boolean = runCatching {
        dir.mkdirs()
        val tmp = File(dir, "edits.json.tmp")
        tmp.writeText(MapEditsCodec.toJson(edits), Charsets.UTF_8)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }.getOrDefault(false)

    fun path(): String = file.absolutePath

    /**
     * 导出到 `filesDir/map/export/`：
     * `campus_annotated_<版本>_<yyyyMMdd-HHmm>.geojson` 与同名 `.csv`（UTF-8 BOM）。
     *
     * @return 两个文件的路径 + 内容（内容同时给界面用 ACTION_CREATE_DOCUMENT 另存）
     */
    data class ExportResult(
        val geojsonPath: String = "",
        val csvPath: String = "",
        val geojsonName: String = "",
        val csvName: String = "",
        val geojson: String = "",
        val csv: String = "",
        val featureCount: Int = 0,
        val error: String = "",
    )

    fun export(
        features: List<MapFeature>,
        edits: MapEdits,
        version: String,
        stamp: String,
    ): ExportResult {
        val geoName = MapExport.fileName(version, stamp, "geojson")
        val csvName = MapExport.fileName(version, stamp, "csv")
        val geo = MapExport.geoJson(features, edits)
        val csv = MapExport.csv(features, edits)
        val ok = runCatching {
            exportDir.mkdirs()
            File(exportDir, geoName).writeText(geo, Charsets.UTF_8)
            File(exportDir, csvName).writeText(csv, Charsets.UTF_8)
            true
        }.getOrDefault(false)
        if (!ok) DebugLog.error("MAP", "导出写盘失败：$exportDir")
        return ExportResult(
            geojsonPath = File(exportDir, geoName).absolutePath,
            csvPath = File(exportDir, csvName).absolutePath,
            geojsonName = geoName,
            csvName = csvName,
            geojson = geo,
            csv = csv,
            featureCount = features.size,
            error = if (ok) "" else "导出文件写不进 $exportDir",
        )
    }

    /** 「清除所有数据」连带清空标注与导出（票 12 边界的自然延伸） */
    fun clear(): Boolean = runCatching {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }
        true
    }.getOrDefault(false)

    fun count(): Int = runCatching { read()?.features?.size ?: 0 }.getOrDefault(0)
}
