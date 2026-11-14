package cn.dkarbon.enroute.data.map

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import java.io.File

/** 地图包的问题类型（票 12 要求「校验失败 → 回退内置并提示，不许白屏」） */
enum class MapIssue {
    /** 正常 */
    NONE,

    /** 目录里没有 manifest.json */
    MISSING_MANIFEST,

    /** JSON 坏 / 顶层结构不对 */
    PARSE_ERROR,

    /** schema 不认（App 只认到 [MapManifest.SUPPORTED_SCHEMA]） */
    BAD_SCHEMA,

    /** bbox 缺失或不合法（min<max） */
    BAD_BBOX,

    /** layers 为空（没有逻辑层 = 画不出东西） */
    MISSING_LAYERS,

    /** manifest 指的 geojson 文件不存在 */
    MISSING_FILE,

    /** 要素不是 Polygon / 一个面都没有 */
    BAD_GEOMETRY,
}

enum class MapSource {
    /** `filesDir/map/`（用户可替换） */
    EXTERNAL,

    /** `assets/map/`（内置） */
    ASSET,
}

/**
 * 一个可渲染的地图包。
 *
 * [fallbackNote] 只在「外部包不可用、已回退内置」时非空 —— 界面必须把它显示出来，
 * 否则用户换了个坏包却看到旧图，会以为自己在看新数据。
 */
data class MapPackage(
    val manifest: MapManifest = MapManifest(),
    val features: List<MapFeature> = emptyList(),
    val source: MapSource = MapSource.ASSET,
    val issue: MapIssue = MapIssue.NONE,
    val fallbackNote: String = "",
    /** 底图文件名（相对 map 目录，真数据是 ase@1x.webp）；空 = 没有底图，只能纯矢量 */
    val baseFile: String = "",
    /** 源数据里 id 重复的次数（>0 时界面提示「建议重新导出」；解析层已临时加序号兜住） */
    val duplicateIds: Int = 0,
) {
    val ok: Boolean get() = issue == MapIssue.NONE && features.isNotEmpty()

    val hasBase: Boolean get() = baseFile.isNotBlank()

    /** 界面角落那一行：版本 + 来源（样例包必须一眼看出是样例） */
    val creditLine: String
        get() = listOf(
            if (manifest.version.isBlank()) "版本未知" else "版本 ${manifest.version}",
            if (manifest.generated.isBlank()) "" else "生成于 ${manifest.generated}",
            manifest.origin,
        ).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * 校验规则（**纯函数**，单测直接喂字符串打）。
 *
 * 与读文件的代码分开：这样「坏 schema / 缺文件 / 要素非 Polygon」三种失败不用装 Android 就能验。
 */
object MapPackageRules {

    fun validate(manifestText: String?, geoJsonText: String?): MapPackage {
        if (manifestText.isNullOrBlank()) {
            return MapPackage(issue = MapIssue.MISSING_MANIFEST)
        }
        val manifest = MapParse.manifest(manifestText)
            ?: return MapPackage(issue = MapIssue.PARSE_ERROR)
        if (manifest.schema != MapManifest.SUPPORTED_SCHEMA) {
            return MapPackage(manifest = manifest, issue = MapIssue.BAD_SCHEMA)
        }
        if (!manifest.isValidBox) {
            return MapPackage(manifest = manifest, issue = MapIssue.BAD_BBOX)
        }
        if (manifest.layers.isEmpty()) {
            return MapPackage(manifest = manifest, issue = MapIssue.MISSING_LAYERS)
        }
        if (geoJsonText.isNullOrBlank()) {
            return MapPackage(manifest = manifest, issue = MapIssue.MISSING_FILE)
        }
        val doc = MapParse.geoJson(geoJsonText)
            ?: return MapPackage(manifest = manifest, issue = MapIssue.PARSE_ERROR)
        // v1 契约：单文件、全部要素都是 Polygon
        if (doc.nonPolygon > 0 || doc.features.isEmpty()) {
            return MapPackage(manifest = manifest, features = doc.features, issue = MapIssue.BAD_GEOMETRY)
        }
        return MapPackage(
            manifest = manifest,
            features = doc.features,
            issue = MapIssue.NONE,
            baseFile = manifest.base?.file.orEmpty(),
            duplicateIds = doc.duplicateIds,
        )
    }

    /** 问题的人话（上屏用，别把枚举名给用户看） */
    fun describe(issue: MapIssue): String = when (issue) {
        MapIssue.NONE -> ""
        MapIssue.MISSING_MANIFEST -> "地图包里没有 manifest.json"
        MapIssue.PARSE_ERROR -> "地图包的 JSON 解析失败"
        MapIssue.BAD_SCHEMA -> "地图包契约版本不认识（本 App 只认 schema ${MapManifest.SUPPORTED_SCHEMA}）"
        MapIssue.BAD_BBOX -> "地图包的 bbox 缺失或不合法"
        MapIssue.MISSING_LAYERS -> "地图包没有声明任何图层"
        MapIssue.MISSING_FILE -> "manifest 里写的 GeoJSON 文件不存在"
        MapIssue.BAD_GEOMETRY -> "地图包的要素不是面（v1 只接受 Polygon）"
    }
}

/**
 * 读地图包：**先 `filesDir/map/`（可替换），坏了就回退 `assets/map/`（内置）**。
 *
 * 阻塞调用，必须在 IO 线程；[load] 内部不再切线程。
 */
class MapPackageLoader(private val ctx: Context) {

    fun load(): MapPackage {
        val external = runCatching { loadFrom(File(ctx.applicationContext.filesDir, "map"), MapSource.EXTERNAL) }
            .getOrNull()
        if (external != null && external.ok) {
            DebugLog.log("MAP", "使用外部地图包：${external.manifest.version}（${external.features.size} 面）")
            return external
        }
        val note = if (external == null || external.issue == MapIssue.MISSING_MANIFEST) "" else {
            "外部地图包不可用（${MapPackageRules.describe(external.issue)}），已回退内置地图"
        }
        val builtin = runCatching { loadFromAssets() }.getOrNull()
            ?: MapPackage(issue = MapIssue.MISSING_MANIFEST, fallbackNote = note)
        DebugLog.log(
            "MAP",
            "使用内置地图包：${builtin.manifest.version}（${builtin.features.size} 面）" +
                if (note.isNotBlank()) " · $note" else "",
        )
        return builtin.copy(fallbackNote = note.ifBlank { builtin.fallbackNote })
    }

    private fun loadFrom(dir: File, source: MapSource): MapPackage {
        val manifestText = runCatching {
            val f = File(dir, "manifest.json")
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }.getOrNull()
        val manifest = manifestText?.let { MapParse.manifest(it) }
        // 先粗读 manifest 拿到 geojson 文件名（manifest 坏就按约定名兜底）
        val geoName = manifest?.layers?.firstOrNull { it.file.isNotBlank() }?.file ?: "campus.geojson"
        val geoText = runCatching {
            val f = File(dir, geoName)
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }.getOrNull()
        val pkg = MapPackageRules.validate(manifestText, geoText)
        // 底图文件真的存在吗？（缺底图不算致命：退回纯矢量模式）
        val base = pkg.baseFile.takeIf { it.isNotBlank() && File(dir, it).isFile }.orEmpty()
        return pkg.copy(source = source, baseFile = base)
    }

    private fun loadFromAssets(): MapPackage {
        val assets = ctx.applicationContext.assets
        val manifestText = runCatching {
            assets.open("map/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        val manifest = manifestText?.let { MapParse.manifest(it) }
        val geoName = manifest?.layers?.firstOrNull { it.file.isNotBlank() }?.file ?: "campus.geojson"
        val geoText = runCatching {
            assets.open("map/$geoName").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        val pkg = MapPackageRules.validate(manifestText, geoText)
        fun exists(name: String) = runCatching {
            assets.open("map/$name").close(); true
        }.getOrDefault(false)
        val base = pkg.baseFile.takeIf { it.isNotBlank() && exists(it) }.orEmpty()
        return pkg.copy(source = MapSource.ASSET, baseFile = base)
    }

    /** 打开底图（供界面转 ImageBitmap）；读不到返回 null，界面自然退回纯矢量 */
    fun openBase(name: String) = runCatching {
        val ext = File(File(ctx.applicationContext.filesDir, "map"), name)
        if (ext.isFile) ext.inputStream() else ctx.applicationContext.assets.open("map/$name")
    }.getOrNull()
}
