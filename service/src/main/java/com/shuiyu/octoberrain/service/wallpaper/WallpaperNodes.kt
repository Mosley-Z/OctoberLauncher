package com.shuiyu.octoberrain.service.wallpaper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍节点操作工具。
 *
 * 封装厂商壁纸界面所需的查找、点击与回收，供会话状态机复用。所有节点在使用后立即回收，
 * 避免长时间持有系统节点；点击失败时按节点中心坐标补一次手势点击。
 * 函数数量超出默认阈值，原因是节点查找、点击与回收三组操作共用同一批常量，拆分到多个
 * 对象会让调用方同时依赖多个工具。
 */
@Suppress("TooManyFunctions")
internal object WallpaperNodes {
    /** 读取窗口内全部文本与内容描述，受深度与数量上限约束。 */
    fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val result = ArrayList<String>()
        walk(root, 0, result)
        return result
    }

    /** 深度优先遍历并累计文本。 */
    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: MutableList<String>,
    ) {
        if (depth > TREE_DEPTH || out.size >= TEXT_LIMIT) return
        labelOf(node).takeIf { it.isNotEmpty() }?.let { out += it }
        for (index in 0 until node.childCount) {
            if (out.size >= TEXT_LIMIT) return
            val child = node.getChild(index) ?: continue
            walk(child, depth + 1, out)
            recycle(child)
        }
    }

    /** 读取节点的文本与内容描述。 */
    fun labelOf(node: AccessibilityNodeInfo): String =
        listOf(node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** 查找文本或内容描述包含目标文本的可视节点；命中的节点由调用方回收。 */
    fun findByText(
        root: AccessibilityNodeInfo,
        text: String,
    ): AccessibilityNodeInfo? {
        val byText = root.findAccessibilityNodeInfosByText(text)
        try {
            for (node in byText) {
                if (!node.isVisibleToUser) continue
                if (labelOf(node).contains(text)) return node
            }
        } finally {
            // 命中的节点交由调用方回收，其余节点在此释放
            byText.forEach { node -> if (!node.isVisibleToUser) recycle(node) }
        }
        return null
    }

    /** 按 id 后缀查找可视节点；命中的节点由调用方回收。 */
    fun findByIdSuffix(
        root: AccessibilityNodeInfo,
        suffix: String,
    ): AccessibilityNodeInfo? {
        val found = ArrayList<AccessibilityNodeInfo>(1)
        collectByIdSuffix(root, suffix, found, 1)
        return found.firstOrNull()
    }

    /** 按 id 后缀收集节点。 */
    fun collectByIdSuffix(
        root: AccessibilityNodeInfo,
        suffix: String,
        out: MutableList<AccessibilityNodeInfo>,
        limit: Int = NODE_LIMIT,
    ) {
        if (out.size >= limit || addMatch(root, suffix, out, limit)) return
        collectChildren(root, suffix, out, limit)
    }

    /** 命中后缀时加入结果，返回是否已达到数量上限。 */
    private fun addMatch(
        root: AccessibilityNodeInfo,
        suffix: String,
        out: MutableList<AccessibilityNodeInfo>,
        limit: Int,
    ): Boolean {
        if (hasIdSuffix(root, suffix)) out += root
        return out.size >= limit
    }

    /** 递归处理子节点并在未命中时回收。 */
    private fun collectChildren(
        root: AccessibilityNodeInfo,
        suffix: String,
        out: MutableList<AccessibilityNodeInfo>,
        limit: Int,
    ) {
        for (index in 0 until root.childCount) {
            if (out.size >= limit) return
            val child = root.getChild(index) ?: continue
            collectByIdSuffix(child, suffix, out, limit)
            if (!out.contains(child)) recycle(child)
        }
    }

    /** 判断节点 id 是否以指定后缀结尾。 */
    fun hasIdSuffix(
        node: AccessibilityNodeInfo,
        suffix: String,
    ): Boolean = node.viewIdResourceName?.endsWith(suffix) == true

    /** 优先按文本、其次按 id 后缀点击目标入口。 */
    fun clickByTextOrId(
        accessibility: AccessibilityService,
        root: AccessibilityNodeInfo,
        text: String,
        suffix: String,
    ): Boolean {
        val byText = clickAndRecycle(accessibility) { findByText(root, text) }
        return if (byText) true else clickAndRecycle(accessibility) { findByIdSuffix(root, suffix) }
    }

    /** 按文本查找并点击。 */
    fun clickByText(
        accessibility: AccessibilityService,
        root: AccessibilityNodeInfo,
        text: String,
    ): Boolean = clickAndRecycle(accessibility) { findByText(root, text) }

    /** 查找节点后点击并回收。 */
    private fun clickAndRecycle(
        accessibility: AccessibilityService,
        find: () -> AccessibilityNodeInfo?,
    ): Boolean {
        val node = find()
        val clicked = node != null && clickNode(accessibility, node)
        if (node != null) recycle(node)
        return clicked
    }

    /** 点击选图页图片项，优先匹配副本文件名，匹配不到时取首个图片项。 */
    fun clickPickImage(
        accessibility: AccessibilityService,
        root: AccessibilityNodeInfo,
        copyName: String,
    ): Boolean {
        val items = ArrayList<AccessibilityNodeInfo>()
        collectByIdSuffix(root, ID_IMAGE, items)
        val clicked =
            try {
                clickMatched(accessibility, items, copyName)
            } finally {
                items.forEach { recycle(it) }
            }
        // 节点不可点击时退回网格中心的坐标点击
        return clicked
    }

    /** 在图片项中优先点击与副本名称匹配的节点。 */
    private fun clickMatched(
        accessibility: AccessibilityService,
        items: List<AccessibilityNodeInfo>,
        copyName: String,
    ): Boolean {
        val stem = copyName.substringBeforeLast('.')
        val target =
            items.firstOrNull { node ->
                val label = labelOf(node)
                label.contains(stem)
            }
        return target != null && clickNode(accessibility, target)
    }

    /** 点击节点的可点击祖先，失败时按节点中心坐标点击。 */
    fun clickNode(
        accessibility: AccessibilityService,
        node: AccessibilityNodeInfo,
    ): Boolean {
        var clicked = node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var current: AccessibilityNodeInfo? = if (clicked) null else node.parent
        var depth = 0
        while (!clicked && current != null && depth < ANCESTOR_LIMIT) {
            clicked = current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val parent = if (clicked) null else current.parent
            if (parent !== current) recycle(current)
            current = parent
            depth += 1
        }
        if (current != null) recycle(current)
        return if (clicked) true else tapNodeCenter(accessibility, node)
    }

    /** 按节点中心坐标补一次点击。 */
    private fun tapNodeCenter(
        accessibility: AccessibilityService,
        node: AccessibilityNodeInfo,
    ): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val usable = !bounds.isEmpty
        if (usable) tap(accessibility, bounds.exactCenterX(), bounds.exactCenterY())
        return usable
    }

    /** 派发一次坐标点击手势。 */
    private fun tap(
        accessibility: AccessibilityService,
        x: Float,
        y: Float,
    ) {
        val path = Path().apply { moveTo(x, y) }
        val description =
            GestureDescription
                .Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_MS))
                .build()
        runCatching { accessibility.dispatchGesture(description, null, null) }
    }

    /** 释放节点，兼容旧版本。 */
    @Suppress("DEPRECATION")
    fun recycle(node: AccessibilityNodeInfo) {
        node.recycle()
    }

    /** 图片项 id 后缀。 */
    const val ID_IMAGE = "/iv_image"

    /** 网格容器 id 后缀。 */
    const val ID_GRID = "/recycler_view"

    /** 单次点击手势时长。 */
    private const val TAP_MS = 60L

    /** 向上查找可点击祖先的最大层数。 */
    private const val ANCESTOR_LIMIT = 8

    /** 文本收集的最大树深度。 */
    private const val TREE_DEPTH = 30

    /** 文本收集的最大条数。 */
    private const val TEXT_LIMIT = 400

    /** 节点收集的最大条数。 */
    private const val NODE_LIMIT = 200
}
