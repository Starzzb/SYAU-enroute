package cn.dkarbon.enroute.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 列表滚动记忆（票 26）。
 *
 * ## 三条语义，缺一条都不对
 * 1. **同级回退要恢复**：进详情/设置再退回来，停在你刚才那一行。
 *    实现不是「记住 index」而是把 `LazyListState` 本身变成 **saveable**，再在导航层用
 *    `SaveableStateHolder.SaveableStateProvider(routeKey)` 把整棵子树的 saveable 状态存起来。
 *    只有两步都做，`if (detail != null) … else …` 这种把列表整个销毁的分支才留得住位置。
 * 2. **换筛选条件要回顶部**：筛选变了就是**新内容**，不是回退。所以筛选签名作为 [resetKey]
 *    进 `rememberSaveable` 的 inputs —— 一变就换一个全新的 `LazyListState`（index = 0）。
 *    这条必须显式传，不要「一刀切都记住」。
 * 3. **跨进程不恢复**：只走 Bundle，不写盘。杀进程重开回到顶部，这是刻意的。
 *
 * ## 为什么不用「记 index 再 scrollToItem」
 * 数据会变（刷新、已读、折叠），记下的 index 会在新列表里指到别的条目；
 * `LazyListState` 自己存的是 **key + offset**（有稳定 key 时按 key 定位），数据变动后位置仍然准。
 * 这也是「列表 item 必须给稳定 key」的另一条理由。
 */
@Composable
fun rememberListScrollState(resetKey: String = ""): LazyListState =
    rememberSaveable(resetKey, saver = LazyListState.Saver) { LazyListState() }

/**
 * 明确「不记忆」的列表（一次性页、极短列表）：等价于 [rememberLazyListState]，
 * 写出来是为了让读者知道这里**是故意的**，不是漏了。
 */
@Composable
fun rememberTransientListState(): LazyListState = rememberLazyListState()
