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

android {
    namespace = "cn.dkarbon.enroute"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "cn.dkarbon.enroute"
        minSdk = 26
        targetSdk = 35
        versionCode = 220
        versionName = "0.4.0+m21"

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
            // 有正式密钥就用它；没有就回退 debug（那种包可调试，仅内测）
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("⚠️ 未配置 ENROUTE_STORE_FILE，release 将回退 debug 签名（不建议分发）")
                signingConfigs.getByName("debug")
            }
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
    implementation("androidx.compose.material:material-icons-extended")
    // 桌面小组件（P4）：Glance
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
