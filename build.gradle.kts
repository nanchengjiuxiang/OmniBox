// OmniBox 根构建脚本
// 约束：不引入统计 / 广告 SDK；网络权限只服务探索页外链，工具本体保持离线。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// 网络策略门禁：探索页需要 INTERNET（外链跳转 + 站点图标）与 ACCESS_NETWORK_STATE（离线提示）。
// 规则：这两项只允许在 app 模块声明；feature / core 模块出现即失败，避免某个工具私自联网。
// 在 CI 中于 assembleRelease / bundleRelease 之后执行 :verifyNetworkPolicy。
tasks.register("verifyNetworkPolicy") {
    group = "verification"
    description = "确认网络权限只在 app 模块声明，且 feature/core 模块不私自联网"
    val appManifest = layout.projectDirectory.file("app/src/main/AndroidManifest.xml").asFile
    doLast {
        val perms = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        )
        val offenders = mutableListOf<String>()
        fileTree(rootDir) {
            include("**/src/main/AndroidManifest.xml")
            exclude("**/build/**")
        }.files
            .filter { it.absolutePath != appManifest.absolutePath }
            .forEach { mf ->
                val text = mf.readText()
                perms.filter { text.contains(it) }.forEach { perm ->
                    offenders += "${mf.relativeTo(rootDir).invariantSeparatorsPath} -> $perm"
                }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException("网络权限只允许在 app 模块声明，以下模块违规：\n" +
                offenders.joinToString("\n"))
        }
        val appText = appManifest.readText()
        val missing = perms.filter { !appText.contains(it) }
        if (missing.isNotEmpty()) {
            throw GradleException("app Manifest 缺少网络权限声明：$missing")
        }
        println("OK: 网络权限策略通过（INTERNET 仅用于探索页外链与图标，工具本体离线）")
    }
}
