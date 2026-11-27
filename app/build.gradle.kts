import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ── 正式签名（release）────────────────────────────────────────────
// 密钥与密码都在仓库外的 O:\dev\keys 与 local.properties（后者已 gitignore），绝不入库。
// 没配的情况下回退 debug 签名，保证 CI / 新机器上仍然能 assembleRelease 出可装包
// （代价：那种包是可调试的，只适合内测）。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val releaseStorePath = localProps.getProperty("ENROUTE_STORE_FILE").orEmpty()
val hasReleaseKey = releaseStorePath.isNotBlank() && File(releaseStorePath).exists()

// 正式包**没有密钥就构建失败**（2026-11-27 改）。
// 以前缺密钥会静默回退 debug 签名：那种包 android:debuggable=true、能被任意重签，
// 一旦混进分发，谁都能在上面套一层。宁可构建红，也不要出一个「看着像正式版」的包。
// 内测走 assembleInternal —— 同一个 release 配置，只是签名用 debug。
val wantsRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (wantsRelease && !hasReleaseKey) {
    throw GradleException(
        "正式构建缺少签名密钥：local.properties 里的 ENROUTE_STORE_FILE 指向的文件不存在。\n" +
            "  正式包：配置 ENROUTE_STORE_FILE / ENROUTE_STORE_PASSWORD / ENROUTE_KEY_ALIAS / ENROUTE_KEY_PASSWORD\n" +
            "  内测包：改用 assembleInternal（release 配置 + debug 签名，可调试、不可分发）",
    )
}

android {
    namespace = "cn.dkarbon.enroute"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "cn.dkarbon.enroute"
        minSdk = 26
        targetSdk = 35
        versionCode = 510
        versionName = "0.12.0+m43"

        // 「关于」页要显示构建来源：commit 短哈希 + 构建日期。
        // 开源软件的 About 页惯例是把「这一版到底是哪份代码」写清楚，方便反馈问题时对照。
        // 取不到 git（比如拷了源码但没有 .git）时留空，页面会自动省略，不显示假信息。
        val gitCommit = runCatching {
            val p = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        }.getOrDefault("")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        // 地图补丁上报的共享键（票 13 阶段 B）。空 = 不带 X-Api-Key，服务端若配了键会 403。
        // ⚠️ 说明：打进包里的"密钥"必然可被提取，它不是安全边界。
        //    真正的防护在服务端（限流 400/小时 + 人工过 conflicts + 数据匿名），
        //    这里只是避免公开仓库里出现明文键、并让换键时不用改代码。
        val cmapKey = localProps.getProperty("CMAP_API_KEY").orEmpty()
        buildConfigField("String", "CMAP_API_KEY", "\"$cmapKey\"")

        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"" + SimpleDateFormat("yyyy-MM-dd").format(Date()) + "\"",
        )
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(releaseStorePath)
                storePassword = localProps.getProperty("ENROUTE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("ENROUTE_KEY_ALIAS")
                keyPassword = localProps.getProperty("ENROUTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 只认正式密钥；没配就由上面的 GradleException 拦下，这里不再兜底
            signingConfig = signingConfigs.findByName("release")
        }

        // 内测：与 release 同一套代码与混淆配置，只把签名换成 debug。
        // 独立成一个构建类型，是为了让「可调试的包」有个说得出口的名字 ——
        // 以前它顶着 release 的名字出现，分发时没人分得清。
        create("internal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // 调试页靠 BuildConfig.DEBUG 判断，release 包里自动消失
        buildConfig = true
    }
    androidResources {
        // 大纲/方案资产是**已经用 brotli 压过的** `.br`（4.6 MB → 见 docs/规划-课程大纲内嵌压缩.md）。
        // 再让 AAPT 走一次 deflate 只会白耗打包时间、且完全压不动（熵已榨干）；
        // 声明 noCompress 让它们按原样进包，运行时由 BrotliInputStream 解。
        noCompress.add("br")
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // 上课提醒（P3）要用：排课在后台也要跑
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // 桌面小组件（P4）：Glance
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 课程大纲 / 培养方案资产的 brotli 解码（纯 Java，98 KB，无 native 依赖）。
    // 选它而不是 gzip：同一批语料 brotli q11 比 gzip -9 小 43%（4.27 MB vs 7.46 MB）。
    implementation("org.brotli:dec:0.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}







