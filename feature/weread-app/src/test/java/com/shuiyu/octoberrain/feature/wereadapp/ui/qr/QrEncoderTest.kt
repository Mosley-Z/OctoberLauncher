package com.shuiyu.octoberrain.feature.wereadapp.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验自实现二维码编码器与参考实现的一致性。
 *
 * 参考矩阵由 Python qrcode 库以字节模式、纠错级别 L 生成，覆盖版本 1 与版本 4
 * 的八个掩码；测试逐模块比对，并解析格式信息确认纠错级别。
 */
class QrEncoderTest {
    /** 与参考矩阵同形的长地址，落在版本 4。 */
    private val longContent = "http://192.168.100.200:9453/k?t=0123456789abcdef0123456789abcdef"

    /** 短内容，落在版本 1。 */
    private val shortContent = "HELLO"

    /** 短内容选择版本一并生成 21 阶矩阵。 */
    @Test
    fun `短内容使用版本一`() {
        val matrix = QrEncoder.encode(shortContent)
        assertEquals(21, matrix.size)
    }

    /** 长地址选择版本四并生成 33 阶矩阵。 */
    @Test
    fun `长内容使用版本四`() {
        val matrix = QrEncoder.encode(longContent)
        assertEquals(33, matrix.size)
    }

    /** 两个样例的矩阵均与参考实现的某个掩码完全一致。 */
    @Test
    fun `矩阵与参考实现一致`() {
        val cases = loadReference()
        assertTrue("参考数据为空", cases.isNotEmpty())
        for (case in cases) {
            val actual = QrEncoder.encode(case.content)
            assertEquals("版本不符", case.version, versionOf(actual.size))
            val matched = case.matrices.any { rows -> matches(actual, rows) }
            assertTrue("内容 ${case.content} 未匹配任何参考掩码", matched)
        }
    }

    /** 格式信息记录的纠错级别为 L，且掩码编号落在合法范围内。 */
    @Test
    fun `格式信息标记纠错级别 L`() {
        for (content in listOf(shortContent, longContent)) {
            val matrix = QrEncoder.encode(content)
            val format = readFormat(matrix)
            assertEquals("纠错级别应为 L", 1, format ushr 3 and 0x03)
            assertTrue("掩码编号越界", (format and 0x07) in 0..7)
        }
    }

    /** 参考用例，来自资源文件的单个版本分组。 */
    private class Case(
        val version: Int,
        val content: String,
        val matrices: List<List<String>>,
    )

    /** 逐模块比较编码结果与参考矩阵行。 */
    private fun matches(
        matrix: QrMatrix,
        rows: List<String>,
    ): Boolean =
        rows.size == matrix.size &&
            rows.indices.all { y ->
                val row = rows[y]
                row.length == matrix.size && row.indices.all { x -> (row[x] == '1') == matrix.isDark(y, x) }
            }

    /** 由矩阵边长反推版本号。 */
    private fun versionOf(size: Int): Int = (size - 17) / 4

    /** 读取第一份格式信息并还原为 15 位码字。 */
    private fun readFormat(matrix: QrMatrix): Int {
        var bits = 0
        for (i in 0 until 6) {
            bits = bits or (if (matrix.isDark(i, 8)) 1 shl i else 0)
        }
        bits = bits or (if (matrix.isDark(7, 8)) 1 shl 6 else 0)
        bits = bits or (if (matrix.isDark(8, 8)) 1 shl 7 else 0)
        bits = bits or (if (matrix.isDark(8, 7)) 1 shl 8 else 0)
        for (i in 9 until 15) {
            bits = bits or (if (matrix.isDark(8, 14 - i)) 1 shl i else 0)
        }
        // 与全部合法格式码字比对，取汉明距离最小者
        var best = -1
        var bestDistance = Int.MAX_VALUE
        for (data in 0 until 32) {
            val distance = Integer.bitCount(bits xor formatBits(data))
            if (distance < bestDistance) {
                bestDistance = distance
                best = data
            }
        }
        assertEquals("格式信息无法还原", 0, bestDistance)
        return best
    }

    /** 按 BCH(15,5) 计算给定数据位的格式码字。 */
    private fun formatBits(data: Int): Int {
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor (if (rem ushr 9 and 1 != 0) 0x537 else 0)
        }
        return ((data shl 10) or rem) xor 0x5412
    }

    /** 解析资源文件中的参考矩阵分组。 */
    private fun loadReference(): List<Case> {
        val stream =
            javaClass.classLoader?.getResourceAsStream("qr-reference.txt")
                ?: return emptyList()
        val lines = stream.bufferedReader().use { it.readLines() }
        val cases = ArrayList<Case>()
        var version = 0
        var content = ""
        var matrices = ArrayList<List<String>>()
        var current: ArrayList<String>? = null
        for (line in lines) {
            when {
                line.startsWith("CASE ") -> {
                    version = line.removePrefix("CASE ").trim().toInt()
                    matrices = ArrayList()
                }
                line.startsWith("CONTENT ") -> content = line.removePrefix("CONTENT ")
                line.startsWith("MASK ") -> {
                    current = ArrayList()
                    matrices.add(current)
                }
                line == "END" -> {
                    cases.add(Case(version, content, matrices))
                    current = null
                }
                else -> current?.add(line)
            }
        }
        return cases
    }
}
