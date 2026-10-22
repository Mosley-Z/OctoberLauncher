package com.shuiyu.octoberrain.feature.reader.office.sheets

/** Bounded CFB stream extraction followed by BIFF8 cell accounting, before the JS workbook parser runs. */
internal object BiffBudget {
    fun check(bytes: ByteArray) {
        sheetBudget(bytes.size.toLong() <= SheetLimits.COMPRESSED)
        val records = if (bytes.firstOrNull() == 0xD0.toByte()) CompoundBook(bytes).workbook() else bytes
        checkRecords(records)
    }

    fun checkRecords(bytes: ByteArray) {
        var offset = 0
        var cells = 0
        var sheets = 0
        var records = 0
        require(bytes.size >= 8 && u16(bytes, 0) == 0x0809 && u16(bytes, 4) == 0x0600) { "Only BIFF8 XLS" }
        while (offset < bytes.size) {
            require(offset + 4 <= bytes.size)
            val id = u16(bytes, offset)
            val size = u16(bytes, offset + 2)
            val start = offset + 4
            require(size <= 8224 && start + size <= bytes.size) { "Invalid BIFF record" }
            records++
            sheetBudget(records <= 500000)
            if (id == 0x0200) {
                require(size >= 14)
                sheetBudget(i32(bytes, start + 4) <= SheetLimits.ROWS && u16(bytes, start + 10) <= SheetLimits.COLUMNS)
            }
            if (id in CELL_RECORDS) {
                require(size >= 6)
                val row = u16(bytes, start)
                val column = u16(bytes, start + 2)
                sheetBudget(row < SheetLimits.ROWS && column < SheetLimits.COLUMNS)
                val count = if (id == 0x00BD || id == 0x00BE) u16(bytes, start + size - 2) - column + 1 else 1
                sheetBudget(count in 1..SheetLimits.COLUMNS && column + count <= SheetLimits.COLUMNS)
                cells += count
                sheetBudget(cells <= SheetLimits.CELLS)
            }
            if (id == 0x0085) {
                sheets++
                sheetBudget(sheets <= SheetLimits.SHEETS)
            }
            offset = start + size
        }
    }

    private val CELL_RECORDS = setOf(0x0203, 0x027E, 0x00BD, 0x00BE, 0x0204, 0x00FD, 0x0201, 0x0006, 0x0205)
}

private class CompoundBook(
    private val bytes: ByteArray,
) {
    private val sectorSize: Int
    private val fat: IntArray

    init {
        val signature =
            byteArrayOf(
                0xD0.toByte(),
                0xCF.toByte(),
                0x11,
                0xE0.toByte(),
                0xA1.toByte(),
                0xB1.toByte(),
                0x1A,
                0xE1.toByte(),
            )
        require(bytes.size >= 512 && bytes.copyOfRange(0, 8).contentEquals(signature))
        require(u16(bytes, 28) == 0xFFFE)
        val shift = u16(bytes, 30)
        require(shift == 9 || shift == 12)
        require(u16(bytes, 32) == 6)
        sectorSize = 1 shl shift
        val sectors = i32(bytes, 44)
        sheetBudget(sectors in 1..109 && i32(bytes, 72) == 0)
        fat = IntArray(sectors * sectorSize / 4)
        for (index in 0 until sectors) {
            val data = sector(i32(bytes, 76 + index * 4))
            for (entry in 0 until sectorSize / 4) fat[index * sectorSize / 4 + entry] = i32(data, entry * 4)
        }
    }

    fun workbook(): ByteArray {
        val directory = chain(i32(bytes, 48), fat, sectorSize) { sector(it) }
        var root: Entry? = null
        var book: Entry? = null
        for (offset in directory.indices step 128) {
            require(offset + 128 <= directory.size)
            val nameSize = u16(directory, offset + 64)
            if (nameSize in 2..64 && nameSize % 2 == 0) {
                val name = directory.copyOfRange(offset, offset + nameSize - 2).toString(Charsets.UTF_16LE)
                val type = directory[offset + 66].toInt()
                val entry = Entry(i32(directory, offset + 116), i32(directory, offset + 120))
                require(i32(directory, offset + 124) == 0)
                if (type == 5) root = entry
                if (type == 2 && name in setOf("Workbook", "Book")) {
                    require(book == null)
                    book = entry
                }
            }
        }
        val target = requireNotNull(book) { "Missing workbook stream" }
        sheetBudget(target.size in 1..SheetLimits.EXPANDED.toInt())
        val data =
            if (target.size <
                4096
            ) {
                miniStream(target, requireNotNull(root))
            } else {
                chain(target.start, fat, sectorSize) { sector(it) }
            }
        require(data.size >= target.size)
        return data.copyOf(target.size)
    }

    private fun miniStream(
        target: Entry,
        root: Entry,
    ): ByteArray {
        val miniFatData = chain(i32(bytes, 60), fat, sectorSize) { sector(it) }
        val miniFat = IntArray(miniFatData.size / 4) { i32(miniFatData, it * 4) }
        val stream = chain(root.start, fat, sectorSize) { sector(it) }
        require(root.size in 0..stream.size)
        return chain(target.start, miniFat, 64) { index ->
            val offset = index.toLong() * 64
            require(offset >= 0 && offset + 64 <= root.size)
            stream.copyOfRange(offset.toInt(), offset.toInt() + 64)
        }
    }

    private fun sector(index: Int): ByteArray {
        val offset = (index.toLong() + 1) * sectorSize
        require(index >= 0 && offset + sectorSize <= bytes.size)
        return bytes.copyOfRange(offset.toInt(), offset.toInt() + sectorSize)
    }

    private fun chain(
        start: Int,
        table: IntArray,
        unit: Int,
        read: (Int) -> ByteArray,
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val visited = mutableSetOf<Int>()
        var current = start
        while (current != -2) {
            require(current in table.indices && visited.add(current)) { "Invalid CFB chain" }
            sheetBudget(output.size() + unit <= SheetLimits.EXPANDED)
            output.write(read(current))
            current = table[current]
        }
        return output.toByteArray()
    }

    private data class Entry(
        val start: Int,
        val size: Int,
    )
}

private fun u16(
    bytes: ByteArray,
    offset: Int,
): Int = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

private fun i32(
    bytes: ByteArray,
    offset: Int,
): Int = u16(bytes, offset) or (u16(bytes, offset + 2) shl 16)
