package com.shuiyu.octoberrain.feature.reader.source

import java.io.InputStream
import java.io.OutputStream

/** 每次固定缓冲，不把URI或归档条目整体读入堆；超限由来源层删除本次临时文件。 */
internal fun copyBounded(
    input: InputStream,
    output: OutputStream,
    limit: Long,
): Long {
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    var count = input.read(buffer)
    while (count >= 0) {
        total += count
        if (total > limit) throw ReaderSourceException(com.shuiyu.octoberrain.core.common.ReaderFailure.LIMIT)
        output.write(buffer, 0, count)
        count = input.read(buffer)
    }
    return total
}
