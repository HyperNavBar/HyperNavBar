package cn.ianzb.hypernavbar.hook.gesture

/** Only use one input path at a time so a cancellation on the old path cannot end a MIUI drag. */
internal class GestureTouchSource {
    private var miuiReceiver: Any? = null

    val acceptLegacy: Boolean get() = miuiReceiver == null

    fun acceptMiui(receiver: Any, isDown: Boolean): Boolean {
        if (miuiReceiver === receiver) return true
        if (!isDown) return false
        miuiReceiver = receiver
        return true
    }

    fun clearMiui(receiver: Any): Boolean {
        if (miuiReceiver !== receiver) return false
        miuiReceiver = null
        return true
    }
}
