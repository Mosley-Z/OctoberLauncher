package com.shuiyu.octoberrain.core.common

/**
 * 一条待办。
 *
 * 字段与微读墨记内核的 `com.inkread.weekread.kernel.TodoEntry` 对齐，写回
 * `kernel_prefs.json` 的 `todo_list` 时使用同名键，日与时间均可为空：
 * [dateSec] 为当天零点的秒数，[minuteOfDay] 为当天第几分钟，未设时均为哨兵值。
 *
 * @param id 唯一标识，由存储层自增分配
 * @param content 内容，必填
 * @param dateSec 日期，当天零点秒数，零表示未设置
 * @param minuteOfDay 时间，当天第几分钟，负一表示未设置
 * @param done 是否已完成
 * @param order 排序序号，升序排列，数值越小越靠前
 */
data class WereadTodo(
    val id: Long = 0L,
    val content: String = "",
    val dateSec: Long = 0L,
    val minuteOfDay: Int = NO_TIME,
    val done: Boolean = false,
    val order: Long = 0L,
) {
    /** 是否设置了日期。 */
    val hasDate: Boolean
        get() = dateSec > 0L

    /** 是否设置了时间。 */
    val hasTime: Boolean
        get() = minuteOfDay >= 0 && minuteOfDay < MINUTES_PER_DAY

    /** 日期时间列的文字，按日期与时间是否设置组合，均未设置时返回空串。 */
    val whenLabel: String
        get() {
            val date = if (hasDate) dateLabel() else null
            val time = if (hasTime) timeLabel() else null
            return listOfNotNull(date, time).joinToString(" ")
        }

    /** 日期文字，当年只写月日，跨年补年份。 */
    fun dateLabel(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = dateSec * MILLIS_PER_SECOND
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val nowCalendar = java.util.Calendar.getInstance()
        val nowYear = nowCalendar.get(java.util.Calendar.YEAR)
        return if (year != nowYear) "${year}年${month}月${day}日" else "${month}月${day}日"
    }

    /** 时间文字，格式为两位补零的时分。 */
    fun timeLabel(): String {
        val hour = minuteOfDay / MINUTES_PER_HOUR
        val minute = minuteOfDay % MINUTES_PER_HOUR
        val hourText = if (hour < 10) "0$hour" else hour.toString()
        val minuteText = if (minute < 10) "0$minute" else minute.toString()
        return "$hourText:$minuteText"
    }

    companion object {
        /** 未设置时间的哨兵值。 */
        const val NO_TIME: Int = -1

        /** 一天的分钟数。 */
        const val MINUTES_PER_DAY: Int = 1440

        /** 一小时的分钟数。 */
        const val MINUTES_PER_HOUR: Int = 60

        /** 每秒的毫秒数。 */
        const val MILLIS_PER_SECOND: Long = 1000L
    }
}

/**
 * 待办数据入口，由应用层装配，界面层只依赖本接口。
 *
 * 所有方法均返回操作后的完整清单，便于界面直接重绘；实现需保证线程安全。
 */
interface WereadTodoHost {
    /**
     * 读取全部待办。
     *
     * @return 按排序序号升序排列的清单
     */
    fun list(): List<WereadTodo>

    /**
     * 新增一条待办。
     *
     * @param content 内容，空白时忽略
     * @param dateSec 日期，当天零点秒数，零表示未设置
     * @param minuteOfDay 时间，当天第几分钟，负一表示未设置
     * @return 操作后的完整清单
     */
    fun add(
        content: String,
        dateSec: Long = 0L,
        minuteOfDay: Int = WereadTodo.NO_TIME,
    ): List<WereadTodo>

    /**
     * 切换完成状态。
     *
     * @param id 待办标识
     * @return 操作后的完整清单
     */
    fun toggle(id: Long): List<WereadTodo>

    /**
     * 删除一条待办。
     *
     * @param id 待办标识
     * @return 操作后的完整清单
     */
    fun remove(id: Long): List<WereadTodo>

    /**
     * 调整排序位置。
     *
     * @param id 待办标识
     * @param delta 位移，负值上移，正值下移
     * @return 操作后的完整清单
     */
    fun move(
        id: Long,
        delta: Int,
    ): List<WereadTodo>

    /**
     * 修改内容与日期时间。
     *
     * @param id 待办标识
     * @param content 新内容，空白时保持原值
     * @param dateSec 新日期
     * @param minuteOfDay 新时间
     * @return 操作后的完整清单
     */
    fun update(
        id: Long,
        content: String,
        dateSec: Long = 0L,
        minuteOfDay: Int = WereadTodo.NO_TIME,
    ): List<WereadTodo>
}
