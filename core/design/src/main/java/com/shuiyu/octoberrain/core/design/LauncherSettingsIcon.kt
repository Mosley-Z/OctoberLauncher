package com.shuiyu.octoberrain.core.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.ImageView

/** 十雨设置入口与页头共用主图标及右上角齿轮，避免两处图形不一致。 */
object LauncherSettingsIcon {
    fun bitmap(
        context: Context,
        size: Int,
    ): Bitmap {
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
        val badge = size * 0.42f
        val x = size - badge / 2f
        val y = badge / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, badge / 2f, paint)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, size * 0.035f)
        InkGlyphs.draw(canvas, InkGlyphs.Kind.GEAR, x, y, badge * 0.8f, paint)
        return result
    }

    fun view(
        context: Context,
        size: Int,
    ): ImageView = ImageView(context).apply { setImageBitmap(bitmap(context, size)) }
}
