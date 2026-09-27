package cn.ianzb.hypernavbar.hook.dexkit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.base.BaseData

/**
 * 单成员 DexKit 查询回调。
 */
fun interface IDexKit {
    @Throws(ReflectiveOperationException::class)
    fun dexkit(bridge: DexKitBridge): BaseData
}
