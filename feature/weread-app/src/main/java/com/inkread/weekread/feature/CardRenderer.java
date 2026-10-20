package com.inkread.weekread.feature;

import android.graphics.Canvas;

/**
 * 卡片的绘制入口。
 *
 * <p>拆分前的全部绘制实现已迁入内核包 {@link com.inkread.weekread.kernel.render.CardRenderer}，
 * 本类保留为宿主侧的薄适配层，负责把调用转交给内核渲染器。原有构造方式与
 * {@code WeekCardView} 中的引用保持不变。
 */
final class CardRenderer {

    /** 宿主视图，实现内核 CardView 接口。 */
    private final WeekCardView host;

    /** 内核渲染器。 */
    private final com.inkread.weekread.kernel.render.CardRenderer delegate;

    /**
     * 构造绘制入口。
     *
     * @param host 宿主视图
     */
    CardRenderer(WeekCardView host) {
        this.host = host;
        this.delegate = new com.inkread.weekread.kernel.render.CardRenderer(host);
    }

    /**
     * 绘制一帧。
     *
     * @param c 目标画布
     */
    void draw(Canvas c) {
        delegate.draw(c);
    }
}
