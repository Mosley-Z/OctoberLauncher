import com.inkread.weekread.kernel.PeriodCalendar;

/** 打印当前周与月向前移动的周期起点，供验证数据构造使用。 */
public final class PeriodShiftProbe {
    /** 入口：输出周与月向前各期起点秒数。 */
    public static void main(String[] args) {
        long now = PeriodCalendar.nowSec();
        long week = PeriodCalendar.startOf(PeriodCalendar.WEEKLY, now);
        long month = PeriodCalendar.startOf(PeriodCalendar.MONTHLY, now);
        System.out.println("weekBase=" + week);
        System.out.println("monthBase=" + month);
        long w = week;
        for (int i = 1; i <= 52; i++) {
            w = PeriodCalendar.shift(PeriodCalendar.WEEKLY, w, -1);
            if (i <= 3 || i == 52) System.out.println("week-" + i + "=" + w);
        }
        long m = month;
        for (int i = 1; i <= 12; i++) {
            m = PeriodCalendar.shift(PeriodCalendar.MONTHLY, m, -1);
            if (i <= 3 || i == 12) System.out.println("month-" + i + "=" + m);
        }
    }
}
