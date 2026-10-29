package com.shuiyu.octoberrain.core.common

/** Only settings opened by this launcher may participate in the S4 BACK compatibility route. */
object VendorSettingsSession {
    @Volatile var active: Boolean = false
        private set

    fun begin() {
        active = true
    }

    fun clear() {
        active = false
    }
}
