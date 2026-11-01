package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerPaginationTest {
    private fun candidate(index: Int): FolderCandidate = FolderCandidate("app:pkg$index", "标签$index", "pkg$index", true)

    private fun candidates(count: Int): List<FolderCandidate> = (0 until count).map { candidate(it) }

    @Test
    fun pageSizeUsesWholeRows() {
        assertEquals(3, PickerPagination.pageSize(300, 93))
        assertEquals(3, PickerPagination.pageSize(279, 93))
        assertEquals(2, PickerPagination.pageSize(278, 93))
    }

    @Test
    fun pageSizeKeepsAtLeastOneRow() {
        assertEquals(1, PickerPagination.pageSize(92, 93))
        assertEquals(1, PickerPagination.pageSize(0, 93))
        assertEquals(1, PickerPagination.pageSize(-10, 93))
        assertEquals(1, PickerPagination.pageSize(300, 0))
        assertEquals(1, PickerPagination.pageSize(300, -1))
    }

    @Test
    fun pageCountRoundsUpAndKeepsEmptyResultAsOnePage() {
        assertEquals(1, PickerPagination.pageCount(0, 3))
        assertEquals(1, PickerPagination.pageCount(1, 3))
        assertEquals(1, PickerPagination.pageCount(3, 3))
        assertEquals(2, PickerPagination.pageCount(4, 3))
        assertEquals(4, PickerPagination.pageCount(10, 3))
        assertEquals(1, PickerPagination.pageCount(10, 0))
    }

    @Test
    fun clampPageConvergesIntoValidRange() {
        assertEquals(0, PickerPagination.clampPage(-1, 10, 3))
        assertEquals(0, PickerPagination.clampPage(0, 10, 3))
        assertEquals(2, PickerPagination.clampPage(2, 10, 3))
        assertEquals(3, PickerPagination.clampPage(3, 10, 3))
        assertEquals(3, PickerPagination.clampPage(9, 10, 3))
        assertEquals(0, PickerPagination.clampPage(5, 0, 3))
        assertEquals(0, PickerPagination.clampPage(5, 10, 0))
    }

    @Test
    fun sliceReturnsCurrentPageInOrder() {
        val all = candidates(10)
        assertEquals(listOf("app:pkg0", "app:pkg1", "app:pkg2"), PickerPagination.slice(all, 0, 3).map { it.key })
        assertEquals(listOf("app:pkg3", "app:pkg4", "app:pkg5"), PickerPagination.slice(all, 1, 3).map { it.key })
        assertEquals(listOf("app:pkg9"), PickerPagination.slice(all, 3, 3).map { it.key })
    }

    @Test
    fun sliceReturnsEmptyForInvalidPageOrSize() {
        val all = candidates(10)
        assertTrue(PickerPagination.slice(all, 4, 3).isEmpty())
        assertTrue(PickerPagination.slice(all, -1, 3).isEmpty())
        assertTrue(PickerPagination.slice(all, 0, 0).isEmpty())
        assertTrue(PickerPagination.slice(emptyList(), 0, 3).isEmpty())
    }

    @Test
    fun sliceReturnsShortLastPage() {
        val all = candidates(7)
        assertEquals(3, PickerPagination.slice(all, 1, 3).size)
        assertEquals(1, PickerPagination.slice(all, 2, 3).size)
        assertEquals(7, PickerPagination.slice(all, 0, 10).size)
    }
}
