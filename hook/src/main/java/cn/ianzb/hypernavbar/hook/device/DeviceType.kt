package cn.ianzb.hypernavbar.hook.device

import cn.ianzb.hypernavbar.hook.device.DeviceType.Companion.OVERRIDE_AUTO


/**
 * 设备形态：手机 / 平板 / 折叠屏。
 *
 * 可在 App 设置中通过 [OVERRIDE_AUTO] / `PHONE` / `PAD` / `FOLD` 覆盖自动判定；
 * 存储键为 [DeviceContext.KEY_DEVICE_TYPE]，由 `PrefsStore` 写入远程偏好、`HookPrefs` 读取。
 */
enum class DeviceType {
    PHONE,
    PAD,
    FOLD;

    /** 覆盖存储值（`phone` / `pad` / `fold`）。 */
    val key: String get() = name.lowercase()

    companion object {
        /** 默认：使用模块自动判定的类型。 */
        const val OVERRIDE_AUTO = "auto"

        /** 把覆盖值解析为设备类型；无法识别时返回 null（按自动处理）。 */
        fun fromKey(key: String?): DeviceType? = when (key?.trim()?.lowercase()) {
            PHONE.key -> PHONE
            PAD.key -> PAD
            FOLD.key -> FOLD
            else -> null
        }
    }
}
