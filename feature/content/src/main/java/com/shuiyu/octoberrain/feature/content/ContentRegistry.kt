package com.shuiyu.octoberrain.feature.content

/**
 * 内容模块实例注册表。
 *
 * 页面在渲染时需要访问内容数据源提供的扩展能力（完整书架、KOReader 统计、
 * 文件浏览等），这些能力超出 [com.shuiyu.octoberrain.core.common.ContentSource]
 * 公共契约的范围。模块安装时把 [ContentSourceImpl] 记录在此，页面按需读取。
 */
object ContentRegistry {
    @Volatile
    private var installed: ContentSourceImpl? = null

    /** 记录模块安装的数据源实例。 */
    fun install(source: ContentSourceImpl) {
        installed = source
    }

    /** 返回已安装的数据源；宿主尚未加载内容模块时返回 null。 */
    fun source(): ContentSourceImpl? = installed
}
