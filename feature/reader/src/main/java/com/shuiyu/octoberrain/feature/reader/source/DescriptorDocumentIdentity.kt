package com.shuiyu.octoberrain.feature.reader.source

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.File

/** 仅凭已打开的真实文件描述符证明同源；不根据正文、文件名或Provider声明推断。 */
internal object DescriptorDocumentIdentity {
    fun resolve(descriptor: ParcelFileDescriptor): File? =
        runCatching {
            val opened = Os.fstat(descriptor.fileDescriptor)
            if (!OsConstants.S_ISREG(opened.st_mode)) return@runCatching null
            val target = Os.readlink("/proc/self/fd/${descriptor.fd}")
            if (!LocalIdentityRules.isCandidate(target)) return@runCatching null
            val file = File(target).canonicalFile
            if (!file.isFile || !file.canRead()) return@runCatching null
            val candidate = Os.stat(file.path)
            file.takeIf {
                OsConstants.S_ISREG(candidate.st_mode) &&
                    LocalIdentityRules.sameFile(opened.identity(), candidate.identity())
            }
        }.getOrNull()

    private fun StructStat.identity() = LocalFileIdentity(st_dev, st_ino, st_size)
}
