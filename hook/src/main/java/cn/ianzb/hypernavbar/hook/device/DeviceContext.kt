package cn.ianzb.hypernavbar.hook.device

import android.annotation.SuppressLint
import android.content.res.Resources
import cn.ianzb.hypernavbar.hook.device.DeviceContext.Companion.detected
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs

/**
 * 当前设备的形态上下文（进程内缓存，运行期不变）。
 *
 * 判定思路参考 HyperCeiler（AGPL-3.0，**仅思路参考，未复用其代码**）：
 * - 平板：优先 MIUI `miui.os.Build.IS_TABLET`，回退 `Resources.getSystem().configuration.smallestScreenWidthDp >= 600`；
 * - 折叠屏：`persist.sys.multi_display_type` 低 4 位（2 后屏 / 3 内折 / 4 翻盖 / 5 外折），
 *   回退旧键 `persist.sys.muiltdisplay_type`（1 后屏 / 2 内折）。
 *
 * 形态归类优先级：MIUI 平板标记 → 折叠屏 → 宽度 ≥ 600dp → 其余为手机。
 * 这样展开态折叠屏（宽度可能 ≥ 600dp）不会被误判为平板。
 */
data class DeviceContext(
    /** 归类后的形态。 */
    val type: DeviceType,
    /** 系统最小宽度（dp）。 */
    val smallestWidthDp: Int,
    /** MIUI 平板硬件标记。 */
    val isMiuiTablet: Boolean,
    /** 是否为（宽屏）平板形态。 */
    val isTablet: Boolean,
    /** 是否为折叠屏（内折 / 外折 / 翻盖）。 */
    val isFoldable: Boolean,
    val isFoldInside: Boolean,
    val isFoldOutside: Boolean,
    val isFlip: Boolean,
    val isRear: Boolean,
    /** 原始 `persist.sys.multi_display_type` 值。 */
    val multiDisplayType: Int,
    /** `ro.build.characteristics`。 */
    val characteristics: String,
    /** 是否被用户在设置中手动覆盖。 */
    val overridden: Boolean = false,
) {
    override fun toString(): String =
        "device=${type.name}${if (overridden) "(override)" else ""}(sw=${smallestWidthDp}dp, miuiTablet=$isMiuiTablet, " +
            "foldable=$isFoldable, inside=$isFoldInside, outside=$isFoldOutside, flip=$isFlip)"

    companion object {

        /** 覆盖存储键（与 App 侧配置键 / `PrefsStore` 一致）。 */
        const val KEY_DEVICE_TYPE = "device_type"

        /** 模块自动判定的设备形态（不含用户覆盖）。 */
        val detected: DeviceContext by lazy { detect() }

        /**
         * 当前生效的设备形态：在 [detected] 基础上应用用户覆盖。
         * 未覆盖或覆盖为 `auto` 时返回 [detected]。
         */
        val current: DeviceContext
            get() {
                val forced = DeviceType.fromKey(readOverrideKey())
                return if (forced == null || forced == detected.type) {
                    detected
                } else {
                    detected.copy(type = forced, overridden = true)
                }
            }

        private fun readOverrideKey(): String =
            runCatching { HookPrefs.getString(KEY_DEVICE_TYPE, DeviceType.OVERRIDE_AUTO) }
                .getOrNull()
                .orEmpty()
                .ifEmpty { DeviceType.OVERRIDE_AUTO }

        private const val KEY_MULTI_DISPLAY = "persist.sys.multi_display_type"
        private const val KEY_MULTI_DISPLAY_LEGACY = "persist.sys.muiltdisplay_type"
        private const val TABLET_MIN_WIDTH_DP = 600

        @SuppressLint("PrivateApi")
        private fun detect(): DeviceContext {
            val multi = getPropInt(KEY_MULTI_DISPLAY, 1)
            var rear = false
            var inside = false
            var outside = false
            var flip = false

            if (multi > 1) {
                when (multi and 0x0F) {
                    2 -> rear = true
                    3 -> inside = true
                    4 -> flip = true
                    5 -> outside = true
                }
            } else {
                when (getPropInt(KEY_MULTI_DISPLAY_LEGACY, 0)) {
                    1 -> rear = true
                    2 -> inside = true
                }
            }

            val foldable = inside || outside || flip
            val smallest = runCatching {
                Resources.getSystem().configuration.smallestScreenWidthDp
            }.getOrDefault(0)
            val miuiTablet = runCatching {
                Class.forName("miui.os.Build").getField("IS_TABLET").getBoolean(null)
            }.getOrDefault(false)
            val tablet = miuiTablet || smallest >= TABLET_MIN_WIDTH_DP

            val type = when {
                miuiTablet -> DeviceType.PAD
                foldable -> DeviceType.FOLD
                smallest >= TABLET_MIN_WIDTH_DP -> DeviceType.PAD
                else -> DeviceType.PHONE
            }

            return DeviceContext(
                type = type,
                smallestWidthDp = smallest,
                isMiuiTablet = miuiTablet,
                isTablet = tablet,
                isFoldable = foldable,
                isFoldInside = inside,
                isFoldOutside = outside,
                isFlip = flip,
                isRear = rear,
                multiDisplayType = multi,
                characteristics = getProp("ro.build.characteristics"),
            )
        }

        @SuppressLint("PrivateApi")
        private fun getProp(key: String): String = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java)
            method.invoke(null, key) as? String ?: ""
        }.getOrDefault("")

        private fun getPropInt(key: String, default: Int): Int =
            getProp(key).trim().toIntOrNull() ?: default
    }
}
