package cn.ianzb.hypernavbar.prefs

import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.hook.rule.SystemVersion

/**
 * Hook 选项对系统版本的要求（统一走 [SystemVersion] 判定，与 Hook 侧门禁同源）。
 *
 * 要求未满足时组件一律**禁用灰显**（不隐藏），并在所属分区标题下给出说明；
 * 依赖该选项的下级组件也会一并禁用（见 `rememberDependencyEnabled`）。
 */
enum class OsRequirement {

    /** 无系统版本要求。 */
    NONE,

    /** 沉浸支持版本：HyperOS 3.3 / 4.0（「强制生效」系列等 NBI Hook）。 */
    IMMERSIVE,

    /** 仅 HyperOS 4.0（手势提示线相关 Hook）。 */
    OS_4,

    ;

    fun satisfiedBy(version: SystemVersion): Boolean = when (this) {
        NONE -> true
        IMMERSIVE -> version.immersiveSupported
        OS_4 -> version.isOs4
    }

    /** 未满足时在分区标题下展示的说明；[NONE] 无说明。 */
    val noteRes: Int
        get() = when (this) {
            NONE -> 0
            IMMERSIVE -> R.string.os_requirement_note_immersive
            OS_4 -> R.string.os_requirement_note_os4
        }
}
