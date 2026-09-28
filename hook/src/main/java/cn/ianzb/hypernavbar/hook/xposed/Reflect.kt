package cn.ianzb.hypernavbar.hook.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * 常用反射工具，便于在 hook 代码里定位类 / 方法 / 字段。
 *
 * 所有查找结果都在进程内缓存：hot path（每帧回调 / 每次 SF 采样）反复调用时，
 * 避免重复 `getDeclaredField` / `getDeclaredMethod` 与 `declaredMethods` 数组拷贝扫描
 * （后者对大类（如 `NavigationBarImmersiveController`）是每次调用一次全量方法数组分配）。
 */
object Reflect {

    private fun defaultClassLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()

    fun findClass(name: String, classLoader: ClassLoader? = null): Class<*> =
        Class.forName(name, false, classLoader ?: defaultClassLoader())

    fun findClassIfExists(name: String, classLoader: ClassLoader? = null): Class<*>? =
        runCatching { findClass(name, classLoader) }.getOrNull()

    private val fieldCache = ConcurrentHashMap<FieldKey, Field>()
    private val methodCache = ConcurrentHashMap<MethodKey, Method>()
    private val matchCache = ConcurrentHashMap<MatchKey, Method>()

    private data class FieldKey(val clazz: Class<*>, val name: String)

    private data class MethodKey(val clazz: Class<*>, val name: String, val params: String)

    private data class MatchKey(val clazz: Class<*>, val name: String, val static: Boolean, val argTypes: String)

    fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method =
        methodCache.computeIfAbsent(MethodKey(clazz, name, paramSignature(parameterTypes))) {
            clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
        }

    fun findMethodIfExists(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        runCatching { findMethod(clazz, name, *parameterTypes) }.getOrNull()

    fun findField(clazz: Class<*>, name: String): Field =
        fieldCache.computeIfAbsent(FieldKey(clazz, name)) {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }

    fun callMethod(instance: Any, name: String, vararg args: Any?): Any? {
        val method = bestMatch(instance.javaClass, name, args, static = false)
            ?: throw NoSuchMethodException("${instance.javaClass.name}#$name")
        return method.invoke(instance, *args)
    }

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val method = bestMatch(clazz, name, args, static = true)
            ?: throw NoSuchMethodException("${clazz.name}#$name")
        return method.invoke(null, *args)
    }

    fun getObjectField(instance: Any, name: String): Any? =
        findField(instance.javaClass, name).get(instance)

    fun setObjectField(instance: Any, name: String, value: Any?) {
        findField(instance.javaClass, name).set(instance, value)
    }

    fun getStaticObjectField(clazz: Class<*>, name: String): Any? =
        findField(clazz, name).get(null)

    fun setStaticObjectField(clazz: Class<*>, name: String, value: Any?) {
        findField(clazz, name).set(null, value)
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val constructor = clazz.declaredConstructors.firstOrNull { matches(it.parameterTypes, args) }
            ?: throw NoSuchMethodException("${clazz.name}(${args.joinToString { it?.javaClass?.simpleName ?: "null" }})")
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    /** 按「类 + 方法名 + 实参类型」缓存匹配结果；未命中（方法不存在）时不缓存。 */
    private fun bestMatch(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        static: Boolean,
    ): Method? {
        val key = MatchKey(clazz, name, static, argTypesSignature(args))
        matchCache[key]?.let { return it }
        val resolved = resolveBestMatch(clazz, name, args, static) ?: return null
        matchCache[key] = resolved
        return resolved
    }

    private fun resolveBestMatch(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        static: Boolean,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filter { it.name == name && Modifier.isStatic(it.modifiers) == static }
                .firstOrNull { matches(it.parameterTypes, args) }
                ?.let { it.isAccessible = true; return it }
            current = current.superclass
        }
        return null
    }

    private fun matches(parameterTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        if (parameterTypes.size != args.size) return false
        return parameterTypes.indices.all { i ->
            val arg = args[i]
            arg == null || boxed(parameterTypes[i]).isInstance(arg)
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> type
    }

    private fun paramSignature(types: Array<out Class<*>>): String =
        if (types.isEmpty()) "" else types.joinToString(",") { it.name }

    private fun argTypesSignature(args: Array<out Any?>): String =
        if (args.isEmpty()) "" else args.joinToString(",") { it?.javaClass?.name ?: "null" }
}
