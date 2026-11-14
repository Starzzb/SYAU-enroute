package cn.dkarbon.enroute.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.launch

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
            // 命中/双击回调里读「最新」的变换与要素：不能进 pointerInput 的 key（否则每帧重建，见下）
            val transformNow = androidx.compose.runtime.rememberUpdatedState(transform)
            val featuresNow = androidx.compose.runtime.rememberUpdatedState(pkg?.features.orEmpty())
            val features = pkg?.features.orEmpty()
            // 提示条的三个数字必须从**合并后的实际数据**算（标注也算进去，票 12 §8.1）
            val mergedRows = remember(pkg, ui.mapEdits) {
                MapEditsOps.merged(pkg?.features.orEmpty(), ui.mapEdits)
            }
            val paths = remember(pkg) { features.map { it to buildPath(it) } }
            val groups = remember(manifest, pkg) { manifest?.let { MapLayers.split(it, features) } }

            // 可拖边界 = (缩放后内容 − 画布)/2 + **视野余量 35%**
            //   · 前半段旧公式 cw*(zoom-1)/2 只在「内容正好铺满画布」时才对（fit 是留边居中的）
            //   · 后半段是 pan padding：让四角地块也能被拖到视野中部（主人 2026-11-14 的建议）
            val bboxW = (manifest?.bbox?.let { it[2] - it[0] } ?: 1024.0).toFloat()
            val bboxH = (manifest?.bbox?.let { it[3] - it[1] } ?: 648.0).toFloat()
            val limitX = MapViewRules.panLimit(
                bboxW * base.scale.toFloat() * zoom, cw, MapViewRules.viewMargin(cw),
            )
            val limitY = MapViewRules.panLimit(
                bboxH * base.scale.toFloat() * zoom, ch, MapViewRules.viewMargin(ch),
            )

            /** 越界衰减（拖过头只留 35%，松手再弹回） */
            fun overdrag() {
                panX = MapViewRules.applyOverdrag(panX, limitX)
                panY = MapViewRules.applyOverdrag(panY, limitY)
            }

            /** 立刻夹回边界（双击 / 重置视图用） */
            fun clampPan() {
                panX = MapViewRules.clampToBounds(panX, limitX)
                panY = MapViewRules.clampToBounds(panY, limitY)
            }

            // 回弹**只在所有手指抬起后**发生（见下面的 awaitEachGesture）：
            // 以前用「停手 140ms 近似手势结束」，慢速拖动会被误判 → 画面被拉回（主人报的"会回弹"）。
            val limitsNow = androidx.compose.runtime.rememberUpdatedState(limitX to limitY)

            // 点击门控（票 18 复测：双击会顺带选中地块、弹出属性面板）。
            // `detectTapGestures` 与我们自管的手势流同时存在时，第一下抬手就被判成单击 ——
            // 现在改成**自己判**：抬手后先等 300ms，期间有第二下 → 双击缩放且**不选中**。
            val tapLastAt = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            val tapLastPos = remember {
                androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
            }
            val tapPending = remember {
                androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null)
            }
            val tapSlopPx = with(density) { MapViewRules.TAP_SLOP_DP.dp.toPx() }
            val doubleTapSlopPx = with(density) { MapViewRules.DOUBLE_TAP_SLOP_DP.dp.toPx() }

            // 回弹动画必须跑在普通协程里：`awaitEachGesture` 的受限挂起作用域不能直接调 animate
            val uiScope = androidx.compose.runtime.rememberCoroutineScope()

            /** 松手回弹：把越界的那点位移用 200ms 动画夹回边界（**只在所有手指抬起后调用**） */
            fun snapBack() {
                val (lx, ly) = limitsNow.value
                val tx = MapViewRules.snapBackTarget(panX, lx)
                val ty = MapViewRules.snapBackTarget(panY, ly)
                if (tx == panX && ty == panY) return
                val sx = panX
                val sy = panY
                uiScope.launch {
                    androidx.compose.animation.core.animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.tween(MapViewRules.SNAP_BACK_MS),
                    ) { v, _ ->
                        panX = sx + (tx - sx) * v
                        panY = sy + (ty - sy) * v
                    }
                }
            }

            Column(Modifier.fillMaxWidth()) {
                // clipToBounds：放大后画面会超出地图块（Canvas 默认不裁剪，会溢到图例上）
                Box(Modifier.fillMaxWidth().height(mapH).clipToBounds()) {
                    Canvas(
                        Modifier.fillMaxSize().background(t.paper)
                            // 自己接管手势：`detectTransformGestures` **没有 onEnd 回调**，
                            // 而回弹必须只在「所有手指抬起」时发生。这里照它的实现抄一份，
                            // 把结束点变成确定事件（票 18 复测问题①）。
                            .pointerInput(pkg) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var lastCentroid = androidx.compose.ui.geometry.Offset.Zero
                                    var lastDistance = 0f
                                    var started = false
                                    var moved = 0f          // 累计位移：判「这是拖动还是点击」
                                    var maxPointers = 1     // 出现过多指就不可能是点击
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.size > maxPointers) maxPointers = pressed.size
                                        if (pressed.isEmpty()) break      // ← 全部抬起 = 真正的手势结束
                                        // 质心与双指间距（只有一根手指时 distance=0 → 纯平移）
                                        var cx = 0f
                                        var cy = 0f
                                        pressed.forEach { cx += it.position.x; cy += it.position.y }
                                        val centroid = androidx.compose.ui.geometry.Offset(
                                            cx / pressed.size, cy / pressed.size,
                                        )
                                        val distance = if (pressed.size >= 2) {
                                            (pressed[1].position - pressed[0].position).getDistance()
                                        } else 0f

                                        if (!started) {
                                            lastCentroid = centroid
                                            lastDistance = distance
                                            started = true
                                        } else {
                                            val pan = centroid - lastCentroid
                                            moved += pan.getDistance()
                                            val zoomChange =
                                                if (lastDistance > 1f && distance > 1f) distance / lastDistance
                                                else 1f
                                            val oldZoom = zoom
                                            val newZoom = (oldZoom * zoomChange)
                                                .coerceIn(MapViewRules.MIN_ZOOM, MapViewRules.MAX_ZOOM)
                                            val k = newZoom / oldZoom
                                            // 以**质心**为锚点缩放：质心下的内容不许跑掉
                                            val ax = centroid.x - cw / 2f
                                            val ay = centroid.y - ch / 2f
                                            panX = MapViewRules.panAfterZoomAt(ax, panX, k) + pan.x
                                            panY = MapViewRules.panAfterZoomAt(ay, panY, k) + pan.y
                                            zoom = newZoom
                                            overdrag()
                                            lastCentroid = centroid
                                            if (distance > 1f) lastDistance = distance
                                        }
                                    }
                                    snapBack()                        // ← 只有这里会回弹

                                    // ── 点击 / 双击判定（**绝不在拖动或双击时选中**）──
                                    if (!MapViewRules.isTap(maxPointers, moved, tapSlopPx)) {
                                        // 拖动 / 缩放：保持原选中不变，什么也不做
                                        tapLastAt.longValue = 0L
                                        return@awaitEachGesture
                                    }
                                    val now = System.currentTimeMillis()
                                    val lastPos = tapLastPos.value
                                    val isDouble = MapViewRules.isDoubleTap(
                                        nowMs = now,
                                        lastTapMs = tapLastAt.longValue,
                                        dx = down.position.x - lastPos.x,
                                        dy = down.position.y - lastPos.y,
                                        slopScreen = doubleTapSlopPx,
                                    )
                                    if (isDouble) {
                                        // 第二下：取消挂起的单击、按点击点放大、**不选中**
                                        tapPending.value?.cancel()
                                        tapPending.value = null
                                        tapLastAt.longValue = 0L
                                        val oldZoom = zoom
                                        val newZoom = (oldZoom * 2f)
                                            .coerceIn(MapViewRules.MIN_ZOOM, MapViewRules.MAX_ZOOM)
                                        val k = newZoom / oldZoom
                                        panX = MapViewRules.panAfterZoomAt(
                                            down.position.x - cw / 2f, panX, k,
                                        )
                                        panY = MapViewRules.panAfterZoomAt(
                                            down.position.y - ch / 2f, panY, k,
                                        )
                                        zoom = newZoom
                                        clampPan()
                                    } else {
                                        // 第一下：记下来，300ms 内没有第二下才真的算单击
                                        tapLastAt.longValue = now
                                        tapLastPos.value = down.position
                                        tapPending.value?.cancel()
                                        tapPending.value = uiScope.launch {
                                            kotlinx.coroutines.delay(MapViewRules.DOUBLE_TAP_MS)
                                            val tf = transformNow.value
                                            val visible = featuresNow.value.filter {
                                                MapGeometry.visibleInScreen(it, tf, cw, ch)
                                            }
                                            val hit = MapHit.pickOrNearest(
                                                candidates = visible,
                                                x = tf.sourceX(down.position.x),
                                                y = tf.sourceY(down.position.y),
                                                transform = tf,
                                                maxScreenDistance = (24.dp.value * density.density).toDouble(),
                                            )
                                            vm.selectMapFeature(hit?.id.orEmpty())   // 空白 → 清空
                                        }
                                    }
                                }
                            }
                    ) {
                        // ① 底图（B 模式）：2.2×→3.0× 线性淡出，**=0 就根本不画**
                        //    以前是 `if (zoom > 2.5f) 0.35f` —— 半透明位图压在纸白上就是那层「灰色模糊遮罩」，
                        //    而且是突变台阶（票 18 问题 B 的根因）。
                        val bmp = baseBitmap
                        val bitmapAlpha =
                            if (bmp != null && !vectorOnly) MapViewRules.bitmapAlpha(zoom) else 0f
                        if (bmp != null && bitmapAlpha > 0f) {
                            val w = (manifest?.bbox?.let { it[2] - it[0] } ?: 1.0) * transform.scale
                            val h = (manifest?.bbox?.let { it[3] - it[1] } ?: 1.0) * transform.scale
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset(transform.offsetX.roundToInt(), transform.offsetY.roundToInt()),
                                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                                alpha = bitmapAlpha,
                                // Low 比 None 少锯齿、比 Medium/High 少振铃，且更快
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                            )
                        }
                        // ② 矢量层：位图淡到一半以下时**自己上色**（A 模式配色），
                        //    否则只剩 3% 填充的灰白线框
                        drawFeatures(
                            paths = paths,
                            groups = groups,
                            selectedId = ui.mapSelectedId,
                            hasBitmap = !MapViewRules.vectorTakesOver(bitmapAlpha),
                            transform = transform,
                            theme = t,
                            style = manifest?.style ?: cn.dkarbon.enroute.data.map.MapStyle(),
                            layerFill = groups?.groups?.firstOrNull()?.layer?.fill.orEmpty(),
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
    /** 逻辑层自带配色（真数据没给，靠要素自身的 color / colorGroup 兜底） */
    layerFill: String = "",
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
                // A 模式：按要素自己的颜色（真数据每条都带 #RRGGBB）；
                // 缺色时用层配色，再缺按 colorGroup 兜底（票 18 问题 B 第 2 步）
                val baseColor = parseColor(MapViewRules.vectorFillHex(f, layerFill), theme.cardAlt)
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
