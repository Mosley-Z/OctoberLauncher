package com.shuiyu.octoberrain.feature.reader

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.ReaderLocator

/** Office操作仍从阅读设置进入，坐标展示从1开始，保存与引擎使用从0开始。 */
internal class ReaderOfficeOptions(
    private val context: Context,
) {
    fun add(
        actions: MutableList<Pair<String, () -> Unit>>,
        locator: ReaderLocator?,
        session: ReaderSession?,
        reopen: (ReaderLocator?) -> Unit,
        locate: (Int, Int, Int) -> Unit,
        slide: (Int) -> Unit,
        encoding: (String) -> Unit,
    ) {
        if (locator is ReaderLocator.Sheet) {
            val names = session?.sheetNames().orEmpty()
            actions += "工作表" to {
                AlertDialog
                    .Builder(context)
                    .setTitle("工作表")
                    .setSingleChoiceItems(names.toTypedArray(), locator.sheet) { dialog, index ->
                        dialog.dismiss()
                        locate(index, 0, 0)
                    }.setNegativeButton("返回", null)
                    .show()
            }
            actions += "定位行列" to { coordinates(locator, locate) }
            if (session?.csv == true) {
                listOf("UTF-8", "GB18030", "UTF-16LE", "UTF-16BE").forEach { value ->
                    actions += "编码 $value" to { encoding(value) }
                }
            }
        }
        if (locator is ReaderLocator.Slide) {
            actions += (if (locator.outline) "原版式预览" else "文字提纲") to {
                reopen(locator.copy(outline = !locator.outline))
            }
            actions += "幻灯片目录" to {
                AlertDialog
                    .Builder(context)
                    .setTitle("幻灯片目录")
                    .setItems(session?.slideOutline().orEmpty().toTypedArray()) { _, index -> slide(index) }
                    .setNegativeButton("返回", null)
                    .show()
            }
        }
    }

    private fun coordinates(
        locator: ReaderLocator.Sheet,
        locate: (Int, Int, Int) -> Unit,
    ) {
        val row = number(locator.row + 1, "行（从1开始）")
        val column = number(locator.column + 1, "列（从1开始）")
        val fields =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(row)
                addView(column)
            }
        AlertDialog
            .Builder(context)
            .setTitle("定位行列")
            .setView(fields)
            .setNegativeButton("返回", null)
            .setPositiveButton("跳转") { _, _ ->
                val top =
                    row.text
                        .toString()
                        .toIntOrNull()
                        ?.takeIf { it > 0 }
                val left =
                    column.text
                        .toString()
                        .toIntOrNull()
                        ?.takeIf { it > 0 }
                if (top != null && left != null) locate(locator.sheet, top - 1, left - 1)
            }.show()
    }

    private fun number(
        value: Int,
        label: String,
    ): EditText =
        EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = label
            setText(value.toString())
            selectAll()
            contentDescription = label
        }
}
