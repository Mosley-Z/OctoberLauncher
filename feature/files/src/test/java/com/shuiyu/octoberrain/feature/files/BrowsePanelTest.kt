package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileSortKey
import com.shuiyu.octoberrain.feature.files.ui.BrowsePanel
import com.shuiyu.octoberrain.feature.files.ui.BrowsePanelState
import com.shuiyu.octoberrain.feature.files.ui.PanelAction
import com.shuiyu.octoberrain.feature.files.ui.PanelRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 操作面板模型测试：核对行顺序、动作映射、选中状态与命中换算。 */
class BrowsePanelTest {
    /** 操作面板八行，长按面板两行。 */
    @Test
    fun `面板行数与顺序固定`() {
        assertEquals(
            listOf(
                PanelRow.NAME,
                PanelRow.TIME,
                PanelRow.SIZE,
                PanelRow.TYPE,
                PanelRow.ORDER,
                PanelRow.HIDDEN,
                PanelRow.VIEW,
                PanelRow.FOLDERS,
            ),
            BrowsePanel.rows,
        )
        assertEquals(2, BrowsePanel.pressedRows.size)
    }

    /** 每行映射到对应动作。 */
    @Test
    fun `面板行动作映射`() {
        assertEquals(PanelAction.SORT_NAME, BrowsePanel.actionOf(PanelRow.NAME))
        assertEquals(PanelAction.TOGGLE_ORDER, BrowsePanel.actionOf(PanelRow.ORDER))
        assertEquals(PanelAction.TOGGLE_VIEW, BrowsePanel.actionOf(PanelRow.VIEW))
        assertEquals(PanelAction.MANAGE_FOLDERS, BrowsePanel.actionOf(PanelRow.FOLDERS))
    }

    /** 选中状态与状态文案随偏好变化。 */
    @Test
    fun `选中状态与状态文案一致`() {
        val state =
            BrowsePanelState(
                sortKey = FileSortKey.TIME,
                ascending = false,
                showHidden = true,
                grid = true,
            )
        assertTrue(BrowsePanel.isSelected(PanelRow.TIME, state))
        assertFalse(BrowsePanel.isSelected(PanelRow.NAME, state))
        assertFalse(BrowsePanel.isSelected(PanelRow.ORDER, state))
        assertEquals("已选", BrowsePanel.stateText(PanelRow.TIME, state))
        assertEquals("", BrowsePanel.stateText(PanelRow.NAME, state))
        assertEquals("降序", BrowsePanel.stateText(PanelRow.ORDER, state))
        assertEquals("开", BrowsePanel.stateText(PanelRow.HIDDEN, state))
        assertEquals("网格", BrowsePanel.stateText(PanelRow.VIEW, state))
        assertEquals("", BrowsePanel.stateText(PanelRow.FOLDERS, state))
    }

    /** 行命中按纵向坐标与行高换算，范围外返回 -1。 */
    @Test
    fun `行命中换算`() {
        assertEquals(0, BrowsePanel.rowAt(y = 100, top = 100, rowHeight = 60, count = 8))
        assertEquals(0, BrowsePanel.rowAt(y = 159, top = 100, rowHeight = 60, count = 8))
        assertEquals(1, BrowsePanel.rowAt(y = 160, top = 100, rowHeight = 60, count = 8))
        assertEquals(7, BrowsePanel.rowAt(y = 579, top = 100, rowHeight = 60, count = 8))
        assertEquals(-1, BrowsePanel.rowAt(y = 580, top = 100, rowHeight = 60, count = 8))
        assertEquals(-1, BrowsePanel.rowAt(y = 99, top = 100, rowHeight = 60, count = 8))
        assertEquals(-1, BrowsePanel.rowAt(y = 100, top = 100, rowHeight = 0, count = 8))
    }
}
