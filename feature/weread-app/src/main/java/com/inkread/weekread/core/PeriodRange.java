package com.inkread.weekread.core;

import com.inkread.weekread.kernel.PeriodCalendar;

/**
 * 周期模式与日历计算的适配层。
 *
 * <p>版本 1.3.2 起，模式常量、日界计算与周期标题生成由内核 {@link PeriodCalendar}
 * 提供，本类仅保留原有类名与静态调用面，既有调用点无需改动。
 */
public final class PeriodRange extends PeriodCalendar {

    private PeriodRange() {
    }
}
