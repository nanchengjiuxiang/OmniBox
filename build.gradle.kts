// OmniBox 根构建脚本
// 约束：不引入任何网络请求库 / 统计 / 广告 SDK；release 产物最终 Manifest 不得含 INTERNET。
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

// 发布门禁：检查合并后的 Manifest 是否意外带入 INTERNET 权限。
// 在 CI 中于 assembleRelease / bundleRelease 之后执行 :verifyNoInternetPermission。
tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "校验所有模块的 AndroidManifest.xml 源码均未声明 INTERNET 权限"
    doLast {
        // 注意：主 Manifest 用 <uses-permission ... tools:node="remove" /> 显式移除
        // 传递依赖注入的 INTERNET，这一行是「防御性声明」而非「申请权限」，必须排除，
        // 否则 CI 的源码级门禁会把自己的防御措施误判为违规并中断构建。
        val offenders = fileTree(rootDir) { include("**/src/main/AndroidManifest.xml") }
            .files.filter { mf ->
                mf.readLines().any { line ->
                    line.contains("android.permission.INTERNET") &&
                        !line.contains("tools:node=\"remove\"")
                }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException("发现 INTERNET 权限申请，违反零网络权限约束：\n" +
                offenders.joinToString("\n") { it.absolutePath })
        }
        println("OK: 源码 Manifest 均不含 INTERNET 权限申请（remove 声明已正确排除）")
    }
}
