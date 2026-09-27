package cn.ianzb.hypernavbar.hook.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 常用反射工具，便于在 hook 代码里定位类 / 方法 / 字段。
 */
object Reflect {

    private fun defaultClassLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()

    fun findClass(name: String, classLoader: ClassLoader? = null): Class<*> =
        Class.forName(name, false, classLoader ?: defaultClassLoader())

    fun findClassIfExists(name: String, classLoader: ClassLoader? = null): Class<*>? =
        runCatching { findClass(name, classLoader) }.getOrNull()

    fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        val method = clazz.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method
    }

    fun findMethodIfExists(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        runCatching { findMethod(clazz, name, *parameterTypes) }.getOrNull()

    fun findField(clazz: Class<*>, name: String): Field {
        val field = clazz.getDeclaredField(name)
        field.isAccessible = true
        return field
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

    private fun bestMatch(
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
}
