package com.shuiyu.octoberrain.core.design

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.FontInfo
import com.shuiyu.octoberrain.core.common.FontProvider
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.device.DeviceProfile

/**
 * 设计运行时：集中持有尺寸令牌、字体与画笔工厂。
 *
 * Application 启动时调用 install；配置变化（字号、字体、钟面字体）后调用 update。
 * 界面层通过 DesignRuntime.tokens 与 DesignRuntime.typeface 获取绘制参数。
 */
object DesignRuntime {
    @Volatile
    lateinit var tokens: InkTokens
        private set

    @Volatile
    lateinit var appearance: AppearanceConfig
        private set

    @Volatile
    private var fontProvider: FontProvider? = null

    @Volatile
    private var labelTypefaceCache: Typeface? = null

    @Volatile
    private var defaultTypeface: Typeface = Typeface.DEFAULT

    /** 安装运行时，需在第一次创建界面前调用。 */
    fun install(
        profile: DeviceProfile,
        appearance: AppearanceConfig,
        fonts: FontProvider?,
    ) {
        this.appearance = appearance
        this.fontProvider = fonts
        tokens = InkTokens(profile, appearance.textScale.coerceIn(0.85f, 1.6f))
        labelTypefaceCache = fonts?.labelTypeface()
        defaultTypeface = labelTypefaceCache ?: Typeface.DEFAULT
        applyThemeTokens(appearance)
    }

    /** 配置变化后重算令牌与字体；调用方负责重建界面。 */
    fun update(appearance: AppearanceConfig) {
        if (!this::tokens.isInitialized) return
        this.appearance = appearance
        tokens = InkTokens(tokens.device, appearance.textScale.coerceIn(0.85f, 1.6f))
        labelTypefaceCache = fontProvider?.labelTypeface()
        defaultTypeface = labelTypefaceCache ?: Typeface.DEFAULT
        applyThemeTokens(appearance)
    }

    /**
     * 应用外观配置中的主题配色。
     *
     * 参数 appearance 为外观配置：`themeTokensJson` 非空时按自定义令牌写入 [InkColors]，
     * 令牌解析失败时记录日志并回退默认预设；令牌为空时按 `themeId` 取内置预设，
     * 未知标识同样回退默认预设，保证界面始终有一组完整配色。
     */
    private fun applyThemeTokens(appearance: AppearanceConfig) {
        val raw = appearance.themeTokensJson
        if (raw.isBlank()) {
            InkColors.apply(ThemePalette.forId(appearance.themeId))
            return
        }
        val tokens = ThemeTokensCodec.decode(raw)
        if (tokens != null) {
            InkColors.apply(tokens.palette)
        } else {
            InkLog.w("设计", "主题令牌解析失败，回退默认配色")
            InkColors.reset()
        }
    }

    /** 是否已初始化。 */
    fun isReady(): Boolean = this::tokens.isInitialized

    /** 供外部替换字体提供者，用于字体导入后立即生效。 */
    fun attachFonts(fonts: FontProvider?) {
        fontProvider = fonts
        labelTypefaceCache = fonts?.labelTypeface()
        defaultTypeface = labelTypefaceCache ?: Typeface.DEFAULT
    }

    /** 应用名称与正文使用的字体。 */
    fun typeface(): Typeface = defaultTypeface

    /** 指定字体标识的 Typeface，未知标识返回默认字体。 */
    fun typeface(id: String): Typeface {
        fontProvider?.let { provider ->
            if (id == "default") return provider.labelTypeface() ?: Typeface.DEFAULT
            if (id.startsWith("file:")) return provider.clockTypeface(id)
        }
        return when (id) {
            "serif" -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            "mono" -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "bold" -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            "cursive" -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            else -> defaultTypeface
        }
    }

    /** 钟面可用字体标识与标题，供设置页列出。 */
    fun clockFaces(): List<FontInfo> = FontInfo.BUILTIN

    /**
     * 创建文本画笔。
     *
     * 参数 size 为字号像素；color 为颜色；typeface 为字体，默认使用当前应用字体；
     * outline 为 true 时返回描边画笔（白色、线宽取字号的一小部分）。
     */
    fun textPaint(
        size: Float,
        color: Int = InkColors.INK,
        typeface: Typeface = defaultTypeface,
        outline: Boolean = false,
    ): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.isAntiAlias = true
        paint.typeface = typeface
        paint.textSize = size
        paint.color = color
        if (outline) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1.5f, size * 0.09f)
            paint.strokeJoin = Paint.Join.ROUND
        } else {
            paint.style = Paint.Style.FILL
        }
        return paint
    }

    /** 白色文字描边画笔，用于黑底反白文字。 */
    fun outlinePaint(size: Float): Paint = textPaint(size, Color.WHITE, outline = true)

    /** 实心画笔。 */
    fun fillPaint(color: Int = InkColors.INK): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

    /** 描边画笔。 */
    fun strokePaint(
        color: Int = InkColors.INK,
        width: Int = tokens.stroke,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width.toFloat()
        }
}
