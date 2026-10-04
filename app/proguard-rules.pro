# libxposed 模块入口：由框架按 META-INF/xposed/java_init.list 中的类名反射实例化。
-keep class cn.ianzb.hypernavbar.hook.xposed.XposedEntry { *; }

# hook 侧代码整体保留（不改名/不优化），避免 R8 破坏与框架的交互。
-keep class cn.ianzb.hypernavbar.hook.** { *; }

# libxposed API 未打包进 APK（由框架提供），其 -libraryjars 由 build.gradle.kts 动态生成。
# 显式保留基类与回调实现，避免被 R8 误删（Hooker/XposedModule 的覆写）。
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
-keep class io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.**
