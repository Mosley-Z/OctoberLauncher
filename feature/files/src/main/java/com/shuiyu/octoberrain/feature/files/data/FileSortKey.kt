package com.shuiyu.octoberrain.feature.files.data

/**
 * 条目排序依据。
 *
 * 排序只作用于内存中的条目列表，不改变文件系统；目录优先与升降序由偏好决定。
 * 名称与类型按忽略大小写比较，两种依据相等时统一回退到名称比较，保证顺序稳定。
 *
 * 参数 label 为界面上展示的中文名称。
 */
enum class FileSortKey(
    val label: String,
) {
    /** 按名称。 */
    NAME("名称"),

    /** 按修改时间。 */
    TIME("时间"),

    /** 按字节数。 */
    SIZE("大小"),

    /** 按扩展名。 */
    TYPE("类型"),

    ;

    /**
     * 生成比较器。
     *
     * 参数 ascending 表示是否升序；dirFirst 表示目录是否始终排在文件之前。
     */
    fun comparator(
        ascending: Boolean,
        dirFirst: Boolean,
    ): Comparator<FileItem> =
        Comparator { left, right ->
            if (dirFirst && left.dir != right.dir) {
                return@Comparator if (left.dir) -1 else 1
            }
            var result =
                when (this) {
                    NAME -> left.name.compareTo(right.name, ignoreCase = true)
                    TIME -> left.mtime.compareTo(right.mtime)
                    SIZE -> left.size.compareTo(right.size)
                    TYPE -> left.extension().compareTo(right.extension(), ignoreCase = true)
                }
            if (result == 0) {
                result = left.name.compareTo(right.name, ignoreCase = true)
            }
            if (ascending) result else -result
        }

    companion object {
        /** 按持久化的序号还原排序依据，越界时回退为名称。 */
        fun fromOrdinal(value: Int): FileSortKey = entries.getOrElse(value) { NAME }
    }
}
