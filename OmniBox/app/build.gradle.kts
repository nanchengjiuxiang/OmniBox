import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 签名信息只从本地 keystore.properties 或 CI 环境变量读取，绝不写入版本库。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    System.getenv(env) ?: keystoreProperties.getProperty(key)

android {
    namespace = "com.omnibox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omnibox"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ABI 过滤必须放在 defaultConfig 内：AGP 8.7 的 Kotlin DSL 不接受 android{} 顶层的 ndk{}
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        // 设计稿设置页的 "OmniBox v1.0.0-release" 由 BuildConfig 驱动，禁止写死在 UI 里
        buildConfigField("String", "VERSION_DISPLAY", "\"OmniBox v$versionName-release\"")
    }

    signingConfigs {
        create("release") {
            val ks = signingValue("storeFile", "OMNIBOX_KS_PATH")
            if (!ks.isNullOrBlank()) {
                storeFile = rootProject.file(ks)
                storePassword = signingValue("storePassword", "OMNIBOX_KS_PASS")
                keyAlias = signingValue("keyAlias", "OMNIBOX_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OMNIBOX_KEY_PASS")
                enableV1Signing = true   // minSdk 26 理论可关，保留兼容性更稳
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    // ABI 过滤见上方 defaultConfig；交 AAB 时由 Play 自动拆分
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    // 桌面小组件：Glance 只做本地渲染与点击，不引入任何网络栈
    implementation(libs.androidx.glance.appwidget)

    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:qrcode"))
    implementation(project(":feature:unitconverter"))
    implementation(project(":feature:randomwheel"))
    implementation(project(":feature:password"))
    implementation(project(":feature:timestamp"))
    implementation(project(":feature:texttools"))
    implementation(project(":feature:imagecompress"))
    implementation(project(":feature:exif"))
    implementation(project(":feature:formatconverter"))
    implementation(project(":feature:compass"))
    implementation(project(":feature:countdown"))
    implementation(project(":feature:decibel"))
    implementation(project(":feature:explore"))
    implementation(project(":feature:displaylab"))
    implementation(project(":feature:battery"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 网络策略：探索页需要 INTERNET 打开外链；除它之外不允许任何网络相关权限被悄悄加进来。
// 源码层只做「必须有 INTERNET」与「ACCESS_NETWORK_STATE 必须声明」的断言，
// 真正的权限清单以 aapt dump permissions 的结果为准（见 scripts/verify-release.sh）。
tasks.register("assertNetworkPolicy") {
    group = "verification"
    description = "确认源码 Manifest 按探索页需要声明了 INTERNET 与 ACCESS_NETWORK_STATE"
    doLast {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        val required = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        )
        val missing = required.filter { !manifest.contains("android:name=\"$it\"") }
        if (missing.isNotEmpty()) throw GradleException("Manifest 缺少网络权限声明：$missing")
        if (manifest.contains("tools:node=\"remove\"") && manifest.contains("permission.INTERNET")) {
            println("WARN: Manifest 中同时存在 INTERNET 声明与 remove 规则，请确认意图")
        }
        println("OK: 网络权限策略检查通过（INTERNET 仅用于探索页外链跳转与站点图标加载）")
    }
}
