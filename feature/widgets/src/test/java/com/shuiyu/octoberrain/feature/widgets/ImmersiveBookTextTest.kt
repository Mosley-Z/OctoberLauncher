package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveBookTextTest {
    @Test
    fun metadataLeavesMainTitleAndOriginalUnchanged() {
        val original = "卡拉马佐夫兄弟（套装上下册）（陀思妥耶夫斯基文集2015）"
        assertEquals("卡拉马佐夫兄弟", immersiveWorkTitle(original))
        assertEquals("故事（一个人的旅程）", immersiveWorkTitle("故事（一个人的旅程）"))
    }

    @Test
    fun durationKeepsUnknownDistinctFromMinutes() {
        assertEquals("—", immersiveReadingDuration(0))
        assertEquals("0m", immersiveReadingDuration(30))
        assertEquals("29h 6m", immersiveReadingDuration(104760))
    }
}
