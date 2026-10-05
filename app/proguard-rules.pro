# libxposed 模块入口：由框架按 META-INF/xposed/java_init.list 中的类名反射实例化。
-keep class cn.ianzb.hypernavbar.hook.xposed.XposedEntry { *; }

# hook 侧代码整体保留（不改名/不优化），避免 R8 破坏与框架的交互。
# 注意：HookHelper 的 Hooker 实现（ChainHooker）必须在此范围内，其重写的 intercept 不能被 R8 处理，
# 否则会出现 AbstractMethodError 导致 hook 全部失效（这也是不能用 Kotlin SAM Lambda 的原因）。
-keep class cn.ianzb.hypernavbar.hook.** { *; }

# compileOnly 的 libxposed API 对 R8 不可见，按名称显式保留其基类与回调实现。
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
-dontwarn io.github.libxposed.**
