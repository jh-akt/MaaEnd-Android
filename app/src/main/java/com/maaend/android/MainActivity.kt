package com.maaend.android

import com.maaframework.android.host.MaaHostPolicies
import com.maaframework.android.ui.MaaHostActivity

class MainActivity : MaaHostActivity() {
    override val hostVersionName: String = BuildConfig.VERSION_NAME
    override val hostPreferencesName: String = "maaend_android_settings"
    override val hostPolicy = MaaHostPolicies.MaaEnd
}
