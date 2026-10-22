package com.shuiyu.octoberrain.feature.reader.source

internal data class LocalFileIdentity(
    val device: Long,
    val inode: Long,
    val size: Long,
)

internal object LocalIdentityRules {
    fun isCandidate(target: String): Boolean =
        target.startsWith('/') && !target.endsWith(" (deleted)") && '\u0000' !in target

    fun sameFile(
        opened: LocalFileIdentity,
        candidate: LocalFileIdentity,
    ): Boolean = opened == candidate && opened.inode > 0 && opened.size >= 0
}
