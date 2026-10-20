import com.inkread.weekread.kernel.PeriodCalendar;

/** 探测内核周期标签文案，用于 App 页面周期选择器显示。 */
public final class PeriodLabelProbe {
    /** 入口：打印当前周、当前月的各标签接口输出。 */
    public static void main(String[] args) {
        long now = PeriodCalendar.nowSec();
        long week = PeriodCalendar.startOf("weekly", now);
        long month = PeriodCalendar.startOf("monthly", now);
        System.out.println("weekLabel=" + PeriodCalendar.weekLabel(week));
        System.out.println("monthLabel=" + PeriodCalendar.monthLabel(month));
        System.out.println("weekTitle=" + PeriodCalendar.title("weekly", week, false));
        System.out.println("monthTitle=" + PeriodCalendar.title("monthly", month, false));
        System.out.println("year=" + PeriodCalendar.yearOf(week));
    }
}
