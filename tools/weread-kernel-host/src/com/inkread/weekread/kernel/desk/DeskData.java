package com.inkread.weekread.kernel.desk;

import com.inkread.weekread.kernel.AssetItem;
import com.inkread.weekread.kernel.BillItem;
import com.inkread.weekread.kernel.BillMath;
import com.inkread.weekread.kernel.NoteItem;
import com.inkread.weekread.kernel.PeriodCalendar;
import com.inkread.weekread.kernel.PeriodSnapshot;
import com.inkread.weekread.kernel.load.KernelDataLoader;
import com.inkread.weekread.kernel.model.BillSheet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 墨台数据装配。把原盘数据读取结果与宿主的菜单配置组装成分区可直接消费的视图。
 *
 * <p>本类持有渲染期的瞬时选择（当前账单期、墨单视图、收入算法、手填价），
 * 不落盘；持久化由宿主在动作回调中完成。
 */
public final class DeskData {

    /** 宿主能力。 */
    private final DeskHost host;
    /** 原盘读取器。 */
    private final KernelDataLoader loader;
    private final File loaderRoot;

    /** 当前选中的周期类型，空串表示未选择。 */
    private String periodMode = "";
    /** 当前选中的周期起点，0 表示未选择。 */
    private long periodStart;
    /** 墨单视图。 */
    private int view = DeskPrefs.VIEW_EXCERPT;
    /** 收入算法。 */
    private String paidAlgo = DeskPrefs.PAID_PERIOD;
    /** 指纹字号覆盖，0 表示沿用宿主设置。 */
    private int fpFontPx;

    /** 手填书价覆盖，键为书籍标识，值为分。 */
    private final Map<String, Integer> prices = new HashMap<String, Integer>();

    /** Host1：页面级笔记缓存，统一失效于 invalidateAll。 */
    private List<NoteItem> notesCache;

    /** 账单缓存。 */
    private BillSheet bill;
    /** 账单缓存的形态与起点，用于判断缓存是否仍然有效。 */
    private String billMode = "";
    private long billStart;

    /**
     * 构造装配器。
     *
     * @param host 宿主能力
     * @param root 数据根目录
     */
    public DeskData(DeskHost host, File root) {
        this.host = host;
        this.loaderRoot = root;
        this.loader = new KernelDataLoader(root);
        this.view = DeskPrefs.view(host);
        this.paidAlgo = DeskPrefs.paidAlgo(host);
        this.periodMode = DeskPrefs.periodMode(host);
        this.periodStart = DeskPrefs.periodStart(host);
    }

    /** 宿主能力。 */
    public DeskHost host() {
        return host;
    }

    /** 原盘读取器。 */
    public KernelDataLoader loader() {
        return loader;
    }

    /** 墨单视图。 */
    public int view() {
        return view;
    }

    /**
     * 设置墨单视图。
     *
     * @param v {@link DeskPrefs#VIEW_EXCERPT} 或 {@link DeskPrefs#VIEW_READING}
     */
    public void setView(int v) {
        this.view = v;
    }

    /** 收入算法。 */
    public String paidAlgo() {
        return paidAlgo;
    }

    /**
     * 设置收入算法。
     *
     * @param algo 算法取值
     */
    public void setPaidAlgo(String algo) {
        this.paidAlgo = algo == null ? DeskPrefs.PAID_PERIOD : algo;
    }

    /**
     * 墨单选定的周期。
     *
     * @param mode  周期类型
     * @param start 周期起点（秒）
     */
    public void selectPeriod(String mode, long start) {
        this.periodMode = mode == null ? "" : mode;
        this.periodStart = start;
        this.bill = null;
    }

    // ══════════════════════ 账单 ══════════════════════

    /** 当前账单，无可选期时返回空快照而非 null。 */
    public BillSheet bill() {
        if (bill != null) return bill;
        BillSheet b = null;
        if (periodMode.length() > 0 && periodStart > 0L) {
            b = loader.billSheet(periodMode, periodStart);
        }
        if (b == null) b = latest();
        if (b == null) {
            b = new BillSheet();
            b.mode = DeskPrefs.PAID_PERIOD;
            b.placeholder = true;
        }
        bill = b;
        billMode = b.mode;
        billStart = b.periodStart;
        return b;
    }

    /**
     * 某周期类型下已生成账单的期起点，按时间降序。
     *
     * @param mode 周期类型
     * @return 期起点列表
     */
    public List<Long> startsOf(String mode) {
        return loader.billStarts(mode);
    }

    /** 最新一期账单。 */
    public BillSheet latest() {
        BillSheet b = newest(PeriodCalendar.WEEKLY);
        if (b != null) return b;
        return newest(PeriodCalendar.MONTHLY);
    }

    private BillSheet newest(String mode) {
        List<Long> starts = loader.billStarts(mode);
        if (starts.isEmpty()) return null;
        return loader.billSheet(mode, starts.get(0));
    }

    /**
     * 读取指定期的账单。
     *
     * @param mode  周期类型
     * @param start 周期起点（秒）
     * @return 账单快照；缺失返回 null
     */
    public BillSheet load(String mode, long start) {
        return loader.billSheet(mode, start);
    }

    /** 丢弃账单缓存。 */
    public void invalidateBill() {
        bill = null;
    }

    // ══════════════════════ 书价与金额 ══════════════════════

    /**
     * 书价，手填价优先于账单里的市场价。
     *
     * @param bookId 书籍标识
     * @return 价格（分）；未知返回 {@link BillMath#UNKNOWN}
     */
    public int priceFen(String bookId) {
        if (bookId == null) return BillMath.UNKNOWN;
        Integer v = prices.get(bookId);
        if (v != null) return v.intValue();
        return loader.priceFen(bookId);
    }

    /**
     * 记录手填价。
     *
     * @param bookId 书籍标识
     * @param fen    价格（分），负数表示清除
     */
    public void setPrice(String bookId, int fen) {
        if (bookId == null) return;
        if (fen < 0) prices.remove(bookId);
        else prices.put(bookId, Integer.valueOf(fen));
    }

    /**
     * 条目有效价：手填价优先，其次账单市场价。
     *
     * @param it 账单条目
     * @return 价格（分）
     */
    public int effFen(BillItem it) {
        if (it == null) return BillMath.UNKNOWN;
        int manual = priceFen(it.bookId);
        if (manual >= 0) return manual;
        return it.marketPriceFen;
    }

    /**
     * 条目原价文本。
     *
     * @param it 账单条目
     * @return 金额文本，未知返回横线
     */
    public String priceText(BillItem it) {
        int fen = effFen(it);
        if (fen == BillMath.UNKNOWN) return "—";
        if (fen == BillMath.FREE) return "免费";
        return yuan(fen);
    }

    /**
     * 条目收入文本，估算时带约等号前缀。
     *
     * @param it   账单条目
     * @param algo 收入算法
     * @return 金额文本
     */
    public String paidText(BillItem it, String algo) {
        int fen = paidFen(it, algo);
        if (fen == BillMath.UNKNOWN) return "—";
        if (fen == BillMath.FREE) return "免费";
        boolean est = estOf(it, algo);
        return (est ? "≈" : "") + yuan(fen);
    }

    private boolean estOf(BillItem it, String algo) {
        if (it == null) return false;
        if (DeskPrefs.PAID_CUMULATIVE.equals(algo)) return it.progressEndEst;
        return it.progressEndEst || it.progressStartEst;
    }

    /**
     * 单条收入。
     *
     * @param it   账单条目
     * @param algo 收入算法
     * @return 金额（分）
     */
    public int paidFen(BillItem it, String algo) {
        if (it == null || algo == null || DeskPrefs.PAID_OFF.equals(algo)) return BillMath.UNKNOWN;
        int price = effFen(it);
        return BillMath.paidFen(price,
                DeskPrefs.PAID_CUMULATIVE.equals(algo) ? BillMath.ALGO_CUMULATIVE : BillMath.ALGO_PERIOD,
                it.progressPct, it.progressEndPct, it.progressEndKnown,
                it.progressEndEst, it.progressStartPct);
    }

    /**
     * 合计收入。
     *
     * @param items 账单条目
     * @param algo  收入算法
     * @return 金额（分）
     */
    public long totalPaidFen(List<BillItem> items, String algo) {
        if (items == null) return 0L;
        long sum = 0L;
        for (int i = 0; i < items.size(); i++) {
            int v = paidFen(items.get(i), algo);
            if (v > 0) sum += v;
        }
        return sum;
    }

    /**
     * 可计算收入的条目数。
     *
     * @param items 账单条目
     * @param algo  收入算法
     * @return 条目数
     */
    public int paidCount(List<BillItem> items, String algo) {
        if (items == null) return 0;
        int n = 0;
        for (int i = 0; i < items.size(); i++) {
            if (paidFen(items.get(i), algo) > 0) n++;
        }
        return n;
    }

    /**
     * 合计中是否含估算项。
     *
     * @param items 账单条目
     * @param algo  收入算法
     * @return 含估算项返回 true
     */
    public boolean totalEstimated(List<BillItem> items, String algo) {
        if (items == null) return false;
        for (int i = 0; i < items.size(); i++) {
            BillItem it = items.get(i);
            if (paidFen(it, algo) > 0 && estOf(it, algo)) return true;
        }
        return false;
    }

    /**
     * 金额文本。
     *
     * @param fen 金额（分）
     * @return 文本，未知返回横线
     */
    public String yuan(int fen) {
        return BillMath.yuan(fen);
    }

    /**
     * 金额文本。
     *
     * @param fen 金额（分）
     * @return 文本，未知返回横线
     */
    public String yuan(long fen) {
        return BillMath.yuan(fen);
    }

    /**
     * 时长文本。
     *
     * @param sec 秒数
     * @return 文本
     */
    public String hoursText(int sec) {
        return BillMath.hoursText(sec);
    }

    // ══════════════════════ 阅读资产 ══════════════════════

    /** 阅读资产条目，按当前排序与折叠设置读取。 */
    public List<AssetItem> assets() {
        List<AssetItem> list = loader.assets(1, false);
        List<AssetItem> out = new ArrayList<AssetItem>();
        Set<String> hidden = hiddenIds();
        for (int i = 0; i < list.size(); i++) {
            AssetItem a = list.get(i);
            if (hidden.contains(a.bookId)) continue;
            out.add(a);
        }
        return out;
    }

    /** 渲染期新增的隐藏书目，未落盘。 */
    private final Set<String> hiddenExtra = new HashSet<String>();

    /**
     * 隐藏一本书目，记录在渲染期集合内，由宿主在回调中持久化。
     *
     * @param bookId 书籍标识
     * @return 是否新加入
     */
    public boolean hideBook(String bookId) {
        return bookId != null && hiddenExtra.add(bookId);
    }

    /** 被隐藏的书目标识集合。 */
    public Set<String> hiddenIds() {
        Set<String> out = new HashSet<String>();
        String raw = host == null ? null : host.menuPrefs("menu_hidden", "ids", "");
        // Host1：首次移出时宿主尚无持久列表，也必须合并本页刚新增的条目。
        if (raw != null && raw.length() > 0) {
            String[] parts = raw.split(",");
            for (int i = 0; i < parts.length; i++) {
                String v = parts[i].trim();
                if (v.length() > 0) out.add(v);
            }
        }
        out.addAll(hiddenExtra);
        return out;
    }

    /** 书架标识集合。 */
    public Set<String> shelfIds() {
        return loader.shelf().ids;
    }

    // ══════════════════════ 摘录与指纹 ══════════════════════

    /**
     * 摘录条目。
     *
     * @param limit 条数上限，小于等于 0 表示不限
     * @return 条目列表
     */
    public List<NoteItem> notes(int limit) {
        if (notesCache == null) notesCache = loader.notes(0);
        int count = limit <= 0 ? notesCache.size() : Math.min(limit, notesCache.size());
        return new ArrayList<NoteItem>(notesCache.subList(0, count));
    }

    /** 顶层周期统计，用于资产页原价映射。 */
    public PeriodSnapshot stats() {
        return loader.period(PeriodCalendar.MONTHLY);
    }

    // ══════════════════════ 阅读资产的价格链与摘录 ══════════════════════

    /** 资产价格链索引：账单落盘市场价、统计落盘原价，各带免费标记。 */
    private final java.util.Map<String, Integer> billFen = new java.util.HashMap<String, Integer>();
    private final java.util.Set<String> billFree = new java.util.HashSet<String>();
    private final java.util.Map<String, Integer> statFen = new java.util.HashMap<String, Integer>();
    private final java.util.Set<String> statFree = new java.util.HashSet<String>();
    /** 价格链索引是否已建立。 */
    private boolean ledgerReady;

    /**
     * 建立价格链索引。遍历全部账单期与各形态统计，取每本书首次出现的价格。
     */
    private void ensureLedger() {
        if (ledgerReady) return;
        ledgerReady = true;
        String[] modes = {PeriodCalendar.WEEKLY, PeriodCalendar.MONTHLY};
        for (int m = 0; m < modes.length; m++) {
            List<Long> starts = loader.billStarts(modes[m]);
            for (int i = 0; i < starts.size(); i++) {
                BillSheet sheet = loader.billSheet(modes[m], starts.get(i).longValue());
                if (sheet != null && sheet.items != null) {
                    for (int k = 0; k < sheet.items.size(); k++) {
                        BillItem it = sheet.items.get(k);
                        if (it == null || it.bookId == null || it.bookId.length() == 0) continue;
                        if (it.marketPriceFen > 0) {
                            if (!billFen.containsKey(it.bookId)) billFen.put(it.bookId, Integer.valueOf(it.marketPriceFen));
                        } else if (it.marketPriceFen == 0) {
                            billFree.add(it.bookId);
                        }
                    }
                }
            }
        }
        String[] statModes = {PeriodCalendar.WEEKLY, PeriodCalendar.MONTHLY,
                PeriodCalendar.ANNUALLY, PeriodCalendar.OVERALL};
        for (int m = 0; m < statModes.length; m++) {
            PeriodSnapshot ps = loader.period(statModes[m]);
            if (ps == null || ps.longest == null) continue;
            for (int k = 0; k < ps.longest.size(); k++) {
                PeriodSnapshot.Longest lg = ps.longest.get(k);
                if (lg == null || lg.bookId == null || lg.bookId.length() == 0) continue;
                int f = BillMath.fenOf(lg.centPrice, lg.priceYuan, lg.origYuan, lg.free);
                if (f > 0) {
                    if (!statFen.containsKey(lg.bookId)) statFen.put(lg.bookId, Integer.valueOf(f));
                } else if (f == 0) {
                    statFree.add(lg.bookId);
                }
            }
        }
    }

    /** 手填书价（分），未填返回 -1。 */
    public int manualFen(String bookId) {
        if (bookId == null) return -1;
        if (prices.containsKey(bookId)) return prices.get(bookId).intValue();
        String raw = host == null ? null : host.menuPrefs("menu_prices", bookId, "");
        if (raw == null || raw.length() == 0) return -1;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Cached notebook metadata is a price source, never a manual-price override. */
    private int notePriceFen(String bookId) {
        ensureNotePrices();
        Integer value = notePrices.get(bookId);
        return value == null ? -1 : value.intValue();
    }

    private final Map<String, Integer> notePrices = new HashMap<String, Integer>();
    private boolean notePricesReady;

    private void ensureNotePrices() {
        if (notePricesReady) return;
        notePricesReady = true;
        File file = new File(host == null ? loaderRoot : host.filesDir(), "nt_index.json");
        try {
            String raw = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            Object parsed = new org.json.JSONTokener(raw).nextValue();
            org.json.JSONArray books = parsed instanceof org.json.JSONArray ? (org.json.JSONArray) parsed
                    : ((org.json.JSONObject) parsed).optJSONArray("books");
            if (books == null) return;
            for (int i = 0; i < books.length(); i++) {
                org.json.JSONObject book = books.optJSONObject(i);
                if (book == null) continue;
                String id = book.optString("bookId", "");
                org.json.JSONObject metadata = book.optJSONObject("book");
                if (metadata == null) metadata = book;
                int fen = BillMath.fenOf(metadata.optInt("centPrice", 0),
                        metadata.optDouble("price", metadata.optDouble("priceY", 0)),
                        metadata.optInt("originalPrice", metadata.optInt("origY", 0)),
                        metadata.optInt("free", 0) == 1);
                if (!id.isEmpty() && fen >= 0) notePrices.put(id, Integer.valueOf(fen));
            }
        } catch (Exception ignored) {
            // Missing or incomplete metadata leaves price unknown.
        }
    }

    /** 账单落盘的市场价（分），无记录返回 -1。 */
    public int billFenOf(String bookId) {
        int note = notePriceFen(bookId);
        if (note > 0) return note;
        if (note == 0) return -1;
        ensureLedger();
        Integer v = billFen.get(bookId);
        return v == null ? -1 : v.intValue();
    }

    /** 账单是否把该书目标记为免费。 */
    public boolean billFree(String bookId) {
        int note = notePriceFen(bookId);
        if (note >= 0) return note == 0;
        ensureLedger();
        return billFree.contains(bookId);
    }

    /** 统计落盘的原价（分），无记录返回 -1。 */
    public int statFenOf(String bookId) {
        ensureLedger();
        Integer v = statFen.get(bookId);
        return v == null ? -1 : v.intValue();
    }

    /** 统计是否把该书目标记为免费。 */
    public boolean statFree(String bookId) {
        ensureLedger();
        return statFree.contains(bookId);
    }

    /** 阅读资产是否只统计书架上的书目。 */
    public boolean assetShelfOnly() {
        return host != null && host.menuBool("asset_shelf_only", false);
    }

    /** 阅读资产是否只统计读完的书目。 */
    public boolean assetFinishedOnly() {
        return host == null || host.menuBool("asset_finished_only", true);
    }

    /**
     * 资产页摘录文本：手填摘录优先，其次已同步划线的最新原文，再次想法的摘要或正文。
     *
     * @param bookId 书籍标识
     * @return 摘录文本，无则返回 null
     */
    public String excerptOf(String bookId) {
        if (bookId == null) return null;
        String manual = host == null ? null : host.menuPrefs("menu_notes", bookId, "");
        if (manual != null && manual.trim().length() > 0) return manual.trim();
        List<NoteItem> all = notes(0);
        String bestMark = null;
        long markTime = Long.MIN_VALUE;
        String bestIdea = null;
        long ideaTime = Long.MIN_VALUE;
        for (int i = 0; i < all.size(); i++) {
            NoteItem n = all.get(i);
            if (n == null || !bookId.equals(n.bookId)) continue;
            if (n.markText != null && n.markText.trim().length() > 0 && n.createTime >= markTime) {
                markTime = n.createTime;
                bestMark = n.markText.trim();
            }
            if (n.ideaText != null && n.ideaText.trim().length() > 0 && n.createTime >= ideaTime) {
                ideaTime = n.createTime;
                bestIdea = n.ideaText.trim();
            }
        }
        return bestMark != null ? bestMark : bestIdea;
    }

    /** 重新读取原盘数据并作废价格链索引。 */
    public void invalidateAll() {
        notesCache = null;
        notePricesReady = false;
        notePrices.clear();
        ledgerReady = false;
        billFen.clear();
        billFree.clear();
        statFen.clear();
        statFree.clear();
        loader.reload();
        invalidateBill();
    }

    // ══════════════════════ 阅读指纹 ══════════════════════

    /** 宿主或内核本次选定的指纹书目；为 null 时读宿主配置。 */
    private List<String> fpOverride;

    /**
     * 替换本次选定的指纹书目。
     *
     * @param bookId 书籍标识；null 或空串表示清除覆盖、回到宿主配置
     */
    public void setFpBook(String bookId) {
        if (bookId == null || bookId.length() == 0) {
            fpOverride = null;
            return;
        }
        List<String> one = new ArrayList<String>();
        one.add(bookId);
        fpOverride = one;
    }

    /** 指纹选中的书目标识，按选择顺序。 */
    public List<String> fpBooks() {
        if (fpOverride != null) return new ArrayList<String>(fpOverride);
        List<String> out = new ArrayList<String>();
        String raw = host == null ? null : host.menuPrefs("cfg", "fp_books", "");
        if (raw == null || raw.length() == 0) return out;
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            String v = parts[i].trim();
            if (v.length() > 0) out.add(v);
        }
        return out;
    }

    /** 指纹字号，单位 px。 */
    public int fpFontPx() {
        if (fpFontPx > 0) return fpFontPx;
        return host == null ? DeskPrefs.FP_FONT_DEFAULT : host.fpFontPx();
    }

    /**
     * 设置指纹字号，仅本次渲染有效。
     *
     * @param px 字号
     */
    public void setFpFontPx(int px) {
        fpFontPx = px;
    }

    /**
     * 某本书的划线原文，按文件中的先后顺序。
     *
     * @param bookId 书籍标识
     * @return 原文列表，无则空表
     */
    public List<String> markTexts(String bookId) {
        List<String> out = new ArrayList<String>();
        List<NoteItem> all = notes(0);
        for (int i = 0; i < all.size(); i++) {
            NoteItem n = all.get(i);
            if (n == null || !bookId.equals(n.bookId)) continue;
            if (n.markText == null || n.markText.length() == 0) continue;
            out.add(n.markText);
        }
        return out;
    }

    /** 划线数据版本键，用于判断指纹是否需要重建。 */
    public String notesKey() {
        List<NoteItem> all = notes(0);
        int marks = 0;
        long sum = 0L;
        for (int i = 0; i < all.size(); i++) {
            NoteItem n = all.get(i);
            if (n == null || n.markText == null || n.markText.length() == 0) continue;
            marks++;
            sum += n.bookmarkId == null ? 0L : n.bookmarkId.hashCode();
        }
        return marks + ":" + sum;
    }
}



