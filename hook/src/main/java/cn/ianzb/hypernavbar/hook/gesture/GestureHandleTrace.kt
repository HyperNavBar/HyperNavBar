package cn.ianzb.hypernavbar.hook.gesture

import android.os.SystemClock
import android.util.Log

/** Opt-in diagnostics. Enabled with `adb shell setprop log.tag.HnvGestureUp DEBUG`. */
internal class GestureHandleTrace {
    private val lastSamples = HashMap<String, Long>()
    private var enabled = false

    fun start() {
        enabled = Log.isLoggable(TAG, Log.DEBUG)
        lastSamples.clear()
    }

    fun sample(stage: String, details: () -> String) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        val previous = lastSamples[stage]
        if (previous != null && now - previous < 250L) return
        lastSamples[stage] = now
        Log.d(TAG, "$stage ${details()}")
    }

    companion object {
        private const val TAG = "HnvGestureUp"
    }
}
