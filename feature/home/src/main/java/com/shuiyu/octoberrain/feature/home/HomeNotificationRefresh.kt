package com.shuiyu.octoberrain.feature.home

/** Main-thread notification invalidation; resume is called after the host becomes visible. */
internal class HomeNotificationRefresh {
    private var pending = false

    fun changed(
        visible: Boolean,
        refresh: () -> Unit,
    ) {
        if (visible) {
            pending = false
            refresh()
        } else {
            pending = true
        }
    }

    fun clear() {
        pending = false
    }

    fun resume(refresh: () -> Unit) {
        if (!pending) return
        pending = false
        refresh()
    }
}
