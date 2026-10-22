package com.shuiyu.octoberrain.feature.reader.office.sheets

import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** Container and cell budgets run before SheetJS can allocate a workbook. No XML entities or macros execute. */
internal object WorkbookBudget {
    fun check(file: File) {
        require(file.length() > 0)
        sheetBudget(file.length() <= SheetLimits.COMPRESSED)
        val signature = ByteArray(8)
        file.inputStream().use { it.read(signature) }
        if (signature.take(2) == listOf(0x50.toByte(), 0x4b.toByte())) {
            checkZip(file)
        } else {
            BiffBudget.check(file.readBytes())
        }
    }

    private fun checkZip(file: File) {
        ZipArchive(file).use { zip ->
            sheetBudget(zip.items.all { it.size in 0..SheetLimits.EXPANDED })
            sheetBudget(zip.items.sumOf { it.size } <= SheetLimits.EXPANDED)
            val ods = zip.items.any { it.path == "content.xml" }
            require(ods || zip.items.any { it.path == "xl/workbook.xml" }) { "Unsupported workbook" }
            // Binary worksheets need their own record budget; do not feed XLSB to the XML preflight.
            require(zip.items.none { it.path.endsWith(".bin") && it.path.startsWith("xl/worksheets/") })
            val budget = SheetXmlBudget(ods)
            checkEntries(zip, budget)
        }
    }

    private fun checkEntries(
        zip: ZipArchive,
        budget: SheetXmlBudget,
    ) {
        zip.items.filter { !it.directory }.forEach { item ->
            zip.open(item.path, SheetLimits.EXPANDED).use { stream ->
                verifyStream(stream, item.path, budget)
            }
        }
    }

    private fun verifyStream(
        stream: InputStream,
        path: String,
        budget: SheetXmlBudget,
    ) {
        val xml = path == "content.xml" || path.startsWith("xl/") && path.endsWith(".xml")
        if (xml) {
            xmlFactory().newSAXParser().parse(SheetXmlInput(stream), budget)
        } else {
            val buffer = ByteArray(8192)
            var count = stream.read(buffer)
            while (count >= 0) count = stream.read(buffer)
        }
    }

    private fun xmlFactory(): SAXParserFactory =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
        }

    fun checkXml(
        xml: String,
        ods: Boolean = false,
    ) {
        val input = SheetXmlInput(ByteArrayInputStream(xml.toByteArray()))
        xmlFactory().newSAXParser().parse(input, SheetXmlBudget(ods))
    }
}

private class SheetXmlBudget(
    private val ods: Boolean,
) : DefaultHandler() {
    private var cells = 0L
    private var nodes = 0
    private var sheets = 0
    private var row = 0L
    private var repeatRows = 1L
    private var column = 0L

    override fun resolveEntity(
        publicId: String?,
        systemId: String?,
    ): InputSource = throw SAXException("External XML entities are unsupported")

    override fun startElement(
        uri: String,
        local: String,
        name: String,
        attributes: Attributes,
    ) {
        nodes++
        sheetBudget(nodes <= MAX_NODES)
        if (ods) odsElement(local, attributes) else xlsxElement(local, attributes)
        sheetBudget(cells <= SheetLimits.CELLS)
    }

    private fun xlsxElement(
        local: String,
        attributes: Attributes,
    ) {
        when (local) {
            "sheet" -> countSheet()
            "dimension", "mergeCell" -> checkAddress(attributes.getValue("ref")?.substringAfter(':') ?: "A1")
            "row" -> sheetBudget((attributes.getValue("r")?.toLongOrNull() ?: 1) <= SheetLimits.ROWS)
            "c" -> {
                cells++
                checkAddress(attributes.getValue("r") ?: "A1")
            }
        }
    }

    private fun odsElement(
        local: String,
        attributes: Attributes,
    ) {
        when (local) {
            "table" -> {
                countSheet()
                row = 0
            }
            "table-row" -> {
                repeatRows = repeat(attributes, "number-rows-repeated")
                row += repeatRows
                column = 0
                sheetBudget(row <= SheetLimits.ROWS)
            }
            "table-cell", "covered-table-cell" -> {
                val columns = repeat(attributes, "number-columns-repeated")
                column += columns
                sheetBudget(column <= SheetLimits.COLUMNS)
                cells += columns * repeatRows
            }
        }
    }

    private fun repeat(
        attributes: Attributes,
        local: String,
    ): Long {
        val index = (0 until attributes.length).firstOrNull { attributes.getLocalName(it) == local }
        return if (index ==
            null
        ) {
            1
        } else {
            attributes.getValue(index).toLong().also { sheetBudget(it in 1..SheetLimits.ROWS) }
        }
    }

    private fun countSheet() {
        sheets++
        sheetBudget(sheets <= SheetLimits.SHEETS)
    }

    private fun checkAddress(address: String) {
        val clean = address.replace("$", "")
        val letters = clean.takeWhile { it.isLetter() }
        require(letters.length in 1..3)
        var column = 0
        letters.forEach { column = column * 26 + (it.uppercaseChar() - 'A' + 1) }
        val row = clean.drop(letters.length).toLongOrNull() ?: error("Invalid cell reference")
        sheetBudget(column in 1..SheetLimits.COLUMNS && row in 1..SheetLimits.ROWS)
    }

    companion object {
        private const val MAX_NODES = 500000
    }
}
