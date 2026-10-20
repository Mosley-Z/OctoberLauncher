package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.design.InkColors

/**
 * 占位组件：绘制虚线框与提示，点击打开组件库。
 *
 * 用于用户选择占位项或组件数据源尚未就绪时的桌面展示。
 */
class PlaceholderWidget(
    context: Context,
) : InkWidgetView(context) {
    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val area = RectF(padX, pad, width - padX, height - pad)
        if (area.width() <= 0f || area.height() <= 0f) return

        InkDraw.dashedRect(canvas, area, tokens.radius.toFloat(), strokePaint(InkColors.INK_3))

        val heading = textPaint(tokens.textHeading, InkColors.INK)
        val caption = textPaint(tokens.textCaption, InkColors.INK_2)
        val block = InkDraw.lineHeight(heading) + pad * 0.4f + InkDraw.lineHeight(caption)
        var y = area.centerY() - block / 2f
        InkDraw.textCenter(canvas, "占位组件", area.centerX(), y, heading)
        y += InkDraw.lineHeight(heading) + pad * 0.4f
        InkDraw.textCenter(canvas, "轻点打开组件库", area.centerX(), y, caption)
    }

    override fun onClick(
        x: Float,
        y: Float,
    ): Boolean {
        navigateTo(Destination.WidgetPicker)
        return true
    }
}
