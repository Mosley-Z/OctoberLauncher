package com.shuiyu.octoberrain.feature.content.data

import java.io.File

/** Library compatibility entry; shared bounded extraction lives in core:render. */
object EpubCover {
    fun extract(
        epub: File,
        targetDirectory: File,
        stem: String,
    ): File? =
        com.shuiyu.octoberrain.core.render.EpubCover
            .extract(epub, targetDirectory, stem)
}
