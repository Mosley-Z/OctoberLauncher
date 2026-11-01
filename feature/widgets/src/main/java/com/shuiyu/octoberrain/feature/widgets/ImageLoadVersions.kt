package com.shuiyu.octoberrain.feature.widgets

/** Main-thread owned. Only immutable tokens may cross into background loading work. */
internal class ImageLoadVersions {
    internal class Token internal constructor(
        internal val owner: ImageLoadVersions,
        val sourceVersion: Long,
        val lifecycleVersion: Long,
        val selectionVersion: Long,
        val dimensionsVersion: Long,
    )

    private var sourceVersion = 0L
    private var lifecycleVersion = 0L
    private var selectionVersion = 0L
    private var dimensionsVersion = 0L
    private var visible = true
    private var released = false

    fun sourceChanged() {
        sourceVersion++
    }

    fun selectionChanged() {
        selectionVersion++
    }

    fun dimensionsChanged() {
        dimensionsVersion++
    }

    fun setVisible(visible: Boolean) {
        if (released || this.visible == visible) return
        this.visible = visible
        lifecycleVersion++
    }

    fun capture(): Token = Token(this, sourceVersion, lifecycleVersion, selectionVersion, dimensionsVersion)

    fun canPublish(token: Token): Boolean =
        !released &&
            token.owner === this &&
            token.sourceVersion == sourceVersion &&
            token.lifecycleVersion == lifecycleVersion &&
            token.selectionVersion == selectionVersion &&
            token.dimensionsVersion == dimensionsVersion

    fun mayRunResources(): Boolean = visible && !released

    fun release() {
        released = true
    }
}
