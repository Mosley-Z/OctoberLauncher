package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.MinuteTicker
import com.shuiyu.octoberrain.core.common.NotificationItem
import com.shuiyu.octoberrain.core.common.TimeUtils
import com.shuiyu.octoberrain.core.common.TrayController
import com.shuiyu.octoberrain.core.data.NotificationCenter
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.device.PermissionCenter

/**
 * 快捷托盘覆盖层：日期电量、WiFi、蓝牙、前光、媒体、通知、悬浮层入口与关闭把手。
 *
 * 内容整体置于可滚动容器，768×552 这类短屏下压缩行高并允许滚动；
 * 顶部下滑手势用于收起托盘，与宫格下滑打开形成连续手势。
 * 每次数据变化重读全局服务状态并重建内容，保留滚动位置。
 */
class TrayPanelView(
    context: Context,
    private val controller: TrayController,
) : FrameLayout(context) {
    private val column: LinearLayout

    private val scroll: DismissibleScrollView

    private val ticker = MinuteTicker { if (visibility == View.VISIBLE) refresh() }

    init {
        setBackgroundColor(InkColors.PAPER)
        val tokens = DesignRuntime.tokens
        column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    tokens.trayGutter,
                    tokens.statusBarHeight + tokens.gap,
                    tokens.trayGutter,
                    tokens.gapLarge,
                )
            }
        scroll = DismissibleScrollView(context, tokens.dpF(48f)) { controller.close() }
        scroll.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.stop()
        super.onDetachedFromWindow()
    }

    /** 重建全部内容，读取最新配置与系统状态。 */
    fun refresh() {
        if (!DesignRuntime.isReady()) return
        val config = runCatching { AppServices.config.state.value }.getOrNull() ?: return
        val previousScroll = scroll.scrollY
        column.removeAllViews()
        addHeader(config)
        column.addView(InkWidgets.divider(context))
        addWifiCard(config)
        addBluetoothCard(config)
        addFrontLightCard(config)
        addMediaCard(config)
        addNotificationCard(config)
        addOverlayHint(config)
        addCloseHandle()
        scroll.post { scroll.scrollTo(0, previousScroll) }
    }

    /** 顶部日期与电量行。 */
    private fun addHeader(config: LauncherConfig) {
        val tokens = DesignRuntime.tokens
        val battery = runCatching { AppServices.system.battery.value }.getOrNull()
        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.trayRow
            }
        header.addView(
            InkWidgets.textView(context, TimeUtils.formatDate(), tokens.textHeading, InkColors.INK),
        )
        header.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            },
        )
        val batteryText =
            when {
                battery == null -> ""
                battery.charging -> "${battery.percent}% 充电中"
                config.tray.showBatteryPercent -> "${battery.percent}%"
                else -> ""
            }
        header.addView(
            InkWidgets.textView(context, batteryText, tokens.textCaption, InkColors.INK_3),
        )
        column.addView(header)
    }

    /** WiFi 卡片：点击开关，长按打开系统设置；显示与否由托盘配置控制。 */
    private fun addWifiCard(config: LauncherConfig) {
        if (!config.tray.showWifi) return
        val wifi = runCatching { AppServices.system.wifi.value }.getOrNull() ?: return
        val card = sectionCard()
        card.addView(
            sectionTitle(
                InkGlyphs.Kind.WIFI,
                "WiFi",
                if (wifi.enabled) "已开启" else "已关闭",
            ),
        )
        val detail =
            when {
                wifi.connected -> "${wifi.ssid} · 信号 ${wifi.level}/3"
                wifi.enabled -> "未连接"
                else -> "点击开启"
            }
        card.addView(detailText(detail))
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            runCatching { AppServices.system.setWifi(!wifi.enabled) }
            refreshPanelContent()
            runCatching { AppServices.refresh.requestPartial() }
        }
        card.setOnLongClickListener {
            runCatching { AppServices.system.openWifiSettings() }
            true
        }
        column.addView(card)
        addSpacer(DesignRuntime.tokens.gap)
    }

    /** 蓝牙卡片：点击开关，长按打开系统设置；显示与否由托盘配置控制。 */
    private fun addBluetoothCard(config: LauncherConfig) {
        if (!config.tray.showBluetooth) return
        if (!runCatching { AppServices.system.hasBluetooth() }.getOrDefault(false)) return
        val bluetooth = runCatching { AppServices.system.bluetooth.value }.getOrNull() ?: return
        val card = sectionCard()
        card.addView(
            sectionTitle(
                InkGlyphs.Kind.BLUETOOTH,
                "蓝牙",
                if (bluetooth.enabled) "已开启" else "已关闭",
            ),
        )
        val detail =
            when {
                bluetooth.connectedName != null -> bluetooth.connectedName.orEmpty()
                bluetooth.enabled -> "未连接设备"
                else -> "点击开启"
            }
        card.addView(detailText(detail))
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            runCatching { AppServices.system.setBluetooth(!bluetooth.enabled) }
            refreshPanelContent()
            runCatching { AppServices.refresh.requestPartial() }
        }
        card.setOnLongClickListener {
            runCatching { AppServices.system.openBluetoothSettings() }
            true
        }
        column.addView(card)
        addSpacer(DesignRuntime.tokens.gap)
    }

    /** 前光卡片：冷光与暖光各十档，支持加减与档位条点击。 */
    private fun addFrontLightCard(config: LauncherConfig) {
        if (!runCatching { AppServices.system.hasFrontLight() }.getOrDefault(false)) return
        val light = runCatching { AppServices.system.frontLight.value }.getOrNull()
        val cold = light?.cold ?: config.tray.brightnessCold
        val warm = light?.warm ?: config.tray.brightnessWarm
        val card = sectionCard()
        card.addView(
            sectionTitle(InkGlyphs.Kind.SUN, "前光", "冷 $cold / 暖 $warm"),
        )
        val current = runCatching { AppServices.system.frontLight.value }.getOrNull()
        val liveCold = current?.cold ?: cold
        val liveWarm = current?.warm ?: warm
        card.addView(
            levelRow("冷光", liveCold) { value ->
                (controller as? TrayControllerImpl)?.setFrontLight(value, liveWarm)
            },
        )
        card.addView(
            levelRow("暖光", liveWarm) { value ->
                (controller as? TrayControllerImpl)?.setFrontLight(liveCold, value)
            },
        )
        column.addView(card)
        addSpacer(DesignRuntime.tokens.gap)
    }

    /** 单通道档位行：减号、档位条、加号与当前值；两行共用同一套尺寸。 */
    private fun levelRow(
        label: String,
        value: Int,
        onChange: (Int) -> Unit,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.trayRow
            }
        row.addView(
            InkWidgets.textView(context, label, tokens.textCaption, InkColors.INK_3),
            LinearLayout.LayoutParams(tokens.dp(36f), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        row.addView(hSpace(tokens.dp(4f)))
        row.addView(squareButton("−") { onChange((value - 1).coerceIn(0, 10)) })
        row.addView(
            LevelBarView(context, value) { onChange(it) },
            LinearLayout.LayoutParams(0, tokens.trayTouch, 1f),
        )
        row.addView(squareButton("+") { onChange((value + 1).coerceIn(0, 10)) })
        row.addView(hSpace(tokens.dp(4f)))
        row.addView(
            InkWidgets.textView(context, value.toString(), tokens.textCaption, InkColors.INK).apply {
                gravity = Gravity.CENTER
                minimumWidth = tokens.dp(24f)
            },
        )
        return row
    }

    /** 媒体卡片：标题、艺术家与上一首、播放暂停、下一首。 */
    private fun addMediaCard(config: LauncherConfig) {
        if (!config.tray.mediaEnabled) return
        val media = runCatching { AppServices.system.media.value }.getOrNull()
        val card = sectionCard()
        val title =
            when {
                media == null -> "无媒体播放"
                media.title.isNotEmpty() -> media.title
                media.active -> "媒体播放"
                else -> "无媒体播放"
            }
        card.addView(sectionTitle(InkGlyphs.Kind.VOLUME, title, media?.artist.orEmpty()))
        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        buttons.addView(
            miniButton("上一首") {
                runCatching { AppServices.system.mediaPrevious() }
                refreshPanelContent()
                runCatching { AppServices.refresh.requestPartial() }
            },
            LinearLayout.LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f),
        )
        buttons.addView(hSpace(DesignRuntime.tokens.gap))
        buttons.addView(
            miniButton(if (media?.playing == true) "暂停" else "播放") {
                runCatching { AppServices.system.mediaPlayPause() }
                refreshPanelContent()
                runCatching { AppServices.refresh.requestPartial() }
            },
            LinearLayout.LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f),
        )
        buttons.addView(hSpace(DesignRuntime.tokens.gap))
        buttons.addView(
            miniButton("下一首") {
                runCatching { AppServices.system.mediaNext() }
                refreshPanelContent()
                runCatching { AppServices.refresh.requestPartial() }
            },
            LinearLayout.LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f),
        )
        card.addView(buttons)
        column.addView(card)
        addSpacer(DesignRuntime.tokens.gap)
    }

    /** 通知卡片：未授权时显示授权入口，已授权时按折叠规则列出最新若干条。 */
    private fun addNotificationCard(config: LauncherConfig) {
        val feed = AppServices.notifications
        val canRead = runCatching { feed.canRead() }.getOrDefault(false)
        val visible =
            if (canRead) {
                runCatching { NotificationCenter.visibleItems(config.notifications) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        val total = visible.sumOf { it.count.coerceAtLeast(1) }
        val card = sectionCard()
        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = DesignRuntime.tokens.trayRow
            }
        header.addView(InkGlyphs.view(context, InkGlyphs.Kind.LIST, DesignRuntime.tokens.statusIcon))
        header.addView(
            InkWidgets
                .textView(
                    context,
                    "通知",
                    DesignRuntime.tokens.textHeading,
                    InkColors.INK,
                ).apply {
                    setPadding(DesignRuntime.tokens.gap, 0, 0, 0)
                },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (canRead) {
            if (total > 0) {
                header.addView(
                    InkWidgets.textView(
                        context,
                        "$total 条",
                        DesignRuntime.tokens.textCaption,
                        InkColors.INK_3,
                    ),
                )
            }
            header.addView(
                InkWidgets
                    .textView(
                        context,
                        "清空",
                        DesignRuntime.tokens.textCaption,
                        InkColors.INK_3,
                    ).apply {
                        setPadding(DesignRuntime.tokens.gap, 0, 0, 0)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            runCatching { feed.dismissAll() }
                            refreshPanelContent()
                            runCatching { AppServices.refresh.requestPartial() }
                        }
                    },
            )
        }
        card.addView(header)
        if (!canRead) {
            card.addView(
                actionText("通知读取未授权，点击开启") {
                    runCatching { feed.openAccessSettings() }
                },
            )
            column.addView(card)
            addSpacer(DesignRuntime.tokens.gap)
            return
        }
        val limit = config.tray.notificationLimit.coerceIn(1, 8)
        if (visible.isEmpty()) {
            card.addView(detailText("暂无通知"))
        } else {
            if (config.notifications.foldByApp) {
                card.addView(detailText("已按应用折叠，长按条目撤销整组"))
            }
            visible.take(limit).forEachIndexed { index, item ->
                if (index > 0) card.addView(InkWidgets.divider(context))
                card.addView(noticeRow(item, config))
            }
        }
        column.addView(card)
        addSpacer(DesignRuntime.tokens.gap)
    }

    /**
     * 单条通知行：首行为应用名与相对时间，其次为标题与正文。
     *
     * 参数 item 为可见条目，config 为当前配置；折叠条目的标题右侧显示数量，
     * 点击打开所属应用，长按或右侧叉号撤销，折叠条目按包名整组撤销。
     */
    private fun noticeRow(
        item: NotificationItem,
        config: LauncherConfig,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.trayRow
                setPadding(0, tokens.dp(4f), 0, tokens.dp(4f))
                isClickable = true
                isFocusable = true
                setOnClickListener { openApplication(item.packageName) }
                setOnLongClickListener {
                    dismissNotice(item)
                    true
                }
            }
        val texts =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
        val meta = metaLine(item, config)
        if (meta.isNotEmpty()) {
            texts.addView(
                InkWidgets.textView(context, meta, tokens.textCaption, InkColors.INK_3).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        }
        val titleRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        titleRow.addView(
            InkWidgets.textView(context, item.title, tokens.textBody, InkColors.INK).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (item.count > 1) {
            titleRow.addView(
                InkWidgets
                    .textView(
                        context,
                        "${item.count} 条",
                        tokens.textCaption,
                        InkColors.INK_2,
                    ).apply {
                        setPadding(tokens.gap, 0, 0, 0)
                    },
            )
        }
        texts.addView(titleRow)
        if (item.text.isNotEmpty()) {
            texts.addView(
                InkWidgets
                    .textView(
                        context,
                        item.text,
                        tokens.textCaption,
                        InkColors.INK_2,
                    ).apply {
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    },
            )
        }
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            InkGlyphs.view(context, InkGlyphs.Kind.CLOSE, tokens.statusIcon).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { dismissNotice(item) }
            },
            LinearLayout.LayoutParams(tokens.trayTouch, tokens.trayTouch).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )
        return row
    }

    /**
     * 构造通知首行说明文本。
     *
     * 参数 item 为通知条目，config 为当前配置；按显示配置依次拼接应用名、
     * 会话名、常驻标记与相对时间，片段之间以间隔号分隔。
     */
    private fun metaLine(
        item: NotificationItem,
        config: LauncherConfig,
    ): String {
        val parts = mutableListOf<String>()
        if (config.notifications.showAppLabel) {
            parts.add(item.appLabel.ifEmpty { item.packageName })
        }
        item.conversationTitle?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        if (item.isOngoing) parts.add("常驻")
        parts.add(TimeUtils.relative(item.postedAt))
        return parts.joinToString(" · ")
    }

    /** 撤销单条通知：折叠条目按应用整组撤销。 */
    private fun dismissNotice(item: NotificationItem) {
        runCatching {
            if (item.isGroup) {
                NotificationCenter.dismissGroup(item.packageName)
            } else {
                AppServices.notifications.dismiss(item.key)
            }
        }
        refreshPanelContent()
    }

    /** 悬浮状态栏入口：未授权时跳设置，已授权未启用时一键启用。 */
    private fun addOverlayHint(config: LauncherConfig) {
        val card = sectionCard()
        val access =
            runCatching {
                AppServices.permissions.snapshot()[PermissionCenter.KEY_ACCESSIBILITY] == true
            }.getOrDefault(false)
        when {
            !access ->
                card.addView(
                    actionText("悬浮状态栏未授权，点击开启无障碍") {
                        runCatching { AppServices.permissions.openAccessibilitySettings() }
                    },
                )

            !config.tray.overlayEnabled ->
                card.addView(
                    actionText("悬浮状态栏已授权，点击启用") {
                        runCatching {
                            AppServices.config.edit { current ->
                                current.copy(tray = current.tray.copy(overlayEnabled = true))
                            }
                        }
                    },
                )

            else -> card.addView(detailText("悬浮状态栏已启用，离开桌面后显示"))
        }
        column.addView(card)
        addSpacer(DesignRuntime.tokens.gap)
    }

    /** 底部关闭把手。 */
    private fun addCloseHandle() {
        val wrapper =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, DesignRuntime.tokens.gap, 0, 0)
            }
        wrapper.addView(
            InkWidgets.button(context, "收起托盘") {
                controller.close()
            },
        )
        column.addView(wrapper)
    }

    /** 卡片容器：白底描边、圆角与 14dp 内边距。 */
    private fun sectionCard(): LinearLayout {
        val tokens = DesignRuntime.tokens
        val pad = tokens.dp(14f)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background =
                InkWidgets.background(
                    tokens.radius,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            setPadding(pad, pad, pad, pad)
        }
    }

    /** 说明文字：使用说明档字号与三级墨色，与标题形成层级。 */
    private fun detailText(label: CharSequence): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textCaption, InkColors.INK_3)
    }

    /** 卡片首行：图标、标题与右侧状态说明。 */
    private fun sectionTitle(
        kind: InkGlyphs.Kind,
        title: CharSequence,
        value: CharSequence,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.trayRow
            }
        row.addView(InkGlyphs.view(context, kind, tokens.statusIcon))
        row.addView(
            InkWidgets.textView(context, title, tokens.textHeading, InkColors.INK).apply {
                setPadding(tokens.gap, 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (value.isNotEmpty()) {
            row.addView(InkWidgets.textView(context, value, tokens.textCaption, InkColors.INK_3))
        }
        return row
    }

    /** 可点击的文字动作行，说明文字使用三级墨色。 */
    private fun actionText(
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textCaption, InkColors.INK_3).apply {
            minimumHeight = tokens.trayRow
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 等分宽度的媒体按钮。 */
    private fun miniButton(
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textCaption, InkColors.INK).apply {
            gravity = Gravity.CENTER
            background = InkWidgets.background(tokens.radiusSmall, InkColors.PAPER, tokens.stroke, InkColors.INK)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 方形加减按钮。 */
    private fun squareButton(
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textBody, InkColors.INK).apply {
            gravity = Gravity.CENTER
            background = InkWidgets.background(tokens.radiusSmall, InkColors.PAPER, tokens.stroke, InkColors.INK)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(tokens.trayTouch, tokens.trayTouch)
        }
    }

    /** 纵向占位。 */
    private fun addSpacer(height: Int) {
        column.addView(InkWidgets.spacer(context, height))
    }

    /** 横向占位。 */
    private fun hSpace(width: Int): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }

    /** 通知控制器刷新托盘内容，并请求局部重绘。 */
    private fun refreshPanelContent() {
        (controller as? TrayControllerImpl)?.refreshPanels()
        runCatching { AppServices.refresh.requestPartial() }
    }

    /** 启动应用；无启动入口时给出提示。 */
    private fun openApplication(packageName: String) {
        val launched =
            runCatching {
                val intent =
                    context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: return@runCatching false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        if (!launched) {
            runCatching { AppServices.router.toast("无法打开该应用") }
        }
    }
}

/**
 * 可识别下滑收起的滚动容器。
 *
 * 在列表顶部继续向下滑动超过阈值时触发收起回调；普通滚动与点击不受影响。
 */
private class DismissibleScrollView(
    context: Context,
    private val thresholdPx: Float,
    private val onSwipeDown: () -> Unit,
) : ScrollView(context) {
    private var downY = 0f

    private var tracking = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                tracking = !canScrollVertically(-1)
            }

            MotionEvent.ACTION_UP -> {
                if (tracking && !canScrollVertically(-1) && event.y - downY > thresholdPx) {
                    onSwipeDown()
                }
                tracking = false
            }

            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return super.dispatchTouchEvent(event)
    }
}

/**
 * 十档档位条：按点击位置写入档位，已点亮段落填充墨色。
 *
 * 构造参数 level 为当前档位 0 到 10；onChange 回调新的档位。
 */
private class LevelBarView(
    context: Context,
    private val level: Int,
    private val onChange: (Int) -> Unit,
) : View(context) {
    private val fill = DesignRuntime.fillPaint(InkColors.INK)

    private val stroke = DesignRuntime.strokePaint(InkColors.INK)

    private val gap = DesignRuntime.tokens.dp(2f)

    private val barHeight = DesignRuntime.tokens.trayBar

    init {
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(0, DesignRuntime.tokens.trayTouch, 1f)
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val cell = width / SEGMENTS.toFloat()
                if (cell > 0f) {
                    val index = (event.x / cell).toInt().coerceIn(0, SEGMENTS - 1)
                    onChange(index + 1)
                }
            }
            true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width / SEGMENTS.toFloat()
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight
        for (index in 0 until SEGMENTS) {
            val left = index * cellWidth + gap / 2f
            val right = (index + 1) * cellWidth - gap / 2f
            val rect = RectF(left, top, right, bottom)
            if (index < level) {
                canvas.drawRect(rect, fill)
            } else {
                canvas.drawRect(rect, stroke)
            }
        }
    }

    private companion object {
        const val SEGMENTS = 10
    }
}
