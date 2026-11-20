package cn.dkarbon.enroute.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.dkarbon.enroute.data.UpdateCenter
import cn.dkarbon.enroute.data.UpdateInstall
import java.io.File

/**
 * 把「装包」这件事抽成一个回调：需要时自动去要「安装未知应用」权限，回来接着装。
 *
 * 为什么抽出来：阻塞浮层与设置页两处都要装同一个包，权限回调、pending 态、
 * ON_RESUME 兜底这三段逻辑一模一样 —— 复制两份必然走偏。
 */
@Composable
fun rememberInstallWithPermission(
    center: UpdateCenter,
    onError: (String) -> Unit = {},
): (File) -> Unit {
    val ctx = LocalContext.current
    var pendingApk by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = pendingApk ?: return@rememberLauncherForActivityResult
        pendingApk = null
        if (center.canInstallPackages()) {
            runCatching { ctx.startActivity(center.installIntent(apk)) }
                .onFailure { onError("打不开系统安装器：${it.message ?: "未知原因"}") }
        }
        // 还是没允许：什么都不说，按钮仍是「安装」，再点就再走一次
    }

    // 兜底：用户若没走「回调」那条路（手势返回、从最近任务切回、开关系统页自己 finish），
    // 回到前台时再检查一次 —— 只要权限已经给了，就立刻把安装器拉起来。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val apk = pendingApk
                if (apk != null && center.canInstallPackages()) {
                    pendingApk = null
                    runCatching { ctx.startActivity(center.installIntent(apk)) }
                        .onFailure { onError("打不开系统安装器：${it.message ?: "未知原因"}") }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return { apk: File ->
            when (UpdateInstall.step(center.canInstallPackages())) {
                UpdateInstall.Step.Installer ->
                    runCatching { ctx.startActivity(center.installIntent(apk)) }
                        .onFailure { onError("打不开系统安装器：${it.message ?: "未知原因"}") }

                UpdateInstall.Step.Settings -> {
                    pendingApk = apk
                    runCatching { permissionLauncher.launch(center.unknownSourcesIntent()) }
                        .onFailure {
                            pendingApk = null
                            onError("打不开系统设置：${it.message ?: "未知原因"}")
                        }
                }
            }
    }
}
