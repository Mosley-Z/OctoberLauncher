package com.inkread.weekread.core;

import com.inkread.weekread.kernel.TodoEntry;

import org.json.JSONObject;

/**
 * 待办条目适配层。
 *
 * <p>字段、标签方法与序列化实现在 {@link TodoEntry}，本类保留原有 public 签名供存储层与界面使用。
 */
public final class TodoItem extends TodoEntry {

    /** 创建空条目。 */
    public TodoItem() {
        super();
    }

    /**
     * 创建条目。
     *
     * @param id      条目编号
     * @param content 内容
     */
    public TodoItem(long id, String content) {
        super(id, content);
    }

    /**
     * 由 JSON 解析条目。
     *
     * <p>字段缺失或类型错误一律取默认值，脏数据不抛出异常。
     *
     * @param o JSON 对象，非空
     * @return 解析结果
     */
    public static TodoItem fromJson(JSONObject o) {
        TodoItem it = new TodoItem();
        TodoEntry.readInto(it, o);
        return it;
    }
}
