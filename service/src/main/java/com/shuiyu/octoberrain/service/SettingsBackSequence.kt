package com.shuiyu.octoberrain.service

/** Ownership remains fixed for the complete physical key sequence, even if focus changes. */
internal class SettingsBackSequence {
    private var captured: Pair<Long, Int>? = null

    fun down(
        downTime: Long,
        deviceId: Int,
        repeat: Int,
        eligible: Boolean,
    ): Boolean {
        val key = downTime to deviceId
        captured?.let { return it == key }
        val capture = repeat == 0 && eligible
        if (capture) captured = key
        return capture
    }

    fun up(
        downTime: Long,
        deviceId: Int,
    ): Boolean {
        if (captured != downTime to deviceId) return false
        captured = null
        return true
    }

    fun clear() {
        captured = null
    }
}

internal fun settingsBackEligible(
    s4: Boolean,
    defaultHome: Boolean,
    session: Boolean,
    packageName: String?,
    settingsTitle: Boolean,
    settingsItems: Boolean,
): Boolean = s4 && defaultHome && session && packageName == "com.wetao.elauncher" && settingsTitle && settingsItems
