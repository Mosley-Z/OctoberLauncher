package com.inkread.weekread.core;

import com.inkread.weekread.kernel.CardGeometry;

/**
 * 桌面卡片几何规格的适配层。
 *
 * <p>版本 1.3.2 起，全部常量与静态计算方法由内核 {@link CardGeometry} 提供，
 * 本类仅保留原有类名，使既有调用点（{@code CardSpec.CARD_LEFT}、
 * {@code CardSpec.cardWidth()} 等）无需改动。
 *
 * <p>新增几何规格应写入内核，本类不再承载实现。
 */
public final class CardSpec extends CardGeometry {

    private CardSpec() {
    }
}
