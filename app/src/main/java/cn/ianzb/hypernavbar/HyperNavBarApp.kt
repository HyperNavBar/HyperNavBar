package cn.ianzb.hypernavbar

import android.app.Application
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionRegistry
import cn.ianzb.hypernavbar.prefs.PrefsStore
import cn.ianzb.hypernavbar.ui.screen.features.featureSpecs
import cn.ianzb.hypernavbar.xposed.XposedServiceManager

class HyperNavBarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PrefsStore.init(this)
        ConfigState.init(this)
        OptionRegistry.registerAll(featureSpecs())
        XposedServiceManager.init()
    }
}
