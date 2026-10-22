package com.shuiyu.octoberrain.feature.reader.web

import com.shuiyu.octoberrain.feature.reader.ReaderTable
import com.shuiyu.octoberrain.feature.reader.TableMerge
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 大消息仅用于固定大小的表格窗口，不能向原生侧透传整工作簿。 */
internal object TableMessages {
    fun parse(data: JsonObject): ReaderTable? =
        runCatching {
            val header = header(data)
            val cells = cells(data.getValue("cells") as JsonArray)
            require(header.row + cells.size <= header.totalRows)
            require(cells.all { header.column + it.size <= header.totalColumns })
            val merges = merges(data["merges"] as? JsonArray ?: JsonArray(emptyList()), header)
            header.copy(cells = cells, merges = merges)
        }.getOrNull()

    private fun header(data: JsonObject): ReaderTable {
        val names = data.getValue("sheets") as JsonArray
        require(names.size in 1..100)
        val sheets =
            names.map {
                val name = it.jsonPrimitive
                require(name.isString && name.content.length in 1..128)
                name.content
            }
        val sheet = data.integer("sheet")
        val row = data.integer("row")
        val column = data.integer("column")
        val rows = data.integer("totalRows")
        val columns = data.integer("totalColumns")
        require(sheet in sheets.indices && rows in 0..100000 && columns in 0..1024)
        require(row in 0 until rows.coerceAtLeast(1) && column in 0 until columns.coerceAtLeast(1))
        return ReaderTable(sheet, sheets, row, column, rows, columns, emptyList())
    }

    private fun cells(rows: JsonArray): List<List<String>> {
        require(rows.size <= 20)
        return rows.map { value ->
            val row = value as JsonArray
            require(row.size <= 10)
            row.map { cell ->
                val text = cell.jsonPrimitive
                require(text.isString && text.content.length <= 512)
                text.content
            }
        }
    }

    private fun merges(
        values: JsonArray,
        table: ReaderTable,
    ): List<TableMerge> {
        require(values.size <= 200)
        return values.map { value ->
            val data = value as JsonObject
            val merge =
                TableMerge(data.integer("row"), data.integer("column"), data.integer("rows"), data.integer("columns"))
            require(merge.row >= 0 && merge.column >= 0 && merge.rows in 1..100000 && merge.columns in 1..1024)
            require(merge.row.toLong() + merge.rows <= table.totalRows)
            require(merge.column.toLong() + merge.columns <= table.totalColumns)
            require(intersects(merge, table))
            merge
        }
    }

    private fun intersects(
        merge: TableMerge,
        table: ReaderTable,
    ): Boolean =
        merge.row < table.row + 20 &&
            merge.row.toLong() + merge.rows > table.row &&
            merge.column < table.column + 10 &&
            merge.column.toLong() + merge.columns > table.column

    private fun JsonObject.integer(key: String): Int = requireNotNull(getValue(key).jsonPrimitive.intOrNull)
}
