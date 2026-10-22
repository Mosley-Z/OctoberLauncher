package com.shuiyu.octoberrain.feature.reader.source

import java.io.File

/** 仅逆向本应用已发布的files_share根；其他Provider仍按URI身份处理。 */
internal object OwnedDocumentPath {
    fun resolve(
        authority: String?,
        segments: List<String>,
        ownedAuthority: String,
        externalRoot: File,
    ): File? {
        if (authority != ownedAuthority || segments.firstOrNull() != "files_share") return null
        val relative = segments.drop(1)
        val valid =
            relative.isNotEmpty() &&
                relative.all {
                    it.isNotEmpty() && it !in setOf(".", "..") && it.none { char -> char in "/\\\u0000" }
                }
        return if (valid) {
            runCatching {
                val root = externalRoot.canonicalFile
                val file = File(root, relative.joinToString(File.separator)).canonicalFile
                file.takeIf { it.path.startsWith(root.path + File.separator) && it.isFile && it.canRead() }
            }.getOrNull()
        } else {
            null
        }
    }
}
