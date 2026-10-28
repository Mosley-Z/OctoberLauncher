package com.inkread.weekread.kernel.desk;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.inkread.weekread.kernel.PeriodCalendar;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 墨台整页：装配分区、量总高、绘制、命中分发。
 *
 * <p>本类只承担列表态渲染。设置态、壁纸预览、手势滚动与保存分享由宿主实现；
 * 分区内的选中态（账单期、墨单视图、收入算法、排序、字号）保存在内核内存中，
 * 持久化由宿主在处理公开动作时自行完成。
 */
public final class DeskPage {

    /** 账单分区序号。 */
    public static final int SEC_BILL = 0;
    /** 阅读资产分区序号。 */
    public static final int SEC_ASSET = 1;
    /** 排行分区序号。 */
    public static final int SEC_RANK = 2;
    /** 阅读指纹分区序号。 */
    public static final int SEC_FP = 3;

    /** 未命中任何可点区域。 */
    public static final int ACTION_NONE = 0;
    /** 顶栏返回键。 */
    public static final int ACTION_BACK = 1;
    /** 顶栏设置入口。 */
    public static final int ACTION_SETTINGS = 2;

    /** 宿主能力。 */
    private final DeskHost host;
    /** 数据装配器。 */
    private final DeskData data;
    /** 顶栏与分区渲染器。 */
    private final DeskRenderer renderer;
    /** 分区分派用的共享画笔。 */
    private final Paint padPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 参与渲染的分区，按宿主给定的顺序。 */
    private final List<DeskSection> sections = new ArrayList<DeskSection>();
    /** 与 sections 一一对应的模块标识。 */
    private final List<String> sectionIds = new ArrayList<String>();

    /** 三个分区实例，关闭时保留实例以便恢复选中态。 */
    private BillSection billSection;
    private AssetSection assetSection;
    private FingerprintSection fpSection;

    /** 最近一次绘制时的视口宽度。 */
    private int width;
    /** 最近一次绘制时的视口高度。 */
    private int height;
    /** 最近一次绘制时的滚动位移。 */
    private int offset;

    /** 是否已装配完成。 */
    private boolean bound;

    /**
     * 构造墨台整页。
     *
     * @param host 宿主能力
     * @param root 数据根目录
     */
    public DeskPage(DeskHost host, File root) {
        this.host = host;
        this.data = new DeskData(host, root);
        this.renderer = new DeskRenderer(host);
        renderer.setEmptyText(DeskPrefs.TXT_EMPTY);
        renderer.setLabels(DeskPrefs.TXT_DESK, DeskPrefs.TXT_BACK);
        renderer.setActionText(DeskPrefs.TXT_SETTINGS);
    }

    /**
     * 构造墨台整页，数据根目录取宿主提供的私有目录。
     *
     * @param host 宿主能力
     */
    public DeskPage(DeskHost host) {
        this(host, host == null ? null : host.filesDir());
    }

    /** 数据装配器，供宿主读取同一份缓存。 */
    public DeskData data() {
        return data;
    }

    /**
     * 装配分区。按宿主给的模块开关与顺序生成，关闭的模块不进入分区表因而既不渲染也不占高。
     */
    public void bind() {
        bound = true;
        rebuildSections();
        refreshSections();
    }

    /** 重新装配分区，保留已建实例。 */
    private void rebuildSections() {
        renderer.clear();
        sections.clear();
        sectionIds.clear();
        if (host == null) return;
        String[] order = host.deskOrder();
        if (order == null || order.length == 0) order = DeskPrefs.MODULE_ORDER;
        int mask = host.deskModuleMask();
        for (int i = 0; i < order.length; i++) {
            String id = order[i];
            if (id == null) continue;
            if ((mask & DeskPrefs.bitOf(id)) == 0) continue;
            if (DeskPrefs.MOD_BILL.equals(id)) {
                if (billSection == null) billSection = new BillSection(data);
                sections.add(billSection);
                renderer.add(billSection);
                sectionIds.add(id);
            } else if (DeskPrefs.MOD_RANK.equals(id)) {
                if (assetSection == null) assetSection = new AssetSection();
                sections.add(assetSection);
                renderer.add(assetSection);
                sectionIds.add(id);
            } else if (DeskPrefs.MOD_FP.equals(id)) {
                if (fpSection == null) fpSection = new FingerprintSection(data);
                sections.add(fpSection);
                renderer.add(fpSection);
                sectionIds.add(id);
            }
        }
    }

    /** 刷新各分区的数据与状态键。 */
    private void refreshSections() {
        float unit = unit();
        if (assetSection != null) assetSection.setDeskHeadH(renderer.headerHeight(unit));
        if (billSection != null) billSection.refresh(data);
        if (assetSection != null) assetSection.refresh(data);
        if (fpSection != null) fpSection.refresh();
    }

    /** 长度单位，由宿主的缩放系数给出。 */
    private float unit() {
        float s = host == null ? 1.2f : host.scale();
        return s > 0f ? s : 1.2f;
    }

    /**
     * 绘制整页。
     *
     * @param c      画布
     * @param width  视口宽度
     * @param height 视口高度
     * @param offset 滚动位移，单位像素，向上为正
     */
    public void render(Canvas c, int width, int height, int offset) {
        if (c == null || width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        this.offset = offset;
        if (!bound) bind();
        // Rendering must preserve interaction state. Explicit bind reloads data;
        // refreshing here closes price/algo popups and resets the selected bill.
        renderer.draw(c, width, height, unit(), offset, updatedLabel());
        // 录价键盘画在所有分区之上：与原版 DeskPageView.drawPage 一致，键盘关闭时本调用直接返回。
        if (assetSection != null) assetSection.drawPad(c, width, height, unit(), padPaint);
    }

    /** 顶栏右侧的更新时间文案。 */
    private String updatedLabel() {
        return "更新于 " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    /**
     * 内容总高。
     *
     * @param width 视口宽度
     * @return 像素高度
     */
    public int height(int width) {
        if (width <= 0) width = this.width > 0 ? this.width : 480;
        int vh = height > 0 ? height : 800;
        float total = renderer.contentHeight(width, vh, unit());
        if (billSection != null && billSection.padOpen()) {
            total += billSection.padHeightPx(unit());
        } else if (assetSection != null && assetSection.padOpen()) {
            total += assetSection.padHeightPx(unit());
        }
        return (int) Math.ceil(total);
    }

    /**
     * 命中测试。坐标以视口左上角为原点。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 动作编号，未命中返回 {@link #ACTION_NONE}
     */
    public int hitTest(float x, float y) {
        if (!bound) bind();
        float w = width > 0 ? width : 480;
        float vh = height > 0 ? height : 800;
        float unit = unit();
        if (renderer.hitBack(x, y, w, unit)) return ACTION_BACK;
        if (renderer.hitAction(x, y, w, unit)) return ACTION_SETTINGS;
        float top = renderer.headerHeight(unit) - offset;
        for (int i = 0; i < sections.size(); i++) {
            DeskSection s = sections.get(i);
            float sh = s.height(w, vh, unit);
            if (y >= top && y < top + sh) {
                int action = hitInSection(s, x, y, w, vh, unit, top);
                if (action != ACTION_NONE) return action;
            }
            top += sh;
        }
        return ACTION_NONE;
    }

    /** 在单个分区内命中。 */
    private int hitInSection(DeskSection s, float x, float y, float w, float vh, float unit, float top) {
        if (s == billSection) {
            if (billSection.algoOpen()) {
                int selection = billSection.hitAlgo(x, y, w, unit, top);
                if (selection >= 0 && selection < DeskPrefs.PAID_CHOICES.length) {
                    return DeskActions.ALGO_BASE + selection;
                }
                billSection.setAlgoOpen(false);
                return ACTION_NONE;
            }
            String mode = billSection.hitMode(x, y, w, unit, top);
            if (PeriodCalendar.WEEKLY.equals(mode)) return DeskActions.BILL_WEEK;
            if (PeriodCalendar.MONTHLY.equals(mode)) return DeskActions.BILL_MONTH;
            int arrow = billSection.hitArrow(x, y, w, unit, top);
            if (arrow < 0) return DeskActions.BILL_PREV;
            if (arrow > 0) return DeskActions.BILL_NEXT;
            if (billSection.hitTab(x, y, w, unit, top)) return DeskActions.BILL_TAB;
            int algo = billSection.hitAlgo(x, y, w, unit, top);
            if (algo == BillSection.ALGO_TOGGLE) {
                billSection.setAlgoOpen(!billSection.algoOpen());
                return ACTION_NONE;
            }
            if (algo == BillSection.ALGO_DISMISS) {
                billSection.setAlgoOpen(false);
                return ACTION_NONE;
            }
            if (algo >= 0) return DeskActions.ALGO_BASE + algo;
            if (billSection.hitWallpaper(x, y, w, unit, top)) return DeskActions.WALLPAPER;
            return ACTION_NONE;
        }
        if (s == assetSection) {
            int sort = assetSection.hitSort(x, y, w, unit, top);
            if (sort >= 0) return DeskActions.ASSET_SORT_BASE + sort;
            if (assetSection.hitToggle(x, y, w, vh, unit, top)) return DeskActions.ASSET_TOGGLE;
            if (assetSection.hitWall(x, y, w, unit, top)) return DeskActions.WALLPAPER;
            return ACTION_NONE;
        }
        if (s == fpSection) {
            int font = fpSection.hitFont(x, y, w, unit, top);
            if (font >= 0) return DeskActions.FP_FONT_BASE + font;
            if (fpSection.hitPick(x, y, w, unit, top)) return DeskActions.FP_PICK;
            if (fpSection.hitWall(x, y, w, unit, top)) return DeskActions.WALLPAPER;
            return ACTION_NONE;
        }
        return ACTION_NONE;
    }

    /**
     * 处理公开动作。返回后由宿主决定是否持久化与重绘。
     *
     * @param action 动作编号
     */
    public void onAction(int action) {
        if (host == null) return;
        switch (action) {
            case ACTION_BACK:
                host.openPage("back");
                return;
            case ACTION_SETTINGS:
                host.openPage("settings");
                return;
            case DeskActions.BILL_WEEK:
                if (billSection != null) billSection.switchMode(PeriodCalendar.WEEKLY);
                break;
            case DeskActions.BILL_MONTH:
                if (billSection != null) billSection.switchMode(PeriodCalendar.MONTHLY);
                break;
            case DeskActions.BILL_PREV:
                if (billSection != null) billSection.shiftPeriod(-1);
                break;
            case DeskActions.BILL_NEXT:
                if (billSection != null) billSection.shiftPeriod(1);
                break;
            case DeskActions.BILL_TAB:
                if (billSection != null) {
                    billSection.setView(data, 1 - billSection.view());
                }
                break;
            case DeskActions.WALLPAPER:
                host.openPage("wallpaper");
                return;
            case DeskActions.FP_PICK:
                host.openPage("fingerprint");
                return;
            default:
                if (action >= DeskActions.ALGO_BASE && action < DeskActions.ALGO_BASE + 3) {
                    if (billSection != null) billSection.pickAlgo(action - DeskActions.ALGO_BASE);
                } else if (action >= DeskActions.ASSET_SORT_BASE
                        && action < DeskActions.ASSET_SORT_BASE + 3) {
                    if (assetSection != null) assetSection.setSort(action - DeskActions.ASSET_SORT_BASE);
                } else if (action >= DeskActions.FP_FONT_BASE
                        && action < DeskActions.FP_FONT_BASE + 4) {
                    data.setFpFontPx(DeskPrefs.FP_FONT_CHOICES[action - DeskActions.FP_FONT_BASE]);
                } else if (action == DeskActions.ASSET_TOGGLE) {
                    if (assetSection != null) assetSection.toggleOpen();
                }
                break;
        }
        host.requestRedraw();
    }

    /** 释放缓存位图与分区状态。 */
    public void release() {
        if (fpSection != null) fpSection.release();
        sections.clear();
        if (billSection != null) billSection.closePad();
        if (assetSection != null) assetSection.closePad();
        bound = false;
    }

    // ══════════════════════ 交互扩展（k3） ══════════════════════

    /** 最近一次长按移出的书名；未发生移出时为 null。 */
    private String pendingTitle;

    /**
     * 长按处理。坐标以视口左上角为原点，判定与点按共用同一套分区几何。
     *
     * <p>三类语义：墨单价格行长按打开录价键盘；阅读资产价格区长按打开录价键盘；
     * 阅读资产书名区长按把该书移出统计并记录书名，供宿主提示。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return {@link DeskActions#LONG_BILL_PRICE}、{@link DeskActions#LONG_ASSET_PRICE}、
     *         {@link DeskActions#LONG_ASSET_TITLE} 或 {@link #ACTION_NONE}
     */
    public int longActionAt(float x, float y) {
        if (!bound) bind();
        pendingTitle = null;
        float w = width > 0 ? width : 480;
        float vh = height > 0 ? height : 800;
        float unit = unit();
        float top = renderer.headerHeight(unit) - offset;
        for (int i = 0; i < sections.size(); i++) {
            DeskSection s = sections.get(i);
            float sh = s.height(w, vh, unit);
            if (y >= top && y < top + sh) {
                if (s == billSection) {
                    int idx = billSection.hitPriceRow(x, y, w, unit, top);
                    if (idx >= 0) {
                        billSection.openPad(idx);
                        if (host != null) host.requestRedraw();
                        return DeskActions.LONG_BILL_PRICE;
                    }
                    return ACTION_NONE;
                }
                if (s == assetSection) {
                    int row = assetSection.hitRow(x, y, w, unit, top, true);
                    if (row >= 0) {
                        String bid = assetSection.bookIdAt(row);
                        if (bid != null) assetSection.openPad(bid);
                        else assetSection.openPad(row);
                        if (host != null) host.requestRedraw();
                        return DeskActions.LONG_ASSET_PRICE;
                    }
                    row = assetSection.hitRow(x, y, w, unit, top, false);
                    if (row >= 0) {
                        String bid = assetSection.bookIdAt(row);
                        String title = assetSection.titleOf(bid);
                        if (assetSection.hideBook(data, bid)) {
                            pendingTitle = title;
                            data.invalidateAll();
                            if (host != null) host.requestRedraw();
                            return DeskActions.LONG_ASSET_TITLE;
                        }
                    }
                    return ACTION_NONE;
                }
                return ACTION_NONE;
            }
            top += sh;
        }
        return ACTION_NONE;
    }

    /** 最近一次长按移出的书名；供宿主拼提示条文案。 */
    public String pendingTitle() {
        return pendingTitle;
    }

    /** 录价键盘是否打开。 */
    public boolean padOpen() {
        return (billSection != null && billSection.padOpen())
                || (assetSection != null && assetSection.padOpen());
    }

    /**
     * 键盘命中。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 键码 0–9 或 {@link PricePad#PK_DOT}、{@link PricePad#PK_DEL}、{@link PricePad#PK_OK}、
     *         {@link PricePad#PK_CANCEL}；未命中返回 -1
     */
    public int hitPad(float x, float y) {
        float w = width > 0 ? width : 480;
        float vh = height > 0 ? height : 800;
        float unit = unit();
        if (billSection != null && billSection.padOpen()) return billSection.hitPad(x, y, w, vh, unit);
        if (assetSection != null && assetSection.padOpen()) return assetSection.hitPad(x, y, w, vh, unit);
        return -1;
    }

    /**
     * 按一个键盘键。键码取值范围与 {@link #hitPad(float, float)} 的返回值一致。
     *
     * @param key 键码
     */
    public void padPress(int key) {
        if (billSection != null && billSection.padOpen()) {
            billSection.padPress(key);
        } else if (assetSection != null && assetSection.padOpen()) {
            assetSection.padPress(key);
        }
        if (host != null) host.requestRedraw();
    }

    /** 关闭录价键盘。 */
    public void closePad() {
        if (billSection != null) billSection.closePad();
        if (assetSection != null) assetSection.closePad();
        if (host != null) host.requestRedraw();
    }

    /**
     * 阅读资产分区内滚。仅该分区有意义，其余分区返回 false。
     *
     * @param dy 位移，正值为内容上移
     * @return 是否发生实际滚动；到边界返回 false
     */
    public boolean scrollBy(float dy) {
        if (assetSection == null || sections.indexOf(assetSection) < 0) return false;
        float vh = height > 0 ? height : 800;
        boolean moved = assetSection.scrollBy(dy, vh, unit());
        if (moved && host != null) host.requestRedraw();
        return moved;
    }

    /**
     * 阅读资产分区当前是否可内滚。仅该分区有意义，其余分区返回 false。
     *
     * @return 可滚返回 true
     */
    public boolean canScroll() {
        if (assetSection == null || sections.indexOf(assetSection) < 0) return false;
        float vh = height > 0 ? height : 800;
        return assetSection.canScroll(vh, unit());
    }

    /**
     * 渲染指定分区的壁纸位图。
     *
     * @param section 分区序号，取值 {@link #SEC_BILL}、{@link #SEC_ASSET}、{@link #SEC_FP}
     * @param width   目标宽度
     * @param height  目标高度
     * @return 位图；失败返回 null，调用方负责回收
     */
    public android.graphics.Bitmap renderWallpaper(int section, int width, int height) {
        WallpaperRenderer wp = new WallpaperRenderer(data);
        if (assetSection != null) wp.setSortKey(assetSection.sort());
        int sec = (section == SEC_RANK) ? SEC_ASSET : section;
        return wp.render(sec, width, height);
    }

    /**
     * 选书候选：指纹已选书目、书架书目、笔记书目三者并集，按出现顺序去重。
     *
     * @return 每项为 {书籍标识, 书名, 作者}
     */
    public List<String[]> fingerprintCandidates() {
        List<String[]> out = new ArrayList<String[]>();
        ArrayList<String> seen = new ArrayList<String>();
        List<String> picked = data.fpBooks();
        for (int i = 0; i < picked.size(); i++) {
            String id = picked.get(i);
            if (id == null || id.length() == 0 || seen.contains(id)) continue;
            String title = "";
            String author = "";
            com.inkread.weekread.kernel.ShelfSnapshot shelf = data.loader().shelf();
            if (shelf != null && shelf.books != null) {
                for (int k = 0; k < shelf.books.size(); k++) {
                    com.inkread.weekread.kernel.ShelfBook b = shelf.books.get(k);
                    if (b != null && id.equals(b.bookId)) {
                        title = b.title == null ? "" : b.title;
                        author = b.author == null ? "" : b.author;
                    }
                }
            }
            out.add(new String[] { id, title, author });
            seen.add(id);
        }
        com.inkread.weekread.kernel.ShelfSnapshot shelf = data.loader().shelf();
        if (shelf != null && shelf.books != null) {
            for (int i = 0; i < shelf.books.size(); i++) {
                com.inkread.weekread.kernel.ShelfBook b = shelf.books.get(i);
                if (b == null || b.bookId == null || seen.contains(b.bookId)) continue;
                out.add(new String[] { b.bookId, b.title == null ? "" : b.title,
                        b.author == null ? "" : b.author });
                seen.add(b.bookId);
            }
        }
        List<com.inkread.weekread.kernel.NoteItem> notes = data.loader().notes(0);
        for (int i = 0; i < notes.size(); i++) {
            com.inkread.weekread.kernel.NoteItem n = notes.get(i);
            if (n == null || n.bookId == null || n.bookId.length() == 0 || seen.contains(n.bookId)) continue;
            out.add(new String[] { n.bookId, n.title == null ? "" : n.title,
                    n.author == null ? "" : n.author });
            seen.add(n.bookId);
        }
        return out;
    }

    /**
     * 设定指纹书目。
     *
     * <p>只在本次渲染内生效；持久化由宿主写入自身配置后重新创建页面。
     *
     * @param bookId 书籍标识；null 或空串表示清除覆盖
     */
    public void setFingerprintBook(String bookId) {
        data.setFpBook(bookId);
        if (fpSection != null) fpSection.refresh();
        if (host != null) host.requestRedraw();
    }
    /**
     * 分区在内容坐标系中的起始偏移，用于把视口滚到指定分区。
     *
     * @param secId 分区序号，取值 {@link #SEC_BILL} 等常量
     * @return 内容偏移像素；分区未启用时返回 -1
     */
    public float sectionTop(int secId) {
        // SEC_ASSET 与 SEC_RANK 指向同一分区（阅读资产），两者都要解析到模块标识 rank；
        // 旧实现只认 SEC_RANK，探针按 SEC_ASSET 取偏移时返回 -1。
        String id = secId == SEC_BILL ? DeskPrefs.MOD_BILL
                : (secId == SEC_ASSET || secId == SEC_RANK) ? DeskPrefs.MOD_RANK
                : secId == SEC_FP ? DeskPrefs.MOD_FP : null;
        if (id == null) return -1f;
        float w = width > 0 ? width : 480f;
        float vh = height > 0 ? height : 800f;
        float unit = unit();
        float top = 0f;
        for (int i = 0; i < sections.size(); i++) {
            if (id.equals(sectionIds.get(i))) return top;
            top += sections.get(i).height(w, vh, unit);
        }
        return -1f;
    }}
