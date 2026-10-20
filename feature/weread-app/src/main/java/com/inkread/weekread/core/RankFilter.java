package com.inkread.weekread.core;

import android.content.Context;

import com.inkread.weekread.kernel.PeriodSnapshot;

import java.util.List;

/**
 * 书籍排名书架过滤适配层。
 *
 * <p>过滤判据与顺序保持实现在内核 {@link com.inkread.weekread.kernel.RankFilter}，
 * 本类补上读取用户开关与全量书架清单两项依赖 Context 的能力。开关关闭、无全量书架清单、
 * 入参为空三种情形均原样返回入参，渲染层零差异。
 */
public final class RankFilter {

    private RankFilter() {
    }

    /**
     * 按用户开关过滤一份读书排行列表。
     *
     * @param c   上下文，用于读取开关与书架清单；为空时原样返回
     * @param src 原始列表，可为空
     * @return 过滤后的新列表，或原列表本身
     */
    public static List<PeriodStats.Longest> apply(Context c, List<PeriodStats.Longest> src) {
        return apply(c, src, c != null && CardPrefs.isRankShelfOnly(c));
    }

    /**
     * 按显式开关过滤一份读书排行列表。
     *
     * @param c         上下文，用于读取书架清单；为空时原样返回
     * @param src       原始列表，可为空
     * @param shelfOnly true 表示启用过滤
     * @return 过滤后的新列表，或原列表本身
     */
    public static List<PeriodStats.Longest> apply(Context c, List<PeriodStats.Longest> src, boolean shelfOnly) {
        if (src == null || src.isEmpty()) {
            return src;
        }
        if (c == null || !shelfOnly) {
            return src;
        }
        return com.inkread.weekread.kernel.RankFilter.apply(src, true, BookStore.shelfIds(c));
    }
}
