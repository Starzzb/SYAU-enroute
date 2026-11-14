package cn.dkarbon.enroute.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapEditsOps
import cn.dkarbon.enroute.data.map.MapFeature
import cn.dkarbon.enroute.data.map.MapGeometry
import cn.dkarbon.enroute.data.map.MapHit
import cn.dkarbon.enroute.data.map.MapLayers
import cn.dkarbon.enroute.data.map.MapPackage
import cn.dkarbon.enroute.data.map.MapProjection
import cn.dkarbon.enroute.data.map.MapTransform
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke as StrokeTokens
import kotlin.math.roundToInt

/**
 * 校园地图 v1（票 12）—— Compose Canvas 自绘，**不引地图库**、不打网络、不做 GPS。
 *
 * ## 两件事
 * 1. **看图**：默认 B 模式（`base@1x.webp` 当底 + 极淡热区）；A 模式纯矢量（按要素 `color`/`colorGroup` 上色）。
 *    热区的 alpha / 描边全部读 `manifest.style`，**不写死**。
 * 2. **标注**（本票核心）：点要素 → 底部可编辑面板（名称 / 类别 / **自定义字段**）。
 *    改一次存一次，落 `filesDir/map/edits.json`；「导出标注」把 asset + edits 合并成 GeoJSON + CSV。
 *
 * ## 三个已踩过的坑（都在数据层处理）
 * y 轴翻转 [MapTransform.screenY]、等比映射 [MapProjection.fit]、小面点不中 [MapHit.pickOrNearest] 的 24dp 兜底。
 */
@Composable
fun CampusMapScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val density = LocalDensity.current

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showLegend by remember { mutableStateOf(true) }
    var vectorOnly by remember { mutableStateOf(false) }
    var baseBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var addingField by remember { mutableStateOf(false) }
    var deletingField by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (ui.mapPackage == null) vm.openCampusMap() }

    // 底图位图（只读一次；换包时重读）
    LaunchedEffect(ui.mapPackage?.manifest?.version, ui.mapPackage?.baseFile) {
        val name = ui.mapPackage?.baseFile.orEmpty()
        baseBitmap = if (name.isBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                cn.dkarbon.enroute.data.map.MapPackageLoader(ctx.applicationContext).openBase(name)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    // 导出：ACTION_CREATE_DOCUMENT 另存（GeoJSON / CSV 各一个）
    fun writeTo(uri: android.net.Uri?, content: String) {
        if (uri == null || content.isEmpty()) return
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }
    }
    val saveGeo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri -> writeTo(uri, ui.mapExportGeoJson) }
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> writeTo(uri, ui.mapExportCsv) }

    val pkg = ui.mapPackage
    val manifest = pkg?.manifest
    val selected = pkg?.features?.firstOrNull { it.id == ui.mapSelectedId }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
        // ── 顶栏 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("校园地图", fontSize = 14.sp, color = t.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(Space.s))
            Text(
                if (pkg != null && pkg.hasBase && !vectorOnly) "底图+热区" else "纯矢量",
                fontSize = 9.5.sp,
                color = t.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            SmallButton(if (showLegend) "收起图例" else "图例") { showLegend = !showLegend }
            Spacer(Modifier.width(Space.xs))
            SmallButton(if (vectorOnly) "用底图" else "用矢量") { vectorOnly = !vectorOnly }
        }

        // 版本与来源：**常显**，样例/真数据一眼可辨
        Text(
            pkg?.creditLine ?: ui.mapMsg,
            fontSize = 9.sp,
            lineHeight = 13.sp,
            color = t.inkFaint,
            modifier = Modifier.padding(horizontal = Space.l, vertical = 1.dp),
        )
        if (ui.mapMsg.isNotBlank() && pkg != null) {
            Text(
                ui.mapMsg,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = t.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 1.dp),
            )
        }
        // 源数据 id 重复：临时的序号已兜住，但要让主人知道该重新导出
        if ((pkg?.duplicateIds ?: 0) > 0) {
            Text(
                "注意：这份地图里有  个要素 id 重复（已临时加序号区分）；" +
                    "建议重新导出，让每个面有唯一 id",
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = t.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 1.dp),
            )
        }
        // 孤立标注：换图后 id 对不上 —— 绝不静默丢弃
        if (ui.mapOrphans.isNotEmpty()) {
            Text(
                "有 ${ui.mapOrphans.size} 条标注在新版地图里找不到对应要素（${ui.mapOrphans.take(4).joinToString("、")}" +
                    if (ui.mapOrphans.size > 4) "…" else "" + "）—— 已保留，不会被删",
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = t.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 1.dp),
            )
        }

        Box(Modifier.fillMaxWidth().height(StrokeTokens.hairline).background(t.rule))

        // ── 地图区：高度锁成「宽度 ÷ 图幅宽高比」，不再撑满屏幕 ──
        // 竖屏把 1024×648 塞满整块剩余高度会上下各留一大片空白，所以按图幅比例定高，
        // 省下来的纵向空间给「图例」和一行操作提示。取 min()：宁可少留白，不让图溢出。
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val aspect = remember(manifest) {
                val b = manifest?.bbox.orEmpty()
                if (b.size == 4 && b[3] > b[1]) ((b[2] - b[0]) / (b[3] - b[1])).toFloat()
                else 1024f / 648f
            }
            val reserved = if (showLegend) 92.dp else 20.dp
            val mapH = minOf(maxWidth / aspect, (maxHeight - reserved).coerceAtLeast(140.dp))
            val cw = with(density) { maxWidth.toPx() }
            val ch = with(density) { mapH.toPx() }

            val base = remember(manifest, cw, ch) {
                MapProjection.fit(manifest?.bbox.orEmpty(), cw, ch, padding = 2f)
            }
            val transform = MapProjection.withUser(base, zoom.toDouble(), panX, panY, cw, ch)
            val features = pkg?.features.orEmpty()
            // 提示条的三个数字必须从**合并后的实际数据**算（标注也算进去，票 12 §8.1）
            val mergedRows = remember(pkg, ui.mapEdits) {
                MapEditsOps.merged(pkg?.features.orEmpty(), ui.mapEdits)
            }
            val paths = remember(pkg) { features.map { it to buildPath(it) } }
            val groups = remember(manifest, pkg) { manifest?.let { MapLayers.split(it, features) } }

            val maxPanX = cw * (zoom - 1f) / 2f
            val maxPanY = ch * (zoom - 1f) / 2f
            fun clampPan() {
                panX = panX.coerceIn(-maxPanX, maxPanX)
                panY = panY.coerceIn(-maxPanY, maxPanY)
            }

            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(mapH)) {
                    Canvas(
                        Modifier.fillMaxSize().background(t.paper)
                            .pointerInput(pkg) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                    panX += pan.x
                                    panY += pan.y
                                    clampPan()
                                }
                            }
                            .pointerInput(pkg, zoom, panX, panY) {
                                detectTapGestures(
                                    onTap = { pos ->
                                        val visible = features.filter {
                                            MapGeometry.visibleInScreen(it, transform, cw, ch)
                                        }
                                        val hit = MapHit.pickOrNearest(
                                            candidates = visible,
                                            x = transform.sourceX(pos.x),
                                            y = transform.sourceY(pos.y),
                                            transform = transform,
                                            maxScreenDistance = with(density) { 24.dp.toPx().toDouble() },
                                        )
                                        vm.selectMapFeature(hit?.id.orEmpty())   // 空白 → 清空选中
                                    },
                                    onDoubleTap = {
                                        zoom = (zoom * 2f).coerceAtMost(8f)
                                        clampPan()
                                    },
                                )
                            },
                    ) {
                        // ① 底图（B 模式）；缩放 >2.5× 淡出到 0.35，避免糊
                        val bmp = baseBitmap
                        if (bmp != null && !vectorOnly) {
                            val alpha = if (zoom > 2.5f) 0.35f else 1f
                            val w = (manifest?.bbox?.let { it[2] - it[0] } ?: 1.0) * transform.scale
                            val h = (manifest?.bbox?.let { it[3] - it[1] } ?: 1.0) * transform.scale
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset(transform.offsetX.roundToInt(), transform.offsetY.roundToInt()),
                                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                                alpha = alpha,
                            )
                        }
                        // ② 矢量层：热区 / 选中 / 洞都由 EvenOdd 路径处理
                        drawFeatures(
                            paths = paths,
                            groups = groups,
                            selectedId = ui.mapSelectedId,
                            hasBitmap = bmp != null && !vectorOnly,
                            transform = transform,
                            theme = t,
                            style = manifest?.style ?: cn.dkarbon.enroute.data.map.MapStyle(),
                        )
                    }

                    // 浮标（贴在地图块内部，不占版面高度）
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(start = Space.m, bottom = Space.s)
                            .clip(RectangleShape).background(t.card).border(StrokeTokens.hairline, t.outline)
                            .clickable {
                                zoom = 1f; panX = 0f; panY = 0f; vm.clearMapSelection()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text("重置视图", fontSize = 10.5.sp, color = t.inkSoft) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(end = Space.m, bottom = Space.s)
                            .background(t.card).border(StrokeTokens.hairline, t.outline)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text("${"%.1f".format(zoom)}×", fontSize = 10.sp, color = t.inkFaint, fontFamily = NumericFont)
                    }

                    // 包坏了：绝不白屏
                    if (pkg != null && !pkg.ok) {
                        Box(
                            Modifier.align(Alignment.Center).padding(Space.xl)
                                .background(t.card).border(1.dp, t.outline).padding(Space.m),
                        ) {
                            Text(
                                "地图包读不出来：${cn.dkarbon.enroute.data.map.MapPackageRules.describe(pkg.issue)}\n" +
                                    "已回退到内置地图；若内置也不可用，请重装。",
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = t.inkSoft,
                            )
                        }
                    }
                }

                // ── 图例（顶栏按钮控显示/隐藏，默认显示）──
                // 只列「本图实际出现过的 kind」；一个都没有时说明原因，别显示空框。
                if (showLegend) {
                    val present = features.filter { it.kind.isNotBlank() }.groupBy { it.kind }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp)
                            .heightIn(max = 84.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("图例", fontSize = 10.sp, color = t.ink, fontWeight = FontWeight.Bold)
                        if (present.isEmpty()) {
                            Text(
                                "本图还没有标类别 —— 点要素加类别后会出现在这里",
                                fontSize = 9.5.sp,
                                lineHeight = 14.sp,
                                color = t.inkFaint,
                            )
                        } else {
                            present.entries.sortedByDescending { it.value.size }.chunked(2).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                                ) {
                                    row.forEach { (k, list) ->
                                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.size(9.dp)
                                                    .background(parseColor(list.firstOrNull()?.color.orEmpty(), t.cardAlt))
                                                    .border(StrokeTokens.hairline, t.outline),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "${kindLabel(k, manifest)} ${list.size}",
                                                fontSize = 9.sp,
                                                color = t.inkSoft,
                                            )
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // ── 一行操作提示 ──
                Text(
                    "双指缩放 · 单指拖动 · 点击要素编辑属性",
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    color = t.inkFaint,
                    modifier = Modifier.padding(start = Space.l, end = Space.l, bottom = 2.dp),
                )

                // ── 常驻提示条（票 12 §8.1：不可关闭；数字从实际数据算，不许写死）──
                MapNudgeBar(
                    total = mergedRows.size,
                    named = mergedRows.count { it.name.isNotBlank() },
                    kinded = mergedRows.count { it.kind.isNotBlank() },
                    hint = ui.mapGuideHint,
                    onClick = { vm.openMapGuide() },
                )
            }
        }

        // ── 属性面板（可编辑，票 12 §7）──
        if (selected != null && manifest != null) {
            PropertyPanel(
                feature = selected,
                edits = ui.mapEdits,
                kindOptions = manifest.kindOptions(),
                onChangeName = { vm.setMapFeatureName(selected.id, it) },
                onChangeKind = { vm.setMapFeatureKind(selected.id, it) },
                onChangeField = { f, v -> vm.setMapFeatureField(selected.id, f, v) },
                onAddField = { addingField = true },
                onDeleteField = { deletingField = it },
                onClose = { vm.clearMapSelection() },
            )
        }

        // ── 导出 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallButton("导出标注") { vm.exportMapAnnotations() }
            Spacer(Modifier.width(Space.s))
            if (ui.mapExportGeoJson.isNotEmpty()) {
                SmallButton("另存 GeoJSON") { saveGeo.launch(ui.mapExportGeoName) }
                Spacer(Modifier.width(Space.xs))
                SmallButton("另存 CSV") { saveCsv.launch(ui.mapExportCsvName) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "已标注 ${ui.mapEdits.features.size} 个面",
                fontSize = 9.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
            )
        }
        if (ui.mapExportMsg.isNotBlank()) {
            Text(
                ui.mapExportMsg,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(horizontal = Space.l).padding(bottom = 4.dp),
            )
        }
        }

        // 共建引导卡（票 12 §8.2）：首次进地图页自动弹一次，「怎么补？」随时可开
        if (ui.mapGuideVisible) {
            MapGuideCard(
                onDismiss = { vm.closeMapGuide(false) },
                onStart = { vm.closeMapGuide(true) },
            )
        }
    }

    // 加字段对话框
    if (addingField) {
        AddFieldDialog(
            existing = ui.mapEdits.fieldOrder,
            onDismiss = { addingField = false },
            onConfirm = { name ->
                vm.addMapField(name)
                addingField = false
            },
        )
    }
    deletingField?.let { field ->
        AlertDialog(
            onDismissRequest = { deletingField = null },
            title = { Text("删除字段「$field」？", fontSize = 14.sp) },
            text = { Text("会连带删掉所有要素这一列的值，不能撤销。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeMapField(field)
                    deletingField = null
                }) { Text("删除", fontSize = 12.sp) }
            },
            dismissButton = { TextButton(onClick = { deletingField = null }) { Text("取消", fontSize = 12.sp) } },
        )
    }
}

// ── 属性面板 ────────────────────────────────────────────────

@Composable
private fun PropertyPanel(
    feature: MapFeature,
    edits: MapEdits,
    kindOptions: List<Pair<String, String>>,
    onChangeName: (String) -> Unit,
    onChangeKind: (String) -> Unit,
    onChangeField: (String, String) -> Unit,
    onAddField: () -> Unit,
    onDeleteField: (String) -> Unit,
    onClose: () -> Unit,
) {
    val t = InkTokens.current
    val row = MapEditsOps.rowOf(feature, edits)
    var name by remember(feature.id) { mutableStateOf(row.name.ifBlank { feature.name }) }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 340.dp)
            .background(t.card)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name.ifBlank { "未命名要素" },
                fontSize = 13.sp,
                color = t.ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("id ${feature.id}", fontSize = 9.sp, color = t.inkFaint, fontFamily = NumericFont)
            Spacer(Modifier.width(Space.s))
            Text("✕", fontSize = 12.sp, color = t.inkFaint, modifier = Modifier.clickable(onClick = onClose))
        }

        // 名称
        LabeledInput(
            label = "名称",
            value = name,
            singleLine = true,
            onChange = {
                name = it
                onChangeName(it)
            },
        )

        // 类别（19 个码，从 manifest.kindLabels 来）
        Text("类别", fontSize = 10.sp, color = t.inkFaint)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(kindOptions, key = { it.first }) { (code, label) ->
                val on = row.kind == code
                Box(
                    Modifier.clip(RectangleShape)
                        .background(if (on) t.cardAlt else t.paper)
                        .border(if (on) 1.5.dp else StrokeTokens.hairline, if (on) t.primary else t.outline)
                        .clickable { onChangeKind(if (on) "" else code) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(label, fontSize = 10.5.sp, color = if (on) t.ink else t.inkSoft)
                }
            }
        }

        // 自定义字段（全局共享）：长按字段名可删
        edits.fieldOrder.forEach { field ->
            LabeledInput(
                label = field,
                value = row.fields.firstOrNull { it.first == field }?.second.orEmpty(),
                singleLine = field.length > 6,
                onLongPressLabel = { onDeleteField(field) },
                onChange = { onChangeField(field, it) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton("＋ 添加字段") { onAddField() }
            Spacer(Modifier.weight(1f))
            Text(
                "字段名全局共享（所有要素同一列）",
                fontSize = 8.5.sp,
                color = t.inkFaint,
            )
        }
        // 票 12b 的入口：本票不做，说明清楚，不造假数据
        Box(
            Modifier.fillMaxWidth().border(StrokeTokens.hairline, t.outline).padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("这栋楼今天的课（票 12b 待接入）", fontSize = 9.5.sp, color = t.inkFaint)
        }
    }
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    singleLine: Boolean,
    onChange: (String) -> Unit,
    onLongPressLabel: (() -> Unit)? = null,
) {
    val t = InkTokens.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 9.5.sp,
                color = t.inkFaint,
                modifier = (if (onLongPressLabel != null) {
                    Modifier.combinedLongClick(onLongPressLabel)
                } else Modifier).padding(vertical = 2.dp),
            )
            if (onLongPressLabel != null) {
                Spacer(Modifier.width(4.dp))
                Text("长按可删字段", fontSize = 8.sp, color = t.inkFaint)
            }
        }
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = t.ink),
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = t.paper,
                unfocusedContainerColor = t.paper,
                focusedIndicatorColor = t.primary,
                unfocusedIndicatorColor = t.outline,
                cursorColor = t.primary,
            ),
        )
    }
}

/** 长按手势（Compose 没有现成的 combinedClickable 在非 material 组件上，这里手搓一个） */
private fun Modifier.combinedLongClick(onLongClick: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(onLongClick) {
            detectTapGestures(onLongPress = { onLongClick() })
        },
    )

@Composable
private fun AddFieldDialog(existing: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val dup = existing.contains(text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加字段", fontSize = 14.sp) },
        text = {
            Column {
                Text("字段名会加到所有要素上（同一列，值可为空）", fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it.take(12) },
                    singleLine = true,
                    placeholder = { Text("如 层数 / 所属单位 / 教室号", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                )
                if (dup) {
                    Spacer(Modifier.height(4.dp))
                    Text("已经有这个字段了", fontSize = 10.sp, color = Color(0xFFC74E17))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank() && !dup) {
                Text("添加", fontSize = 12.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontSize = 12.sp) } },
    )
}

// ── 绘制 ────────────────────────────────────────────────────

private fun buildPath(f: MapFeature): Path {
    val p = Path()
    f.rings.forEach { ring ->
        if (ring.size < 3) return@forEach
        p.moveTo(ring[0].x.toFloat(), ring[0].y.toFloat())
        for (i in 1 until ring.size) p.lineTo(ring[i].x.toFloat(), ring[i].y.toFloat())
        p.close()
    }
    p.fillType = PathFillType.EvenOdd   // 洞自动挖空
    return p
}

private fun DrawScope.drawFeatures(
    paths: List<Pair<MapFeature, Path>>,
    groups: MapLayers.Result?,
    selectedId: String,
    hasBitmap: Boolean,
    transform: MapTransform,
    theme: InkTokenSet,
    style: cn.dkarbon.enroute.data.map.MapStyle,
) {
    val minX = transform.bbox[0]
    val maxY = transform.bbox[3]
    // 层自带配色（可能没有）→ 兜底用要素自身的 color（矢量化聚类色）
    val layerFill = groups?.groups?.firstOrNull()?.layer?.fill.orEmpty()
    val layerStroke = groups?.groups?.firstOrNull()?.layer?.stroke.orEmpty()

    withTransform({
        translate(transform.offsetX.toFloat(), transform.offsetY.toFloat())
        scale(transform.scale.toFloat(), -transform.scale.toFloat(), pivot = Offset.Zero)
        translate(-minX.toFloat(), -maxY.toFloat())
    }) {
        val strokeSrc = (style.strokeWidth * 2f / transform.scale).toFloat().coerceAtLeast(0.05f)
        val strokeSel = style.selectedStrokeWidth.toFloat() / transform.scale.toFloat()
        paths.forEach { (f, path) ->
            val isSel = f.id == selectedId
            if (hasBitmap) {
                // B 模式：统一淡色热区，透出底图
                val fillColor = parseColor(style.fill, theme.cardAlt)
                val strokeColor = parseColor(layerStroke.ifBlank { style.stroke }, theme.outline)
                if (isSel) {
                    drawPath(path, color = fillColor.copy(alpha = style.selectedFillAlpha))
                    drawPath(
                        path,
                        color = strokeColor.copy(alpha = style.selectedStrokeAlpha),
                        style = Stroke(width = strokeSel),
                    )
                } else {
                    drawPath(path, color = fillColor.copy(alpha = style.fillAlpha))
                    drawPath(
                        path,
                        color = strokeColor.copy(alpha = style.strokeAlpha),
                        style = Stroke(width = strokeSrc),
                    )
                }
            } else {
                // A 模式：按要素自己的颜色（真数据每条都带 #RRGGBB）
                val baseColor = parseColor(f.color.ifBlank { layerFill }, theme.cardAlt)
                if (isSel) {
                    drawPath(path, color = baseColor)
                    drawPath(path, color = parseColor(style.stroke, theme.outline), style = Stroke(width = strokeSel))
                } else {
                    drawPath(path, color = baseColor.copy(alpha = 0.9f))
                    drawPath(path, color = parseColor(style.stroke, theme.outline).copy(alpha = 0.45f), style = Stroke(width = strokeSrc))
                }
            }
        }
    }
}

// ── 小组件 ──────────────────────────────────────────────────

/**
 * 页内常驻提示条（票 12 §8.1）：**不可关闭**。
 *
 * 数据诚实的一条：三个数字全从当前载入的数据算（含用户标注），没有一个是写死的常量。
 * 点整条或右侧「怎么补？」都打开引导卡。
 */
@Composable
private fun MapNudgeBar(
    total: Int,
    named: Int,
    kinded: Int,
    hint: String,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = Space.l, vertical = 4.dp)
            .background(t.cardAlt)
            .border(StrokeTokens.hairline, t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                MapNudge.line(total, named, kinded),
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = t.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                MapNudge.ACTION,
                fontSize = 9.5.sp,
                color = t.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 9.sp, color = t.primary)
        }
    }
}

/** 共建引导卡（票 12 §8.2）：五步 + 隐私小字 + 两个按钮 */
@Composable
private fun MapGuideCard(onDismiss: () -> Unit, onStart: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.l)
                .background(t.card).border(1.5.dp, t.outline)
                .padding(Space.l)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(MapNudge.GUIDE_TITLE, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.height(3.dp))
            Text(MapNudge.GUIDE_SUBTITLE, fontSize = 10.5.sp, lineHeight = 15.sp, color = t.inkFaint)
            Spacer(Modifier.height(Space.s))

            MapNudge.GUIDE_STEPS.forEachIndexed { i, (title, body) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "${i + 1}",
                        fontSize = 10.sp,
                        color = t.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NumericFont,
                        modifier = Modifier.width(16.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
                        Text(body, fontSize = 10.sp, lineHeight = 15.sp, color = t.inkSoft)
                    }
                }
            }

            Spacer(Modifier.height(Space.s))
            // 隐私 + 数据诚实的底线，必须显示
            Text(
                MapNudge.GUIDE_FOOTNOTE,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = t.inkFaint,
            )
            Spacer(Modifier.height(Space.m))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Box(
                    Modifier.weight(1f).border(StrokeTokens.hairline, t.outline)
                        .clickable(onClick = onDismiss).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("知道了", fontSize = 12.sp, color = t.inkSoft) }
                Box(
                    Modifier.weight(1f).background(t.primary)
                        .clickable(onClick = onStart).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("开始标注", fontSize = 12.sp, color = t.paper, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier.clip(RectangleShape)
            .border(StrokeTokens.hairline, t.outline)
            .background(t.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.5.sp, color = t.inkSoft)
    }
}

/** `kind` 码 → 中文：**优先读 manifest.kindLabels**（数据驱动），未知码原样显示 */
internal fun kindLabel(kind: String, manifest: cn.dkarbon.enroute.data.map.MapManifest?): String {
    if (kind.isBlank()) return ""
    manifest?.kindLabels?.get(kind)?.let { return it }
    return kind
}

/** `#RRGGBB` → Color；解析不出用兜底色（**配色从数据读，不写死**） */
internal fun parseColor(hex: String, fallback: Color): Color {
    val s = hex.trim().removePrefix("#")
    if (s.length != 6) return fallback
    val v = s.toLongOrNull(16) ?: return fallback
    return Color((0xFF000000L or v).toInt())
}
