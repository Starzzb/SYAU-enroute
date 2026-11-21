package cn.dkarbon.enroute

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.Reminders
import cn.dkarbon.enroute.ui.EnRouteRoot
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.EnRouteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面。
 *
 * ## 启动路径纪律（2026-11-10 性能审查后定的，别破坏）
 *
 * `onCreate` 里**只允许三件事**：`super` → `enableEdgeToEdge` → `setContent`。
 * 通知渠道创建、调试日志、TCP 探测全部搬到**首帧之后**（[withFrameNanos] 等到第一帧绘制完）。
 * 原因：这些动作虽小，但都在首帧前的主线程上排队，加起来是中端机上百毫秒的黑屏时间。
 *
 * 实测（iQOO V2301A / Android 16，`am start -W` 冷启动 6 次取中位数）：
 *   改造前 P50 = 505ms → 改造后见 docs 记录
 */
class MainActivity : ComponentActivity() {

    private var vmRef: TimetableViewModel? = null

    /** SAF 选择器：把 JSON 内容读成文本交给 ViewModel */
    private val pickJson = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val text = runCatching {
            contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            vmRef?.importTimetableText(text)
        } else {
            vmRef?.toast("这个文件读不出来（可能不是 UTF-8 的 JSON）")
        }
    }

    /** 通知权限（Android 13+ 才需要） */
    private val askNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vmRef?.toast(if (granted) "提醒已开启" else "没给通知权限，上课提醒不会响")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 状态栏/导航栏透明 + 图标明暗跟随主题
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        setContent {
            val vm: TimetableViewModel = viewModel()
            vmRef = vm
            remember {
                vm.onPickFile = {
                    pickJson.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                vm.onRequestNotifPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                true
            }

            val ui by vm.ui.collectAsStateWithLifecycle()

            // ── 首帧之后才做的事（不挡启动）──
            LaunchedEffect(Unit) {
                withFrameNanos { }   // 等第一帧真的画出来
                // 首帧耗时（m40）：只在内存里排一条，不碰磁盘也不联网
                runCatching {
                    cn.dkarbon.enroute.data.telemetry.Telemetry.record(
                        "perf.first_frame",
                        mapOf(
                            "ms" to cn.dkarbon.enroute.data.telemetry.Telemetry.bucketMs(
                                cn.dkarbon.enroute.data.telemetry.Telemetry.msSinceStart(),
                            ),
                        ),
                    )
                }
                withContext(Dispatchers.IO) {
                    Reminders.ensureChannel(this@MainActivity)
                    DebugLog.log(
                        "BOOT",
                        "沈农在途 ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) · " +
                            "${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    )
                    DebugLog.log("BOOT", "通知权限=${Reminders.canNotify(this@MainActivity)}")
                    // 校园网探测：切到 IO + 短超时（3s），失败也不影响任何东西
                    val ok = runCatching {
                        java.net.Socket().use {
                            it.connect(
                                java.net.InetSocketAddress(
                                    cn.dkarbon.enroute.data.AcademicApi.URP_HOST, 80,
                                ),
                                3000,
                            )
                        }
                        true
                    }.getOrDefault(false)
                    DebugLog.log(
                        "NET",
                        if (ok) "URP ${cn.dkarbon.enroute.data.AcademicApi.URP_HOST}:80 可达 → 在校园网"
                        else "URP ${cn.dkarbon.enroute.data.AcademicApi.URP_HOST}:80 不可达 → 不在校园网/没连 VPN",
                    )
                }
            }

            // 一次性提示：系统 Toast
            LaunchedEffect(ui.toast) {
                ui.toast?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    vm.consumeToast()
                }
            }

            EnRouteTheme(
                themeMode = ui.themeMode,
                styleSet = ui.styleSet,
                colorSource = ui.colorSource,
                customPalette = ui.customPalette,
            ) {
                EnRouteRoot(ui = ui, vm = vm)
            }
        }
    }

    /**
     * 进后台时记一笔会话时长并顺手 flush（m40）。
     *
     * 放 onStop 而不是 onPause：切到别的 App 再回来只算一次，
     * 也不跟对话框、权限弹窗这些短暂遮挡混在一起。
     */
    override fun onStop() {
        super.onStop()
        runCatching { cn.dkarbon.enroute.data.telemetry.Telemetry.recordBackground() }
    }
}
