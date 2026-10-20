package com.shuiyu.octoberrain.feature.settings

/**
 * 美化模式与预览页之间的进程内状态传递。
 *
 * 美化模式在进入预览前把当前页的内存顺序、页号与各页条目键写入本对象；
 * 预览页读取后按该顺序绘制网格，并在用户确认后按同样的逐页规则写回配置。
 * 进程重建后本对象为空，预览页回退到配置中的桌面顺序与第 1 页。
 */
internal object BeautifyPreviewState {
    /** 当前页下标，从 0 开始。 */
    var pageIndex: Int = 0
        private set

    /** 当前页尚未写入配置的顺序，元素为 LauncherEntry.key。 */
    var workingKeys: List<String> = emptyList()
        private set

    /** 全部页的条目键，写回配置时逐页拼装使用。 */
    var pages: List<List<String>> = emptyList()
        private set

    /** 条目键到显示名称的映射，供预览页绘制标签。 */
    var labels: Map<String, String> = emptyMap()
        private set

    /** 写入次数，为 0 表示本对象尚未被美化模式填充。 */
    var version: Long = 0L
        private set

    /** 是否存在可用的美化模式状态。 */
    val isFilled: Boolean
        get() = version > 0L && pages.isNotEmpty()

    /**
     * 写入美化模式的当前状态。
     *
     * @param pageIndex 当前页下标
     * @param workingKeys 当前页的页内顺序
     * @param pages 各页条目键
     * @param labels 条目键到显示名称的映射
     */
    fun put(
        pageIndex: Int,
        workingKeys: List<String>,
        pages: List<List<String>>,
        labels: Map<String, String>,
    ) {
        this.pageIndex = pageIndex.coerceAtLeast(0)
        this.workingKeys = workingKeys.toList()
        this.pages = pages.map { it.toList() }
        this.labels = labels.toMap()
        version += 1L
    }

    /** 清除状态，例如排序已写回配置之后。 */
    fun clear() {
        pageIndex = 0
        workingKeys = emptyList()
        pages = emptyList()
        labels = emptyMap()
        version = 0L
    }
}
