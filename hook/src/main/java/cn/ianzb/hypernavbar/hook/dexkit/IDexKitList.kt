package cn.ianzb.hypernavbar.hook.dexkit

import org.luckypray.dexkit.DexKitBridge

/**
 * 成员列表 DexKit 查询回调。
 */
fun interface IDexKitList {
    @Throws(ReflectiveOperationException::class)
    fun dexkit(bridge: DexKitBridge): List<*>
}
