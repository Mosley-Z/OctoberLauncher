package com.shuiyu.octoberrain.feature.files.ui

import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FileListing

/**
 * 文件条目文本格式化。
 *
 * 列表页与详情面板共用同一套大小与时间写法；大小与时间直接沿用数据层的格式化实现，
 * 保证浏览页与后续查看器显示一致。
 */
internal object FileText {
    /** 格式化字节数，负数按 0 处理，结果保留一位小数。 */
    fun size(bytes: Long): String = FileListing.formatSize(bytes)

    /** 格式化毫秒时间戳，非正数返回空串。 */
    fun time(millis: Long): String = FileListing.formatTime(millis)

    /**
     * 生成条目副标题。
     *
     * 目录只显示修改时间，文件显示大小与修改时间，两者以两个空格分隔；
     * 参数 item 为待格式化条目。
     */
    fun meta(item: FileItem): String = if (item.dir) time(item.mtime) else "${size(item.size)}  ${time(item.mtime)}"
}
