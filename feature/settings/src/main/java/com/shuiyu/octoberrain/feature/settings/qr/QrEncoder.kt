package com.shuiyu.octoberrain.feature.settings.qr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * 二维码矩阵。
 *
 * @property size 每行每列的模块数量。
 * @property dark 按行优先存储的模块颜色，true 表示深色模块。
 */
internal class QrMatrix(
    val size: Int,
    val dark: BooleanArray,
) {
    /**
     * 读取指定位置的模块颜色。
     *
     * @param row 行下标，范围为 0 至 [size] 减一。
     * @param column 列下标，范围为 0 至 [size] 减一。
     * @return 深色模块返回 true。
     */
    fun isDark(
        row: Int,
        column: Int,
    ): Boolean = dark[row * size + column]

    /**
     * 写入指定位置的模块颜色。
     *
     * @param row 行下标，调用方保证落在有效范围内。
     * @param column 列下标，调用方保证落在有效范围内。
     * @param value 深色模块传 true。
     */
    fun setDark(
        row: Int,
        column: Int,
        value: Boolean,
    ) {
        dark[row * size + column] = value
    }

    /**
     * 复制一份颜色相同的新矩阵。
     *
     * @return 独立副本。
     */
    fun copy(): QrMatrix = QrMatrix(size, dark.copyOf())
}

/**
 * 轻量二维码编码器。
 *
 * 只实现字节模式与纠错级别 L，版本范围 1 至 10。局域网录密钥地址约 60 至 70 字符，
 * 版本 4 至 6 足以容纳；采用自实现编码以维持在离线构建环境下的零第三方依赖。
 *
 * 编码分为五步：选择版本、生成数据码字与纠错码字、布局功能图形、按之字形填充数据、
 * 逐掩码评分后写入格式信息。矩阵生成与位图渲染分离，前者可在单元测试中直接比对。
 *
 * 矩阵布局与掩码评分按规范逐格遍历，分支深度与函数数量由编码规范本身决定，
 * 拆分反而割裂步骤对应关系，因此对两项度量局部豁免。
 */
@Suppress("TooManyFunctions", "NestedBlockDepth")
internal object QrEncoder {
    /** 字节模式指示符。 */
    private const val MODE_BYTE = 0b0100

    /** 纠错级别 L 的两位编码。 */
    private const val ECC_L = 0b01

    /** 格式信息掩码常量。 */
    private const val FORMAT_MASK = 0x5412

    /** 格式信息生成多项式。 */
    private const val FORMAT_POLY = 0x537

    /** 版本信息生成多项式。 */
    private const val VERSION_POLY = 0x1F25

    /** 支持的最大版本号。 */
    const val MAX_VERSION = 10

    /** 类探测图形模式，一比一比一比一加四个浅色模块。 */
    private val FIRST_PATTERN =
        booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)

    /** 类探测图形模式的镜像形式。 */
    private val SECOND_PATTERN =
        booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)

    /** 各版本的纠错级别 L 参数，下标为版本号减一。 */
    private val VERSIONS: List<VersionSpec> =
        listOf(
            VersionSpec(1, 7, listOf(BlockGroup(1, 19)), emptyList()),
            VersionSpec(2, 10, listOf(BlockGroup(1, 34)), listOf(6, 18)),
            VersionSpec(3, 15, listOf(BlockGroup(1, 55)), listOf(6, 22)),
            VersionSpec(4, 20, listOf(BlockGroup(1, 80)), listOf(6, 26)),
            VersionSpec(5, 26, listOf(BlockGroup(1, 108)), listOf(6, 30)),
            VersionSpec(6, 18, listOf(BlockGroup(2, 68)), listOf(6, 34)),
            VersionSpec(7, 20, listOf(BlockGroup(2, 78)), listOf(6, 22, 38)),
            VersionSpec(8, 24, listOf(BlockGroup(2, 97)), listOf(6, 24, 42)),
            VersionSpec(9, 30, listOf(BlockGroup(2, 116)), listOf(6, 26, 46)),
            VersionSpec(10, 18, listOf(BlockGroup(2, 68), BlockGroup(2, 69)), listOf(6, 28, 50)),
        )

    /** 分块参数，表示同组内块数与每块数据码字数。 */
    private class BlockGroup(
        val count: Int,
        val dataCodewords: Int,
    )

    /** 单个版本的参数。 */
    private class VersionSpec(
        val version: Int,
        val eccPerBlock: Int,
        val groups: List<BlockGroup>,
        val alignment: List<Int>,
    ) {
        /** 数据码字总数。 */
        val dataCodewords: Int = groups.sumOf { it.count * it.dataCodewords }

        /** 矩阵边长。 */
        val size: Int = 17 + version * 4
    }

    /**
     * 生成二维码矩阵。
     *
     * @param content 需要编码的文本，按 UTF-8 转字节。
     * @return 版本 1 至 10 中容纳内容的最小矩阵。
     * @throws IllegalArgumentException 内容超出最大版本容量时抛出。
     */
    fun encode(content: String): QrMatrix {
        val data = content.toByteArray(Charsets.UTF_8)
        val spec = selectVersion(data.size)
        val matrix = layout(spec)
        val function = layoutFunction(spec)
        placeData(matrix, function, buildCodewords(spec, data))
        var best: QrMatrix? = null
        var bestScore = Int.MAX_VALUE
        for (mask in 0 until 8) {
            val candidate = matrix.copy()
            applyMask(candidate, function, mask)
            writeFormat(candidate, mask)
            writeVersion(candidate, spec.version)
            val score = score(candidate)
            if (score < bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best ?: error("二维码掩码选择失败")
    }

    /**
     * 把矩阵渲染为位图。
     *
     * @param matrix 待渲染矩阵。
     * @param sizePx 位图边长，单位为像素。
     * @param quietZone 静默区宽度，单位为模块。
     * @return 黑白位图。
     */
    fun render(
        matrix: QrMatrix,
        sizePx: Int,
        quietZone: Int = 4,
    ): Bitmap {
        val total = matrix.size + quietZone * 2
        val scale = (sizePx / total).coerceAtLeast(1)
        val side = total * scale
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(side * side) { Color.WHITE }
        for (row in 0 until matrix.size) {
            for (column in 0 until matrix.size) {
                if (!matrix.isDark(row, column)) continue
                val left = (column + quietZone) * scale
                val top = (row + quietZone) * scale
                for (y in top until top + scale) {
                    val base = y * side
                    for (x in left until left + scale) {
                        pixels[base + x] = Color.BLACK
                    }
                }
            }
        }
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        return bitmap
    }

    /**
     * 选择能容纳指定字节数的最小版本。
     *
     * @param byteCount 内容字节数。
     * @return 对应版本参数。
     * @throws IllegalArgumentException 十个版本均不足以容纳时抛出。
     */
    private fun selectVersion(byteCount: Int): VersionSpec {
        for (spec in VERSIONS) {
            val countBits = if (spec.version <= 9) 8 else 16
            val needed = 4 + countBits + byteCount * 8
            if (needed <= spec.dataCodewords * 8) return spec
        }
        throw IllegalArgumentException("内容超出二维码容量上限")
    }

    /**
     * 生成数据码字与纠错码字并按标准交织。
     *
     * @param spec 版本参数。
     * @param data 内容字节。
     * @return 交织后的完整码字序列。
     */
    private fun buildCodewords(
        spec: VersionSpec,
        data: ByteArray,
    ): IntArray {
        val countBits = if (spec.version <= 9) 8 else 16
        val bits = BitWriter()
        bits.append(MODE_BYTE, 4)
        bits.append(data.size, countBits)
        for (byte in data) bits.append(byte.toInt() and 0xFF, 8)

        val capacityBits = spec.dataCodewords * 8
        bits.append(0, minOf(4, capacityBits - bits.size))
        bits.padToByteBoundary()
        var pad = 0xEC
        while (bits.size < capacityBits) {
            bits.append(pad, 8)
            pad = if (pad == 0xEC) 0x11 else 0xEC
        }

        val payload = bits.toByteArray()
        val dataBlocks = ArrayList<ByteArray>()
        val eccBlocks = ArrayList<ByteArray>()
        var offset = 0
        for (group in spec.groups) {
            repeat(group.count) {
                val block = payload.copyOfRange(offset, offset + group.dataCodewords)
                offset += group.dataCodewords
                dataBlocks.add(block)
                eccBlocks.add(reedSolomon(block, spec.eccPerBlock))
            }
        }

        val result = ArrayList<Int>()
        val maxData = dataBlocks.maxOf { it.size }
        for (index in 0 until maxData) {
            for (block in dataBlocks) {
                if (index < block.size) result.add(block[index].toInt() and 0xFF)
            }
        }
        for (index in 0 until spec.eccPerBlock) {
            for (block in eccBlocks) result.add(block[index].toInt() and 0xFF)
        }
        return result.toIntArray()
    }

    /**
     * 计算一个数据块的纠错码字。
     *
     * @param block 数据码字。
     * @param eccCount 需要生成的纠错码字数量。
     * @return 纠错码字，长度为 [eccCount]。
     */
    private fun reedSolomon(
        block: ByteArray,
        eccCount: Int,
    ): ByteArray {
        val generator = generatorPolynomial(eccCount)
        val result = IntArray(eccCount)
        for (byte in block) {
            val factor = (byte.toInt() and 0xFF) xor result[0]
            System.arraycopy(result, 1, result, 0, eccCount - 1)
            result[eccCount - 1] = 0
            for (index in 0 until eccCount) {
                result[index] = result[index] xor gfMultiply(generator[index], factor)
            }
        }
        return ByteArray(eccCount) { result[it].toByte() }
    }

    /**
     * 生成指定阶数的里德所罗门生成多项式系数。
     *
     * @param degree 生成多项式次数，等于纠错码字数量。
     * @return 从最高次到一次项的系数，首项一省略。
     */
    private fun generatorPolynomial(degree: Int): IntArray {
        var polynomial = intArrayOf(1)
        for (index in 0 until degree) {
            val next = IntArray(polynomial.size + 1)
            for (position in polynomial.indices) {
                next[position] = next[position] xor polynomial[position]
                next[position + 1] = next[position + 1] xor gfMultiply(polynomial[position], gfPower(index))
            }
            polynomial = next
        }
        return polynomial.copyOfRange(1, polynomial.size)
    }

    /**
     * GF(256) 乘法，本原多项式取 0x11D。
     *
     * @param left 乘数，取值 0 至 255。
     * @param right 乘数，取值 0 至 255。
     * @return 乘积，取值 0 至 255。
     */
    private fun gfMultiply(
        left: Int,
        right: Int,
    ): Int {
        var a = left and 0xFF
        var b = right and 0xFF
        var product = 0
        while (b != 0) {
            if (b and 1 != 0) product = product xor a
            b = b ushr 1
            a = a shl 1
            if (a and 0x100 != 0) a = a xor 0x11D
        }
        return product and 0xFF
    }

    /**
     * 计算 GF(256) 中二的指定次幂。
     *
     * @param exponent 指数，取值 0 至 254。
     * @return 幂值。
     */
    private fun gfPower(exponent: Int): Int {
        var value = 1
        repeat(exponent) { value = gfMultiply(value, 2) }
        return value
    }

    /**
     * 建立包含全部功能图形的矩阵，数据区保持浅色。
     *
     * @param spec 版本参数。
     * @return 功能图形已写入的矩阵。
     */
    private fun layout(spec: VersionSpec): QrMatrix {
        val size = spec.size
        val matrix = QrMatrix(size, BooleanArray(size * size))

        drawFinder(matrix, 0, 0)
        drawFinder(matrix, size - 7, 0)
        drawFinder(matrix, 0, size - 7)

        for (index in 6 until size - 6) {
            matrix.setDark(6, index, index % 2 == 0)
            matrix.setDark(index, 6, index % 2 == 0)
        }

        for (row in spec.alignment) {
            for (column in spec.alignment) {
                if (isOverFinder(row, column, size)) continue
                drawAlignment(matrix, row, column)
            }
        }

        matrix.setDark(size - 8, 8, true)
        return matrix
    }

    /**
     * 建立功能区标记矩阵，标记格式信息、版本信息与定位图形覆盖的位置。
     *
     * @param spec 版本参数。
     * @return 功能区标记矩阵，深色表示该位置不可写入数据。
     */
    private fun layoutFunction(spec: VersionSpec): QrMatrix {
        val size = spec.size
        val function = QrMatrix(size, BooleanArray(size * size))

        markFinder(function, 0, 0)
        markFinder(function, size - 7, 0)
        markFinder(function, 0, size - 7)

        for (index in 6 until size - 6) {
            function.setDark(6, index, true)
            function.setDark(index, 6, true)
        }

        for (row in spec.alignment) {
            for (column in spec.alignment) {
                if (isOverFinder(row, column, size)) continue
                for (dr in -2..2) {
                    for (dc in -2..2) function.setDark(row + dr, column + dc, true)
                }
            }
        }

        for (index in 0 until 9) {
            function.setDark(8, index, true)
            function.setDark(index, 8, true)
        }
        for (index in 0 until 8) {
            function.setDark(8, size - 1 - index, true)
            function.setDark(size - 1 - index, 8, true)
        }
        if (spec.version >= 7) {
            for (row in 0 until 6) {
                for (column in 0 until 3) {
                    function.setDark(row, size - 11 + column, true)
                    function.setDark(size - 11 + column, row, true)
                }
            }
        }
        return function
    }

    /**
     * 判断对齐图形中心是否与三个探测图形重叠。
     *
     * @param row 中心行坐标。
     * @param column 中心列坐标。
     * @param size 矩阵边长。
     * @return 重叠时返回 true。
     */
    private fun isOverFinder(
        row: Int,
        column: Int,
        size: Int,
    ): Boolean {
        val last = size - 7
        return (row == 6 && column == 6) ||
            (row == 6 && column == last) ||
            (row == last && column == 6)
    }

    /**
     * 绘制一个位置探测图形及其分隔符。
     *
     * @param matrix 目标矩阵。
     * @param row 图形左上角行坐标。
     * @param column 图形左上角列坐标。
     */
    private fun drawFinder(
        matrix: QrMatrix,
        row: Int,
        column: Int,
    ) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = row + dr
                val c = column + dc
                if (r !in 0 until matrix.size || c !in 0 until matrix.size) continue
                val dark =
                    dr in 0..6 &&
                        dc in 0..6 &&
                        (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                matrix.setDark(r, c, dark)
            }
        }
    }

    /**
     * 标记位置探测图形与分隔符覆盖的位置。
     *
     * @param function 功能区标记矩阵。
     * @param row 图形左上角行坐标。
     * @param column 图形左上角列坐标。
     */
    private fun markFinder(
        function: QrMatrix,
        row: Int,
        column: Int,
    ) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = row + dr
                val c = column + dc
                if (r !in 0 until function.size || c !in 0 until function.size) continue
                function.setDark(r, c, true)
            }
        }
    }

    /**
     * 绘制一个对齐图形。
     *
     * @param matrix 目标矩阵。
     * @param row 中心行坐标。
     * @param column 中心列坐标。
     */
    private fun drawAlignment(
        matrix: QrMatrix,
        row: Int,
        column: Int,
    ) {
        for (dr in -2..2) {
            for (dc in -2..2) {
                matrix.setDark(row + dr, column + dc, abs(dr).coerceAtLeast(abs(dc)) != 1)
            }
        }
    }

    /**
     * 按标准之字形路径填充数据码字。
     *
     * @param matrix 目标矩阵。
     * @param function 功能区标记矩阵。
     * @param codewords 交织后的完整码字序列。
     */
    private fun placeData(
        matrix: QrMatrix,
        function: QrMatrix,
        codewords: IntArray,
    ) {
        var bitIndex = 0
        var right = matrix.size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vertical in 0 until matrix.size) {
                for (offset in 0..1) {
                    val column = right - offset
                    val upward = ((right + 1) and 2) == 0
                    val row = if (upward) matrix.size - 1 - vertical else vertical
                    if (function.isDark(row, column)) continue
                    val bit =
                        if (bitIndex < codewords.size * 8) {
                            (codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1
                        } else {
                            0
                        }
                    matrix.setDark(row, column, bit == 1)
                    bitIndex++
                }
            }
            right -= 2
        }
    }

    /**
     * 应用指定掩码到数据区模块。
     *
     * @param matrix 目标矩阵。
     * @param function 功能区标记矩阵。
     * @param mask 掩码编号，取值 0 至 7。
     */
    private fun applyMask(
        matrix: QrMatrix,
        function: QrMatrix,
        mask: Int,
    ) {
        for (row in 0 until matrix.size) {
            for (column in 0 until matrix.size) {
                if (function.isDark(row, column)) continue
                if (maskBit(mask, row, column)) {
                    matrix.setDark(row, column, !matrix.isDark(row, column))
                }
            }
        }
    }

    /**
     * 计算掩码在指定位置是否翻转。
     *
     * @param mask 掩码编号，取值 0 至 7。
     * @param row 行坐标。
     * @param column 列坐标。
     * @return 需要翻转时返回 true。
     */
    private fun maskBit(
        mask: Int,
        row: Int,
        column: Int,
    ): Boolean =
        when (mask) {
            0 -> (row + column) % 2 == 0
            1 -> row % 2 == 0
            2 -> column % 3 == 0
            3 -> (row + column) % 3 == 0
            4 -> (row / 2 + column / 3) % 2 == 0
            5 -> (row * column) % 2 + (row * column) % 3 == 0
            6 -> ((row * column) % 2 + (row * column) % 3) % 2 == 0
            else -> ((row + column) % 2 + (row * column) % 3) % 2 == 0
        }

    /**
     * 写入格式信息。
     *
     * @param matrix 目标矩阵。
     * @param mask 掩码编号，取值 0 至 7。
     */
    private fun writeFormat(
        matrix: QrMatrix,
        mask: Int,
    ) {
        val bits = formatBits(mask)
        val size = matrix.size
        for (index in 0 until 15) {
            val bit = (bits ushr index) and 1 == 1
            when {
                index < 6 -> matrix.setDark(index, 8, bit)
                index == 6 -> matrix.setDark(7, 8, bit)
                index == 7 -> matrix.setDark(8, 8, bit)
                index == 8 -> matrix.setDark(8, 7, bit)
                else -> matrix.setDark(8, 14 - index, bit)
            }
        }
        for (index in 0 until 8) {
            matrix.setDark(8, size - 1 - index, (bits ushr index) and 1 == 1)
        }
        for (index in 8 until 15) {
            matrix.setDark(size - 15 + index, 8, (bits ushr index) and 1 == 1)
        }
        matrix.setDark(size - 8, 8, true)
    }

    /**
     * 计算带掩码的格式信息位。
     *
     * @param mask 掩码编号，取值 0 至 7。
     * @return 十五位格式信息。
     */
    private fun formatBits(mask: Int): Int {
        val value = (ECC_L shl 3) or mask
        var remainder = value
        repeat(10) { remainder = (remainder shl 1) xor (((remainder ushr 9) and 1) * FORMAT_POLY) }
        return ((value shl 10) or remainder) xor FORMAT_MASK
    }

    /**
     * 写入版本信息，版本七及以上才存在。
     *
     * @param matrix 目标矩阵。
     * @param version 版本号。
     */
    private fun writeVersion(
        matrix: QrMatrix,
        version: Int,
    ) {
        if (version < 7) return
        var remainder = version
        repeat(12) { remainder = (remainder shl 1) xor (((remainder ushr 11) and 1) * VERSION_POLY) }
        val bits = (version shl 12) or remainder
        val size = matrix.size
        for (index in 0 until 18) {
            val bit = (bits ushr index) and 1 == 1
            val row = index / 3
            val column = size - 11 + index % 3
            matrix.setDark(row, column, bit)
            matrix.setDark(column, row, bit)
        }
    }

    /**
     * 按四条标准规则计算掩码评分，分数越低越优。
     *
     * @param matrix 已应用掩码与格式信息的矩阵。
     * @return 评分。
     */
    private fun score(matrix: QrMatrix): Int {
        val size = matrix.size
        var penalty = 0

        for (row in 0 until size) {
            penalty += runPenalty(size) { matrix.isDark(row, it) }
            penalty += runPenalty(size) { matrix.isDark(it, row) }
        }
        for (row in 0 until size - 1) {
            for (column in 0 until size - 1) {
                val first = matrix.isDark(row, column)
                if (matrix.isDark(row, column + 1) == first &&
                    matrix.isDark(row + 1, column) == first &&
                    matrix.isDark(row + 1, column + 1) == first
                ) {
                    penalty += 3
                }
            }
        }
        for (row in 0 until size) {
            penalty += finderPatternPenalty(size) { matrix.isDark(row, it) }
            penalty += finderPatternPenalty(size) { matrix.isDark(it, row) }
        }

        val dark = matrix.dark.count { it }
        val percent = dark * 100 / (size * size)
        penalty += 10 * (abs(percent - 50) / 5)
        return penalty
    }

    /**
     * 计算一维序列中连续同色模块的扣分。
     *
     * @param size 序列长度。
     * @param read 读取指定下标模块颜色的函数。
     * @return 扣分，每段长度超出五的部分另计。
     */
    private fun runPenalty(
        size: Int,
        read: (Int) -> Boolean,
    ): Int {
        var penalty = 0
        var runColor = read(0)
        var runLength = 1
        for (index in 1 until size) {
            val color = read(index)
            if (color == runColor) {
                runLength++
            } else {
                if (runLength >= 5) penalty += 3 + (runLength - 5)
                runColor = color
                runLength = 1
            }
        }
        if (runLength >= 5) penalty += 3 + (runLength - 5)
        return penalty
    }

    /**
     * 统计一维序列中出现类探测图形模式的次数。
     *
     * @param size 序列长度。
     * @param read 读取指定下标模块颜色的函数。
     * @return 每次命中扣四十分。
     */
    private fun finderPatternPenalty(
        size: Int,
        read: (Int) -> Boolean,
    ): Int {
        var penalty = 0
        for (start in 0..size - 11) {
            var first = true
            var second = true
            for (offset in 0 until 11) {
                val value = read(start + offset)
                if (value != FIRST_PATTERN[offset]) first = false
                if (value != SECOND_PATTERN[offset]) second = false
            }
            if (first || second) penalty += 40
        }
        return penalty
    }

    /** 按位写入器，用于拼装数据码字。 */
    private class BitWriter {
        private val bytes = ArrayList<Byte>()
        private var current = 0
        private var bitCount = 0

        /** 已写入的位数。 */
        val size: Int
            get() = bytes.size * 8 + bitCount

        /**
         * 追加指定数量的低位。
         *
         * @param value 待写入的整数值。
         * @param count 写入位数。
         */
        fun append(
            value: Int,
            count: Int,
        ) {
            for (index in count - 1 downTo 0) {
                current = (current shl 1) or ((value ushr index) and 1)
                bitCount++
                if (bitCount == 8) {
                    bytes.add(current.toByte())
                    current = 0
                    bitCount = 0
                }
            }
        }

        /** 补零到字节边界。 */
        fun padToByteBoundary() {
            if (bitCount > 0) append(0, 8 - bitCount)
        }

        /**
         * 导出已写入的字节。
         *
         * @return 字节序列。
         */
        fun toByteArray(): ByteArray = bytes.toByteArray()
    }
}
