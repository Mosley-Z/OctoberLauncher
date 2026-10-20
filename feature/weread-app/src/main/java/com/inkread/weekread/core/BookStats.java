package com.inkread.weekread.core;

import com.inkread.weekread.kernel.BookProgress;

import org.json.JSONObject;

/**
 * 本书阅读进度适配层。
 *
 * <p>字段、百分比换算与序列化实现在 {@link BookProgress}，本类保留原有 public 签名，
 * 供书架缓存、本书卡片与账单进度解析使用。
 */
public class BookStats extends BookProgress {

    /**
     * 由 JSON 解析阅读进度。
     *
     * @param o JSON 对象，为空时返回 null
     * @return 解析结果，入参为空返回 null
     */
    public static BookStats fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        BookStats b = new BookStats();
        BookProgress.readInto(b, o);
        return b;
    }

    /**
     * 从进度接口的 book 对象读取读完时间。
     *
     * @param s    目标实例
     * @param book 进度接口返回的 book 对象
     */
    public static void applyFinishTime(BookStats s, JSONObject book) {
        BookProgress.applyFinishTime(s, book);
    }

    /**
     * 从书架条目读取读完标记。
     *
     * @param s         目标实例
     * @param shelfBook 书架接口返回的书目对象
     */
    public static void applyFinishReading(BookStats s, JSONObject shelfBook) {
        BookProgress.applyFinishReading(s, shelfBook);
    }
}
