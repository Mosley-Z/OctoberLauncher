package com.inkread.weekread.core;

import com.inkread.weekread.kernel.PeriodSnapshot;

import org.json.JSONObject;

/**
 * 阅读周期统计适配层。
 *
 * <p>字段、解析逻辑与判定方法实现在 {@link PeriodSnapshot}，本类保留原有 public 签名，
 * 供缓存读取与卡片渲染使用。嵌套类型 Longest、PreferCat、StatItem 由父类继承，
 * 既有引用 {@code PeriodStats.Longest} 无需修改。
 */
public class PeriodStats extends PeriodSnapshot {

    /**
     * 解析统计回包。
     *
     * @param j         回包 JSON，可为空，为空时仅填充周期起点与天数
     * @param fetchedAt 拉取时刻（毫秒）
     * @param mode      请求用的周期
     * @param reqBase   请求用的周期基准（秒），回包未带基准时使用
     * @return 解析结果
     */
    public static PeriodStats parse(JSONObject j, long fetchedAt, String mode, long reqBase) {
        PeriodStats s = new PeriodStats();
        PeriodSnapshot.parseInto(s, j, fetchedAt, mode, reqBase);
        return s;
    }
}
