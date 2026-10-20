package com.shuiyu.octoberrain.feature.files.ui.viewer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文件大小单位，按 1024 进位，最后一级为 GB。 */
private val SIZE_UNITS = listOf("B", "KB", "MB", "GB")

/** 详情时间格式，与文件列表保持一致。 */
private const val TIME_PATTERN = "yyyy-MM-dd HH:mm"

/**
 * 查看器文案与格式化工具。
 *
 * 纯函数集合，不引用 Android 类型，可在本地 JVM 单元测试中直接验证。
 * 省略号截断通过调用方传入的宽度判定回调完成，位图绘制与文本测量留在界面层。
 */
object ViewerText {
    /**
     * 按可用宽度截断文本，超宽时保留末尾并追加省略号。
     *
     * 从后向前逐字缩短，直到判定可容纳或只剩一个字符；判定回调返回 false 时继续缩短。
     *
     * @param text 原始文本。
     * @param fits 宽度判定回调，参数为候选文本，返回是否可完整显示。
     * @return 可显示文本；原文可容纳时原样返回。
     */
    fun ellipsize(
        text: String,
        fits: (String) -> Boolean,
    ): String {
        var result = "…"
        if (fits(text)) {
            result = text
        } else {
            var end = text.length - 1
            while (end > 0) {
                val candidate = text.substring(0, end) + "…"
                if (fits(candidate)) {
                    result = candidate
                    break
                }
                end -= 1
            }
        }
        return result
    }

    /**
     * 把文件大小格式化为带单位的文本。
     *
     * 小于 1024 字节显示整数字节；更大时按 1024 进位并保留一位小数。
     *
     * @param bytes 字节数，负数按 0 处理。
     * @return 形如「1.5 MB」的文本。
     */
    fun formatSize(bytes: Long): String {
        var value = bytes.coerceAtLeast(0).toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < SIZE_UNITS.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        if (unit == 0) return "${value.toLong()} ${SIZE_UNITS[unit]}"
        return String.format(Locale.US, "%.1f %s", value, SIZE_UNITS[unit])
    }

    /**
     * 把时间戳格式化为分钟精度文本。
     *
     * @param millis 毫秒时间戳，非正值视为未知。
     * @return 形如「2026-10-20 00:23」的文本；未知时返回空串。
     */
    fun formatTime(millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(millis))
    }

    /**
     * 取文件名扩展名并转为大写，作为详情面板的格式行。
     *
     * @param name 文件名，可含路径分隔符。
     * @return 大写的扩展名；无扩展名时返回「未知」。
     */
    fun formatLabel(name: String): String {
        val extension = name.substringAfterLast('.', "").trim()
        return extension.uppercase(Locale.US).ifBlank { "未知" }
    }
}
