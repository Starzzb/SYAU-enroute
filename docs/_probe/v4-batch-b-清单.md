# v4 batch-b 收尾清单

> 执行：2026-11-16　构建：`build-verify.bat`（testDebugUnitTest + assembleRelease）

## 0. 构建与单测

- `BUILD SUCCESSFUL in 2m 18s` + `EXIT=0`（build-verify-log.txt 末尾两行，72 个 task：8 executed / 64 up-to-date）。
- 单测：**44 个测试类 / 569 条 / 0 失败 / 0 错误 / 0 跳过**（app/build/test-results/testDebugUnitTest 的 XML 统计）。

## 1. 上屏说明性文字扫描（要求为空）

- `docs/_probe/scan-hint-copy.ps1`（剥离注释 / contentDescription / 排除 FeedSummary.kt）→ **SCAN_RESULT=EMPTY**。

## 2. 地图自查

- **发光**：`CampusMapScreen.kt:1176–1210` 选中要素三层外发光（2dp 实描边 + 4dp α0.35 + 7dp α0.15，主色），宽度除以 scale，屏幕上恒定 dp 不随缩放变粗。
- **底部面板**：`CampusMapScreen.kt:789–808, 886–893` `PropertyPanel` 贴屏幕下沿（`BottomCenter` + `fillMaxHeight(0.4f)`），顶部 12dp 圆角 + 3dp 抓手，`imePadding()`。
- **控件 2.5 秒淡出不占位**：`CampusMapScreen.kt:131–137, 318–322` 末次手势后 `CONTROLS_IDLE_MS=2500` 置 `controlsVisible=false`；`FloatingMapControls`（:1331–1367）以 `AnimatedVisibility` 淡入淡出（150ms），`align(BottomStart/BottomEnd)` 浮在地图 Box 内、不占 Column 版面。
- **左右滑动同步高亮**：`CampusMapScreen.kt:872–884, 907` 头部 `HorizontalPager`，`settledPage` → `onSelect(feature.id)` 反向选中；地图点选 → `animateScrollToPage` 翻页。高亮由同一份 `selectedId` 驱动（:880 注释）。

## 3. 共建自查

- **一次同意后直接上传**：`MapUploader.uploadFromEdits`（:98–114）只查 `isEnabled` 后 `queueFromEdits` + `upload(force=true)`，无二次确认；开关开启弹一次 `MapContributeConsentDialog`（`ContributeSection.kt:99–108`、`CampusMapScreen.kt:672–684`）。
- **sid 加密**：`MapContribute.kt:33–42, 202–216` sid 只取内存态学籍、绝不落盘/不进日志，有值才发 `"sid"` 键；服务端只落 `sid_enc`（AES-256-GCM）+ `ch`（HMAC），明文不落盘。
- **设置开关常驻**：`ContributeSection.kt:81–109` 开关 + 状态行常驻；默认关（`MapContributeStore`），关掉即刻不再发（`MapUploader.kt:59–60`）。
- **按需拉取入口**：`CampusMapScreen.kt:177–184` 进地图只读本地缓存（`datasetClient.current()`），手动「同步」（:727–748）；设置页另有「拉取最新标注」（`ContributeSection.kt:116–147`），均为显式触发。
- **未开启时的提示**：`ContributeSection.kt:90`「未开启 · 导出标注只存本地，不上传」；`CampusMapScreen.kt:695`「已导出到本地 · 未开启共建，不会上传」。

## 4. 需真机确认

- 地图：选中发光在缩放下的恒定观感、底部面板与软键盘共存、控件 2.5s 淡出后版面不跳、头部左右滑动与地图高亮双向同步。
- 共建：开关开启弹一次同意（如实列学号）→ 之后「导出标注」直接上传不再问；未开启时导出只存本地提示；「同步 / 拉取最新标注」按需拉取；关闭开关后不再有任何上传。
- sid 路径：真机已登录态上传后，确认本机不落盘（`filesDir` 无 sid 明文），服务端仅存 `sid_enc`。
