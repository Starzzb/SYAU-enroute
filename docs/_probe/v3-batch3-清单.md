# v3 batch3 自查清单

> 执行：2026-11-15　构建：`build-verify.bat`（testDebugUnitTest + assembleRelease）

## 0. 构建与单测

- `BUILD SUCCESSFUL in 2m 48s` + `EXIT=0`（build-verify-log.txt 末尾两行，72 个 task：19 executed / 53 up-to-date）。
- 单测：**43 个测试类 / 561 条 / 0 失败 / 0 错误 / 0 跳过**（app/build/test-results/testDebugUnitTest 的 XML 统计）。

## 1. 资讯页：页眉 + 筛选栏同收同回（FeedScreen.kt:187–281）

- 结构：页眉（LedgerHeader）、搜索行（SearchRow）、截止/失败行、频道筛选（ChannelFilterRow）全部包在同一个 `AnimatedVisibility` 里（180ms expand/shrinkVertically），收起即整体塌缩、内容区 `weight(1f)` 补高，不留空白。
- 向上翻（位置增大）→ `headerHidden = true` 收起；向下滑（位置减小）→ `headerHidden = false` **立刻**回来。
- 到顶（`idx==0 && off==0`）→ 强制 `headerHidden = false` 展开。
- 停手 1.2s：每次位置变化都 `launch { delay(1200); headerHidden = false }` 重启计时，滚动中永远轮不到它，停滚 1.2s 后回来。
- 判定只看「当前页」滚动状态：各分类页独立 LazyColumn 经 `listStateSlot` 上报（FeedPage DisposableEffect），切页旧页 dispose 清空、新页重报；旧页 onDispose 只有槽里还是自己才清，不会抹掉新页刚报的 state。
- 初值：`lastPos = -1` 首帧不动 headerHidden，打开页不误收。

## 2. 我的页：可展开身份卡（UserIdentityCard.kt / UserScreen.kt:265–307）

- 展开内容 = 学业四方格（UserStatGrid 2×2）：加权绩点 / 已修学分 / 排名（暂缺，如实显示）/ 培养方案（格内「查看」）。
- 展开态记住：`var identityExpanded by rememberSaveable { mutableStateOf(false) }`，切到资讯再回来不丢。
- 原「学业」组已从页面删除：UserScreen 只剩教务 / 校园 / 本机三个 box；UserStatGroup（旧组外壳）已无调用点，仅作遗留 helper 保留（不编译报错、不上屏）。
- 入口无丢失：四方格四格分别挂 `onOpenScore` / `onOpenCurriculum`；旧入口（考试/成绩/查教师/课程大纲/历年课表/空闲教室/地图/自定义课程/周次校准/设置/关于/登录）均仍在；未登录/过期时整卡是登录门禁（点此登录，不展开），B3 不丢。

## 3. 课表底纹：手势驱动（本批已完成，编译通过，勿动 ui/timetable/）

- 见 `docs/_probe/课表底纹-手势驱动.md`：底纹由无条件 34s 自转改为「手指在动才转（横向切周 + 纵向滚课表合并成 backdropDrive），手停 600ms 彻底静止、协程靠 snapshotFlow 唤醒不空转」。本批不重复实现，仅引用。

## 4. 需真机确认

- 资讯：上翻时页眉+筛选栏同时收起、下滚立即回来、到顶强制展开、停手 1.2s 回来（以上为代码级核对，动画手感需真机过一遍）。
- 我的：身份卡展开/收起动画、四方格数值与暂缺显示、展开态切页保留、入口跳转。
- 课表底纹：滑动跟手度、手停即止、按钮跳周也有转换（Lead 复核）。
