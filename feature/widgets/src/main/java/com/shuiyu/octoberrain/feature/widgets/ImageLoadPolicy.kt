package com.shuiyu.octoberrain.feature.widgets

import android.os.FileObserver

internal object ImageLoadPolicy {
    const val EVENT_MASK =
        FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

    fun isMutation(event: Int): Boolean = event and EVENT_MASK != 0

    fun sameContent(
        previous: Long,
        current: Long,
    ): Boolean = previous == current

    fun isHiding(
        previous: Boolean,
        current: Boolean,
    ): Boolean = previous && !current
}
