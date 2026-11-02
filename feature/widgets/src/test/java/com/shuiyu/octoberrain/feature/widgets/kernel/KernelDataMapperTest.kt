package com.shuiyu.octoberrain.feature.widgets.kernel

import com.shuiyu.octoberrain.core.common.WereadStatsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 周期统计映射：快照存在即产出数据，数值允许全为零。 */
class KernelDataMapperTest {
    /** 已同步但本期没有阅读时仍应产出快照，由内核按零值正常渲染形态。 */
    @Test
    fun periodKeepsSnapshotWhenPeriodHasNoReading() {
        val snapshot =
            WereadStatsSnapshot(
                totalMinutes = 0,
                readDays = 0,
                dayAverageMinutes = 0,
                buckets = emptyList(),
            )
        val period = KernelDataMapper.period(snapshot, MODE_WEEKLY)
        assertNotNull(period)
        assertEquals(0, period?.totalSec)
        assertEquals(0, period?.readDays)
        assertEquals(0, period?.dayCount)
    }

    /** 内容服务未返回统计时不产出快照，交由占位文案提示。 */
    @Test
    fun periodIsNullWithoutSnapshot() {
        assertNull(KernelDataMapper.period(null, MODE_WEEKLY))
    }

    /** 有阅读时按分钟换算为秒，并保留阅读天数。 */
    @Test
    fun periodConvertsMinutesToSeconds() {
        val snapshot =
            WereadStatsSnapshot(
                totalMinutes = 95,
                readDays = 3,
                dayAverageMinutes = 31,
            )
        val period = KernelDataMapper.period(snapshot, MODE_WEEKLY)
        assertEquals(95 * SECONDS_PER_MINUTE, period?.totalSec)
        assertEquals(3, period?.readDays)
        assertEquals(31 * SECONDS_PER_MINUTE, period?.avgSec)
    }

    private companion object {
        /** 周形态统计模式标识。 */
        const val MODE_WEEKLY: String = "weekly"

        /** 每分钟秒数。 */
        const val SECONDS_PER_MINUTE: Int = 60
    }
}
