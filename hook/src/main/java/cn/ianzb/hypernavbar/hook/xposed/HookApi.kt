@file:Suppress("unused")

package cn.ianzb.hypernavbar.hook.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * 传递给 hook 回调的参数包装。
 */
class HookParam(
    val executable: Any?,
    val thisObject: Any?,
    val args: List<Any?>,
) {
    var result: Any? = null
    var hasResult: Boolean = false

    fun setResultValue(value: Any?) {
        result = value
        hasResult = true
    }
}

typealias BeforeHook = (HookParam) -> Unit
typealias AfterHook = (HookParam) -> Unit
typealias ReplaceHook = (HookParam) -> Any?

/**
 * libxposed API 102 的二次封装门面。
 *
 * - 统一挂载入口（before / after / replace / intercept / 批量）
 * - 所有 [HookHandle] 自动登记到 [HookRegistry]，便于统一卸载
 */
object HookHelper {

    private lateinit var xposed: XposedInterface

    fun init(module: XposedInterface) {
        xposed = module
    }

    fun log(message: String) {
        if (::xposed.isInitialized) xposed.log(4, "HyperNavBar", message)
    }

    fun log(message: String, throwable: Throwable) {
        if (::xposed.isInitialized) xposed.log(6, "HyperNavBar", message, throwable)
    }

    // ---------------- 基础挂载 ----------------

    fun intercept(
        executable: Executable,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        mode: ExceptionMode = ExceptionMode.PROTECTIVE,
        hooker: Hooker,
    ): HookHandle {
        val handle = xposed.hook(executable)
            .setPriority(priority)
            .setExceptionMode(mode)
            .intercept(hooker)
        HookRegistry.register(handle)
        return handle
    }

    fun hookBefore(
        executable: Executable,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        callback: BeforeHook,
    ): HookHandle = intercept(executable, priority) { chain ->
        val param = HookParam(executable, chain.thisObject, chain.args)
        callback(param)
        if (param.hasResult) param.result else chain.proceed()
    }

    fun hookAfter(
        executable: Executable,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        callback: AfterHook,
    ): HookHandle = intercept(executable, priority) { chain ->
        val param = HookParam(executable, chain.thisObject, chain.args)
        param.result = chain.proceed()
        param.hasResult = true
        callback(param)
        param.result
    }

    fun hookReplace(
        executable: Executable,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        callback: ReplaceHook,
    ): HookHandle = intercept(executable, priority) { chain ->
        val param = HookParam(executable, chain.thisObject, chain.args)
        callback(param)
    }

    fun hookClassInitializer(
        clazz: Class<*>,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        callback: BeforeHook,
    ): HookHandle {
        val handle = xposed.hookClassInitializer(clazz)
            .setPriority(priority)
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val param = HookParam(clazz, null, emptyList())
                callback(param)
                chain.proceed()
            }
        HookRegistry.register(handle)
        return handle
    }

    // ---------------- 查找并挂载 ----------------

    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
        callback: BeforeHook,
    ): HookHandle = hookBefore(clazz.findMethod(methodName, parameterTypes), callback = callback)

    fun findAndHookMethodAfter(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
        callback: AfterHook,
    ): HookHandle = hookAfter(clazz.findMethod(methodName, parameterTypes), callback = callback)

    fun findAndHookMethodReplace(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
        callback: ReplaceHook,
    ): HookHandle = hookReplace(clazz.findMethod(methodName, parameterTypes), callback = callback)

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypes: Class<*>,
        callback: BeforeHook,
    ): HookHandle = findAndHookMethod(
        Reflect.findClass(className, classLoader), methodName, *parameterTypes, callback = callback
    )

    fun findAndHookConstructor(
        clazz: Class<*>,
        vararg parameterTypes: Class<*>,
        callback: BeforeHook,
    ): HookHandle = hookBefore(clazz.findConstructor(parameterTypes), callback = callback)

    fun findAndHookConstructorAfter(
        clazz: Class<*>,
        vararg parameterTypes: Class<*>,
        callback: AfterHook,
    ): HookHandle = hookAfter(clazz.findConstructor(parameterTypes), callback = callback)

    fun hookAllMethods(
        clazz: Class<*>,
        methodName: String,
        callback: BeforeHook,
    ): List<HookHandle> = clazz.declaredMethods
        .filter { it.name == methodName }
        .map { hookBefore(it, callback = callback) }

    fun hookAllConstructors(
        clazz: Class<*>,
        callback: BeforeHook,
    ): List<HookHandle> = clazz.declaredConstructors
        .map { hookBefore(it, callback = callback) }

    // ---------------- 调用原方法 ----------------

    fun invokeOriginal(method: Method, thisObject: Any?, vararg args: Any?): Any? {
        method.isAccessible = true
        return method.invoke(thisObject, *args)
    }

    fun invokeOriginal(constructor: Constructor<*>, vararg args: Any?): Any? {
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    // ---------------- 内部辅助 ----------------

    private fun Class<*>.findMethod(name: String, parameterTypes: Array<out Class<*>>): Method {
        val method = getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method
    }

    private fun Class<*>.findConstructor(parameterTypes: Array<out Class<*>>): Constructor<*> {
        val constructor = getDeclaredConstructor(*parameterTypes)
        constructor.isAccessible = true
        return constructor
    }
}

/**
 * 统一登记 HookHandle，供卸载使用。
 */
object HookRegistry {
    private val handles = java.util.concurrent.CopyOnWriteArrayList<HookHandle>()

    fun register(handle: HookHandle) {
        handles.add(handle)
    }

    fun unhookAll() {
        handles.forEach { runCatching { it.unhook() } }
        handles.clear()
    }

    fun size(): Int = handles.size
}
