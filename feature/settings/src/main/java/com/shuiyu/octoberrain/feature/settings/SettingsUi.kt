package com.shuiyu.octoberrain.feature.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置页公共构件与操作封装。
 *
 * 页面代码通过 [section] 建立带描边的分组容器，再向容器追加信息行、
 * 选择行、开关行与按钮；所有尺寸取自 DesignRuntime.tokens。
 * 配置修改统一经过 [edit]，保证写入 ConfigStore 并持久化。
 */
object SettingsUi {
    /** 读取当前配置。 */
    fun settings(): LauncherConfig = AppServices.config.state.value

    /** 修改配置，写入失败或结果相同时由读取方忽略。 */
    fun edit(transform: (LauncherConfig) -> LauncherConfig) {
        AppServices.config.edit(transform)
    }

    /** 请求局部刷新。 */
    fun refresh() {
        AppServices.refresh.requestPartial()
    }

    /** 请求整屏刷新。 */
    fun fullRefresh() {
        AppServices.refresh.requestFullRefresh()
    }

    /**
     * 应用外观配置：先重建设计令牌，再请求整屏刷新。
     *
     * 参数 appearance 为新的外观配置，调用方负责先写入 ConfigStore。
     */
    fun applyAppearance(appearance: AppearanceConfig) {
        DesignRuntime.update(appearance)
        AppServices.refresh.requestFullRefresh()
    }

    /** 本次构建待统计的页面与行数。 */
    private var countedPage: InkPage? = null
    private var countedRows: Int = 0

    /**
     * 开始统计某页面的行数。
     *
     * 参数 page 为需要统计的页面，后续只有该页面内的行会被计数。
     */
    fun beginRowCount(page: InkPage) {
        countedPage = page
        countedRows = 0
    }

    /** 读取上次统计到的行数。 */
    fun rowCount(): Int = countedRows

    /**
     * 统计一行，仅对开始统计的页面生效。
     *
     * 参数 page 为行所属页面。
     */
    internal fun countRow(page: InkPage) {
        if (page === countedPage) countedRows += 1
    }

    /** 读取主题显示名。 */
    fun themeLabel(id: String): String {
        val themes = AppServices.appearance.themes()
        return themes.firstOrNull { it.id == id }?.label ?: id
    }

    /** 读取图标包显示名。 */
    fun iconPackLabel(id: String): String {
        val packs = AppServices.appearance.iconPacks()
        return packs.firstOrNull { it.id == id }?.label ?: id
    }

    /** 读取应用显示名，目录中不存在时回退包名。 */
    fun appLabel(packageName: String): String =
        AppServices.catalog.apps.value
            .firstOrNull { it.packageName == packageName }
            ?.label
            ?: packageName

    /** 把比例格式化为百分比文本。 */
    fun percent(value: Float): String = "${Math.round(value * 100f)}%"

    /** 显示轻提示。 */
    fun toast(
        context: Context,
        message: CharSequence,
    ) {
        InkDialogs.toast(context, message)
    }

    /**
     * 建立页面分组：标题、可选说明与带描边的行容器。
     *
     * 参数 page 为承载分组的页面；title 为分组标题；
     * subtitle 为分组说明，传入 null 时不显示。
     */
    fun section(
        page: InkPage,
        title: CharSequence,
        subtitle: CharSequence? = null,
    ): SettingsSection {
        val context = page.context
        val tokens = DesignRuntime.tokens
        if (page.content.childCount > 0) {
            page.content.addView(InkWidgets.spacer(context, tokens.gapLarge))
        }
        page.content.addView(InkWidgets.heading(context, title))
        if (subtitle != null) {
            page.content.addView(
                InkWidgets.caption(context, subtitle).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
            )
        }
        val box =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = tokens.gap }
            }
        page.content.addView(box)
        return SettingsSection(page, box)
    }

    /** 在页面底部追加说明文字。 */
    fun note(
        page: InkPage,
        text: CharSequence,
    ) {
        val tokens = DesignRuntime.tokens
        page.content.addView(
            InkWidgets.caption(page.context, text).apply {
                setPadding(0, tokens.gapLarge, 0, 0)
            },
        )
    }

    /**
     * 以全屏对话框显示一个二级页面，用于不属于 Destination 的设置子页。
     *
     * 参数 context 为宿主上下文；title 为页面标题；build 负责填充页面内容。
     * 返回创建的对话框，调用方可在需要时主动关闭。
     */
    fun overlayPage(
        context: Context,
        title: CharSequence,
        build: (InkPage) -> Unit,
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page = InkPage(context, title, showBack = true) { dialog.dismiss() }
        build(page)
        dialog.setContentView(page)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
        dialog.show()
        return dialog
    }

    /**
     * 选择既有文件。
     *
     * 参数 mimeTypes 传给系统文件选择器；extensions 用于模块内扫描回退；
     * onPicked 在确认后回调目标 Uri。宿主未装配选择器或选择器抛出异常时
     * 回退到扫描列表，扫描结果以 Uri.fromFile 包装。
     */
    fun pickFile(
        context: Context,
        title: CharSequence,
        mimeTypes: Array<String>,
        extensions: Set<String>,
        forImport: Boolean = false,
        onPicked: (Uri) -> Unit,
    ) {
        if (AppServices.hasPicker()) {
            val opened =
                runCatching {
                    val callback: (Uri?) -> Unit = { uri -> if (uri != null) onPicked(uri) }
                    if (forImport) {
                        AppServices.picker.pickForImport(
                            mimeTypes,
                            callback,
                        )
                    } else {
                        AppServices.picker.pick(mimeTypes, callback)
                    }
                }.getOrDefault(false)
            if (opened) return
        }
        fallbackPick(context, title, extensions, onPicked)
    }

    /**
     * 选择保存位置并回调目标 Uri。
     *
     * 宿主未装配选择器时回退到应用私有导出目录，返回该目录下文件的 Uri。
     */
    fun saveFile(
        context: Context,
        fileName: String,
        mimeType: String,
        onPicked: (Uri) -> Unit,
    ) {
        if (AppServices.hasPicker()) {
            val opened =
                runCatching {
                    AppServices.picker.save(fileName, mimeType) { uri ->
                        if (uri != null) onPicked(uri)
                    }
                }.getOrDefault(false)
            if (opened) return
        }
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        onPicked(Uri.fromFile(File(dir, fileName)))
    }

    /** 模块内扫描回退：列出候选文件供选择，结果包装为 file Uri。 */
    private fun fallbackPick(
        context: Context,
        title: CharSequence,
        extensions: Set<String>,
        onPicked: (Uri) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val files = withContext(Dispatchers.IO) { scanFiles(context, extensions) }
            if (files.isEmpty()) {
                showEmptyHint(context, extensions)
                return@launch
            }
            InkDialogs.choose(context, title, files.map { fileLabel(context, it) }) { index ->
                onPicked(Uri.fromFile(files[index]))
            }
        }
    }

    /** 扫描候选文件，限制目录深度与结果数量，避免长目录阻塞界面。 */
    private fun scanFiles(
        context: Context,
        extensions: Set<String>,
    ): List<File> {
        val result = ArrayList<File>()
        for (root in scanRoots(context)) {
            if (result.size >= MAX_CANDIDATES) break
            if (!root.isDirectory) continue
            walk(root, extensions, result, 0)
        }
        return result.sortedByDescending { it.lastModified() }.take(MAX_CANDIDATES)
    }

    /** 列出文件扫描根目录，仅包含用户可见位置。 */
    private fun scanRoots(context: Context): List<File> {
        val public = Environment.getExternalStorageDirectory()
        val roots = ArrayList<File>()
        roots.add(File(public, "Pictures"))
        roots.add(File(public, "Download"))
        roots.add(File(public, "DCIM"))
        roots.add(File(public, "Documents"))
        roots.add(File(public, "Books"))
        roots.add(File(public, "Fonts"))
        return roots
    }

    /** 递归收集指定扩展名的文件。 */
    private fun walk(
        dir: File,
        extensions: Set<String>,
        out: MutableList<File>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_CANDIDATES) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (out.size >= MAX_CANDIDATES) return
            if (child.isDirectory) {
                walk(child, extensions, out, depth + 1)
            } else if (extensions.any { child.name.lowercase().endsWith(".$it") }) {
                out.add(child)
            }
        }
    }

    /** 生成文件候选的显示文本：相对路径与文件大小。 */
    private fun fileLabel(
        context: Context,
        file: File,
    ): String {
        val base = Environment.getExternalStorageDirectory().absolutePath
        val relative = file.absolutePath.removePrefix(base).removePrefix("/")
        val name = if (relative.isBlank()) file.name else relative
        val size = Formatter.formatFileSize(context, file.length())
        return "$name · $size"
    }

    /** 没有可用文件时给出路径说明与权限入口。 */
    private fun showEmptyHint(
        context: Context,
        extensions: Set<String>,
    ) {
        val formats = extensions.joinToString("、") { it.uppercase() }
        InkDialogs.confirm(
            context,
            "未找到可用文件",
            "在 Pictures、Download、DCIM、Documents、Books、Fonts 与应用私有目录中没有找到 $formats 文件。可以把文件放到这些目录，或先授予存储访问权限。",
            confirmLabel = "存储设置",
            onConfirm = { runCatching { AppServices.permissions.openStorageSettings() } },
        )
    }

    /** 单个候选文件上限，避免一次列出过多条目。 */
    private const val MAX_CANDIDATES = 120

    /** 目录递归深度上限。 */
    private const val MAX_DEPTH = 3
}

/**
 * 设置分组容器：向页面中的描边盒子追加行控件。
 *
 * 行与行之间自动插入分隔线；分组内可插入小标题改变视觉节奏。
 */
class SettingsSection internal constructor(
    private val page: InkPage,
    private val box: LinearLayout,
) {
    private val context: Context
        get() = page.context

    private var dividerPending = false

    /** 追加自定义视图。 */
    fun content(view: View): SettingsSection {
        add(view)
        return this
    }

    /** 插入分组内小标题。 */
    fun subheading(text: CharSequence): SettingsSection {
        val tokens = DesignRuntime.tokens
        if (box.childCount > 0 && dividerPending) box.addView(InkWidgets.divider(context))
        box.addView(
            InkWidgets.caption(context, text).apply {
                setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap / 2)
            },
        )
        dividerPending = false
        return this
    }

    /** 只读信息行：左侧标题，右侧值。 */
    fun info(
        label: CharSequence,
        value: CharSequence,
    ): SettingsRow {
        val row = rowContainer(label, null)
        val valueView = valueText(value)
        row.addView(valueView)
        add(row)
        return SettingsRow(row, valueView)
    }

    /** 可点击的跳转行：右侧可带当前值摘要。 */
    fun action(
        label: CharSequence,
        value: CharSequence? = null,
        hint: CharSequence? = null,
        onClick: () -> Unit,
    ): SettingsRow {
        val row = rowContainer(label, hint)
        val valueView = value?.let { valueText(it) }
        if (valueView != null) row.addView(valueView)
        row.addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON))
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { onClick() }
        add(row)
        return SettingsRow(row, valueView)
    }

    /** 开关行：右侧显示开或关，并在开启时显示勾选图标。 */
    fun toggle(
        label: CharSequence,
        get: () -> Boolean,
        hint: CharSequence? = null,
        set: (Boolean) -> Unit,
    ): SettingsRow {
        val row = rowContainer(label, hint)
        val checked = get()
        val valueView = valueText(if (checked) "开" else "关")
        val check = InkGlyphs.view(context, InkGlyphs.Kind.CHECK)
        check.alpha = if (checked) 1f else 0f
        row.addView(valueView)
        row.addView(check)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener {
            val next = !get()
            set(next)
            valueView.text = if (next) "开" else "关"
            check.alpha = if (next) 1f else 0f
            AppServices.refresh.requestPartial()
        }
        add(row)
        return SettingsRow(row, valueView)
    }

    /** 选项行：点击弹出候选列表，选择后更新摘要并回调。 */
    fun choose(
        label: CharSequence,
        options: List<CharSequence>,
        selected: () -> Int,
        hint: CharSequence? = null,
        onPick: (Int) -> Unit,
    ): SettingsRow {
        val row = rowContainer(label, hint)
        val valueView = valueText(options.getOrElse(selected()) { "" })
        row.addView(valueView)
        row.addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON))
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener {
            InkDialogs.choose(context, label, options) { index ->
                onPick(index)
                valueView.text = options.getOrElse(selected()) { "" }
            }
        }
        add(row)
        return SettingsRow(row, valueView)
    }

    /** 分段滑条行：拖动或点击分段后，在抬手时提交所选值。 */
    fun slider(
        label: CharSequence,
        values: List<Float>,
        initial: Float,
        format: (Float) -> String,
        hint: CharSequence? = null,
        onCommit: (Float) -> Unit,
    ) {
        val tokens = DesignRuntime.tokens
        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap)
            }
        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        header.addView(labelColumn(label, hint))
        val valueView = valueText(format(initial))
        header.addView(valueView)
        container.addView(header)
        container.addView(
            StepSliderView(context, values, initial) { value ->
                valueView.text = format(value)
                onCommit(value)
            },
        )
        add(container)
    }

    /**
     * 步进行：左侧标题，右侧依次为「−」「当前值」「+」。
     *
     * 参数 values 为候选值的显示文本，selected 返回当前档位序号，
     * onChange 在档位变化后回调；行高沿用列表行高，说明文本最多一行。
     */
    fun stepper(
        label: CharSequence,
        values: List<CharSequence>,
        selected: () -> Int,
        hint: CharSequence? = null,
        normalize: (Int) -> Int = { it },
        onChange: (Int) -> Unit,
    ): SettingsRow {
        val tokens = DesignRuntime.tokens
        val row = rowContainer(label, hint)
        var current = selected().coerceIn(0, (values.size - 1).coerceAtLeast(0))

        val valueView =
            valueText(values.getOrElse(current) { "" }).apply {
                gravity = Gravity.CENTER
                minWidth = tokens.dp(48f)
                setPadding(0, 0, 0, 0)
            }

        /** 按方向移动一档；到达边界时保持不动。 */
        fun step(delta: Int) {
            if (values.isEmpty()) return
            val next = normalize((current + delta).coerceIn(0, values.size - 1)).coerceIn(0, values.size - 1)
            if (next == current) return
            current = next
            valueView.text = values[next]
            onChange(next)
            AppServices.refresh.requestPartial()
        }

        row.addView(stepButton("−") { step(-1) })
        row.addView(valueView)
        row.addView(stepButton("+") { step(1) })
        add(row)
        return SettingsRow(row, valueView)
    }

    /** 首页/其他页各一行列×行；fit返回当前视口中实际可用值。 */
    fun gridSize(
        label: String,
        initial: Pair<Int, Int>,
        fit: (Int, Int) -> Pair<Int, Int>,
        onChange: (Int, Int) -> Unit,
    ) {
        val row = rowContainer(label, null)
        var current = initial
        val values = listOf(0) + (1..12).toList()

        fun valueTextFor(value: Int): String = if (value == 0) "自" else value.toString()
        val columnView = valueText(valueTextFor(current.first))
        val rowView = valueText(valueTextFor(current.second))
        listOf(columnView, rowView).forEach {
            it.minWidth = DesignRuntime.tokens.dp(22f)
            it.gravity = Gravity.CENTER
            it.setPadding(0, 0, 0, 0)
        }
        val controls = mutableListOf<Pair<TextView, () -> Pair<Int, Int>>>()

        fun candidate(
            axis: Int,
            delta: Int,
        ): Pair<Int, Int> {
            val value = if (axis == 0) current.first else current.second
            val index = values.indexOf(value).coerceAtLeast(0)
            val next = values[(index + delta).coerceIn(0, values.lastIndex)]
            return if (axis == 0) fit(next, current.second) else fit(current.first, next)
        }

        fun update() {
            columnView.text = valueTextFor(current.first)
            rowView.text = valueTextFor(current.second)
            controls.forEach { (button, target) ->
                button.isEnabled = target() != current
                button.alpha = if (button.isEnabled) 1f else 0.45f
            }
        }

        fun button(
            axis: Int,
            delta: Int,
        ): TextView {
            val target = { candidate(axis, delta) }
            val view =
                stepButton(if (delta < 0) "−" else "+") {
                    val next = target()
                    if (next != current) {
                        current = next
                        onChange(next.first, next.second)
                        update()
                        AppServices.refresh.requestPartial()
                    }
                }
            view.contentDescription = label + listOf("列", "行")[axis] + mapOf(-1 to "减少", 1 to "增加").getValue(delta)
            controls += view to target
            return view
        }
        row.addView(button(0, -1))
        row.addView(columnView)
        row.addView(button(0, 1))
        row.addView(valueText("×"))
        row.addView(button(1, -1))
        row.addView(rowView)
        row.addView(button(1, 1))
        add(row)
        update()
    }

    /** 步进按钮：白底描边方块，用于加减档位。 */
    private fun stepButton(
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textBody, InkColors.INK).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(tokens.dp(32f), maxOf(tokens.dp(32f), tokens.labelHeight))
            background =
                InkWidgets.background(
                    tokens.radiusSmall,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 描边按钮行。 */
    fun button(
        label: CharSequence,
        filled: Boolean = false,
        onClick: () -> Unit,
    ) {
        val tokens = DesignRuntime.tokens
        val button = InkWidgets.button(context, label, filled) { onClick() }
        (button.layoutParams as? LinearLayout.LayoutParams)?.apply {
            leftMargin = tokens.gap
            rightMargin = tokens.gap
            topMargin = tokens.gap
            bottomMargin = tokens.gap
        }
        add(button)
    }

    /** 追加一行或一个控件，必要时先插入分隔线。 */
    private fun add(view: View) {
        SettingsUi.countRow(page)
        if (box.childCount > 0 && dividerPending) box.addView(InkWidgets.divider(context))
        box.addView(view)
        dividerPending = true
    }

    /** 建立基础行容器：左标题列加右侧附加控件。 */
    private fun rowContainer(
        label: CharSequence,
        hint: CharSequence?,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = tokens.listRowHeight
            setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
            addView(labelColumn(label, hint))
        }
    }

    /** 左侧标题列：标题与可选说明纵向排列。 */
    private fun labelColumn(
        label: CharSequence,
        hint: CharSequence?,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            addView(InkWidgets.textView(context, label, tokens.textBody, InkColors.INK))
            if (hint != null) {
                addView(
                    InkWidgets
                        .textView(context, hint, tokens.textCaption, InkColors.INK_2)
                        .apply { setPadding(0, tokens.gap / 2, 0, 0) },
                )
            }
        }
    }

    /** 右侧摘要文本。 */
    private fun valueText(value: CharSequence): TextView =
        InkWidgets
            .textView(
                context,
                value,
                DesignRuntime.tokens.textCaption,
                InkColors.INK_3,
            ).apply {
                setPadding(0, 0, DesignRuntime.tokens.gap / 2, 0)
            }
}

/** 设置行句柄：保留值文本的引用，便于交互后就地更新摘要。 */
class SettingsRow internal constructor(
    val view: LinearLayout,
    private val valueView: TextView? = null,
) {
    /** 更新右侧摘要文本。 */
    fun setValue(text: CharSequence) {
        valueView?.text = text
    }
}

/**
 * 分段滑条：把固定档位等宽排列，点击或拖动选择档位，抬手时提交。
 *
 * 参数 values 为档位取值，initial 为初始值，onCommit 在抬手且档位变化后回调。
 */
private class StepSliderView(
    context: Context,
    private val values: List<Float>,
    initial: Float,
    private val onCommit: (Float) -> Unit,
) : View(context) {
    private val tokens = DesignRuntime.tokens
    private val fillPaint: Paint = DesignRuntime.fillPaint(InkColors.INK)
    private val strokePaint: Paint = DesignRuntime.strokePaint(InkColors.INK)

    private var index: Int =
        values
            .indexOfFirst { Math.abs(it - initial) < 0.001f }
            .coerceAtLeast(0)

    private var committed: Int = index

    init {
        layoutParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                tokens.trayTouch * 2,
            )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val barHeight = tokens.trayBar.toFloat()
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight
        val segment = width.toFloat() / values.size
        for (i in values.indices) {
            val left = i * segment
            val right = left + segment - tokens.stroke
            fillPaint.color = if (i <= index) InkColors.INK else InkColors.PAPER
            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (values.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val next =
                    ((event.x / width) * values.size)
                        .toInt()
                        .coerceIn(0, values.size - 1)
                if (next != index) {
                    index = next
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (index != committed) {
                    committed = index
                    onCommit(values[index])
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                index = committed
                invalidate()
                return true
            }
        }
        return true
    }
}
