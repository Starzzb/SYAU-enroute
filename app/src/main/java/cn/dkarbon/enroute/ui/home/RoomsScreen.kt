package cn.dkarbon.enroute.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.UrpHomeApi.RoomOption
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 空闲教室。
 *
 * 数据结构是「校区列表 → 楼栋列表 → 某节次空闲教室」，但**查询是三支接口、两步流**：
 * 第三支（`GET /freeClassroom/today/<节次>?dayplus=`）是**会话粘滞**的，只答服务器会话里
 * 上一次 `POST /freeClassroom/today`（`position=<校区号>_<楼号>&xqm=<校区名>`）选中的那栋楼。
 * 只发 GET 永远拿到空的 —— 这正是票 02 修掉的那个 bug。实现在
 * `UrpHomeApi.fetchFreeRooms`，对接口的实测证据见 `docs/URP-学生端接口清单.md` §3。
 *
 * 这里不做任何缓存：**空教室是时效性数据**，缓存一次就可能在告诉用户"这间空着"而其实刚有人进去。
 *
 * 交互只保留三级：校区 → 楼 → 节次（+ 今天/明天/后天）。再多一级就会点不完。
 *
 * 第四级是**点教室**（票 03）：弹一层浮层看这间教室的档案。
 * ⚠️ 「这间教室被哪些课占了」在 URP 里是另一条尚未探明的路由，浮层里明写「暂缺」，
 * 不摆空课表 —— 详见 `docs/URP-学生端接口清单.md` §6.1。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomsScreen(ui: AppState, vm: TimetableViewModel) {
    // 每次进页面都刷一次明细（空教室是实时数据）；校区/教学楼列表由 VM 按每日档缓存判定，不重复打
    LaunchedEffect(Unit) { vm.refreshRooms() }

    // 浮层叠在列表之上（与考试页同一套：Box + AcademicSheet），不切页面 ——
    // 看完这间能立刻回列表点下一间。**不要**包进 Dialog：AcademicSheet 内部用 BackHandler，
    // Dialog 是另一个 composition，拿不到 OnBackPressedDispatcherOwner，运行时会抛。
    Box(Modifier.fillMaxSize()) {
        RoomsBody(ui = ui, vm = vm)
        if (ui.roomDetailRoom.isNotBlank()) {
            RoomDetailSheet(ui = ui, vm = vm)
        }
    }
}

/** 空闲教室的本体：校区 → 楼 → 节次 → 教室列表（浮层在 [RoomsScreen] 里叠在它上面） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomsBody(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏：说明 + 刷新 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (ui.roomsMsg.isNotBlank()) ui.roomsMsg else "空闲教室",
                fontSize = 11.sp,
                color = if (ui.roomsLoading) t.inkFaint else t.inkSoft,
                fontFamily = NumericFont,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.refreshRooms() }, enabled = !ui.roomsLoading) {
                Text(if (ui.roomsLoading) "查询中" else "刷新", fontSize = 12.sp)
            }
        }

        // 校区/教学楼列表的获取日期（明细是实时数据不标；列表走每日缓存，得标出它是几时的）
        val listsStamp = remember(ui.roomsListsFetchedAt) {
            cn.dkarbon.enroute.data.CachePolicy.fetchStamp(System.currentTimeMillis(), ui.roomsListsFetchedAt)
        }
        if (listsStamp.isNotBlank()) {
            Text(
                listsStamp,
                fontSize = 11.5.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(horizontal = Space.l, vertical = 2.dp),
            )
        }

        // ── 校区 ──
        if (ui.campuses.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch).padding(bottom = Space.xs),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(ui.campuses, key = { it.number }) { c ->
                    PickChip(
                        label = c.name,
                        selected = c.number == ui.roomsCampusNo,
                        onClick = { vm.selectRoomsCampus(c.number) },
                    )
                }
            }
        }

        // ── 教学楼 ──
        if (ui.buildings.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(ui.buildings.filter { it.number != "n" }, key = { it.number }) { b ->
                    PickChip(
                        label = b.name,
                        selected = b.number == ui.roomsBuildingNo,
                        onClick = { vm.selectRoomsBuilding(b.number) },
                        strong = true,
                    )
                }
            }
        }

        // ── 日期 + 节次 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("今天" to 0, "明天" to 1, "后天" to 2).forEach { (label, d) ->
                PickChip(
                    label = label,
                    selected = ui.roomsDayPlus == d,
                    onClick = { vm.selectRoomsDay(d) },
                    small = true,
                    modifier = Modifier.padding(end = Space.xs),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "第 ${ui.roomsSession} 节",
                fontSize = 11.sp,
                color = t.inkSoft,
                fontFamily = NumericFont,
            )
        }

        LazyRow(
            Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
            contentPadding = PaddingValues(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items((1..12).toList(), key = { it }) { n ->
                PickChip(
                    label = "$n",
                    selected = ui.roomsSession == n,
                    onClick = { vm.selectRoomsSession(n) },
                    small = true,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule).padding(top = Space.s))

        // ── 结果 ──
        if (ui.roomsLoading && ui.freeRooms.buildings.isEmpty()) {
            SkeletonRooms()
        } else if (ui.freeRooms.buildings.isEmpty()) {
            val buildingName = ui.buildings.firstOrNull { it.number == ui.roomsBuildingNo }?.name.orEmpty()
            Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 三分法（票 02）：真没空教室 / 会话失效或不在校园网 / 选楼没生效。
                    // 以前这里写死「这一节没有空闲教室」，接口明明只发 GET、从来没选过楼，
                    // 于是不管怎么选都是这句 —— 那是故障，不是空教室。
                    Text(
                        HomeModel.freeRoomsEmptyHint(ui.freeRooms.issue, buildingName),
                        fontSize = 12.5.sp,
                        color = t.ink,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        if (ui.roomsMsg.isNotBlank()) ui.roomsMsg else "只在校内网可用 · 数据来自教务系统教室查询",
                        fontSize = 10.5.sp,
                        color = t.inkFaint,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l, end = Space.l, top = Space.s, bottom = Space.xxl,
                ),
            ) {
                ui.freeRooms.buildings.forEach { b ->
                    HomeModel.roomGroups(b).forEach { (floor, rooms) ->
                        item(key = "${b.name}|$floor") {
                            RoomFloorBlock(
                                title = "${b.name} · $floor",
                                count = rooms.size,
                                rooms = rooms,
                                onRoomClick = { vm.openRoomDetail(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一间教室的详情浮层。
 *
 * 这里必须守住「数据诚实」：`classroomCurriculum/search` 只给**教室档案**，
 * 「这间教室今天被哪些课占了」是另一条还没探明的路由 —— 所以占用那一段明确写「暂缺」，
 * 绝不渲染一张空课表让人以为「没人用」。
 */
@Composable
private fun RoomDetailSheet(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    val roomNo = ui.roomDetailRoom
    // 空闲教室列表里那一行（座位/楼层来自票 02 的接口，比档案接口更贴身）
    val building = ui.freeRooms.buildings.firstOrNull { b -> b.rooms.any { it.room == roomNo } }
    val room = building?.rooms?.firstOrNull { it.room == roomNo }
    val buildingName = building?.name
        ?: ui.buildings.firstOrNull { it.number == ui.roomsBuildingNo }?.name.orEmpty()
    val info = ui.roomDetailInfo

    cn.dkarbon.enroute.ui.academic.AcademicSheet(
        t = t,
        title = "教室 $roomNo",
        subtitle = HomeModel.roomDetailSubtitle(buildingName, room?.seats ?: 0, room?.floor.orEmpty()),
        onDismiss = { vm.closeRoomDetail() },
    ) {
        cn.dkarbon.enroute.ui.academic.SheetField(t, "校区", info?.campusName ?: ui.freeRooms.campusName)
        cn.dkarbon.enroute.ui.academic.SheetField(t, "教学楼", buildingName)
        cn.dkarbon.enroute.ui.academic.SheetField(t, "教室号", info?.roomNo ?: roomNo, mono = true, emphasize = true)
        cn.dkarbon.enroute.ui.academic.SheetField(t, "教室名", info?.roomName.orEmpty())
        cn.dkarbon.enroute.ui.academic.SheetField(
            t, "座位", HomeModel.seatsLabel(room ?: RoomOption()),
            mono = true,
        )
        cn.dkarbon.enroute.ui.academic.SheetField(t, "楼层", room?.floor.orEmpty(), mono = true)
        cn.dkarbon.enroute.ui.academic.SheetField(t, "教室类型", info?.type.orEmpty())
        cn.dkarbon.enroute.ui.academic.SheetField(t, "所属院系", info?.dept.orEmpty())
        cn.dkarbon.enroute.ui.academic.SheetField(t, "学期", HomeModel.termLabel(info?.term.orEmpty()))

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        Spacer(Modifier.height(10.dp))

        // 2026-11-28：教室课表接上了（classroomCurriculum/searchCurriculum/callback），
        // 原来写死的「占用暂缺」撤掉 —— 有排课就把这段课列出来，真没排课才说话。
        val slots = ui.roomSchedule.slots
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("这间教室的课表", fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (slots.isNotEmpty()) {
                Text("${slots.size} 段", fontSize = 10.sp, color = t.inkFaint, fontFamily = NumericFont)
            }
        }
        Spacer(Modifier.height(4.dp))
        when {
            ui.roomScheduleLoading -> Text(
                "正在查这间教室的排课…",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkSoft,
            )
            slots.isNotEmpty() -> Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                slots.forEach { slot -> SlotRow(slot) }
            }
            ui.roomScheduleMsg.isNotBlank() -> Text(
                ui.roomScheduleMsg,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkSoft,
            )
            else -> Text(
                "这段时间这间教室没有排课",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkFaint,
            )
        }

        val msg = when {
            ui.roomDetailLoading -> "正在查这间教室的档案…"
            else -> ui.roomDetailMsg
        }
        if (msg.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
            Spacer(Modifier.height(8.dp))
            Text(msg, fontSize = 11.sp, lineHeight = 17.sp, color = t.inkFaint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomFloorBlock(
    title: String,
    count: Int,
    rooms: List<RoomOption>,
    onRoomClick: (RoomOption) -> Unit,
) {
    val t = InkTokens.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 11.sp, color = t.inkSoft, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("$count 间", fontSize = 10.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
        Spacer(Modifier.height(Space.xs))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            rooms.forEach { r ->
                RoomCell(r, onClick = { onRoomClick(r) })
            }
        }
    }
}

/** 一间教室：房号大、座位数小。可点 —— 点开看这间教室的档案（票 03） */
@Composable
private fun RoomCell(r: RoomOption, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .clickable(onClickLabel = "查看教室 ${r.room}", onClick = onClick)
            .padding(horizontal = Space.s, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.room, fontSize = 12.5.sp, color = t.ink, fontWeight = FontWeight.Medium, fontFamily = NumericFont)
        val seats = HomeModel.seatsLabel(r)
        if (seats.isNotBlank()) {
            Spacer(Modifier.width(Space.xs))
            Text(seats, fontSize = 9.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
    }
}

/** 加载骨架：静态方块，不做 shimmer */
@Composable
private fun SkeletonRooms() {
    val t = InkTokens.current
    Column(Modifier.fillMaxSize().padding(Space.l)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 2 == 0) 0.45f else 0.32f)
                    .height(10.dp)
                    .background(t.cardAlt),
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                repeat(4) {
                    Box(Modifier.width(54.dp).height(26.dp).background(t.cardAlt))
                }
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}

/**
 * 选择胶囊。`strong=true` 用于教学楼那排（字号大一点、选中用主色描边），
 * 其余是次级筛选。
 */
@Composable
fun PickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    strong: Boolean = false,
    small: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    Box(
        modifier
            .heightIn(min = if (small) 32.dp else 36.dp)
            .clip(RectangleShape)
            .background(if (selected) t.cardAlt else t.card)
            .border(if (selected) 1.5.dp else Stroke.hairline, if (selected) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) Space.s else Space.m, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (small) 11.sp else if (strong) 12.5.sp else 12.sp,
            color = if (selected) t.ink else t.inkSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (small) NumericFont else null,
        )
    }
}
