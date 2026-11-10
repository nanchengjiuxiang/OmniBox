# OmniBox release 混淆规则
# 原则：只保留反射 / JNI / 代码生成所需符号，其余一律压缩混淆。

# ---- Kotlin / Coroutines ----
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# ---- Hilt / Dagger 生成类 ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltModules** { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class com.omnibox.**.Hilt_* { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---- 数据模型（Room 实体 / 注册表 / 序列化字段名）----
-keep class com.omnibox.core.model.** { *; }
-keep class com.omnibox.core.data.local.** { *; }

# ---- JNI / native（P2 引入 FFmpeg-Kit、libwebp 时生效）----
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.omnibox.**.jni.** { *; }

# ---- Glance 桌面小组件 ----
# Glance 通过 receiver 的 glanceAppWidget 属性反射创建 GlanceAppWidget 实例，
# 并依赖 AppWidgetSession / 组合运行时；这些符号混淆后会在添加小组件时崩溃。
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keepclassmembers class * {
    androidx.glance.appwidget.GlanceAppWidget getGlanceAppWidget();
}

# ---- ZXing / CameraX / ML Kit（P2 预留）----
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
-dontwarn androidx.camera.**
-dontwarn com.google.mlkit.**

# ---- org.json（系统内置，避免误移除）----
-keep class org.json.** { *; }

# ---- 发布可追溯性 ----
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
