import com.inkread.weekread.kernel.NoteItem;
import com.inkread.weekread.kernel.desk.DeskData;
import com.inkread.weekread.kernel.desk.DeskHost;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Runs against real Android org.json via app_process; uses synthetic notes only. */
public final class KernelNotesCacheProbe {
    public static android.graphics.Picture lastBillPicture;
    private static final class HiddenHost implements DeskHost {
        private final String hidden;
        HiddenHost(String hidden) { this.hidden = hidden; }
        public int deskModuleMask() { return 0; }
        public String[] deskOrder() { return new String[0]; }
        public String menuPrefs(String group, String key, String fallback) {
            return "menu_hidden".equals(group) && "ids".equals(key) ? hidden : fallback;
        }
        public boolean menuBool(String key, boolean fallback) { return fallback; }
        public int menuInt(String key, int fallback) { return fallback; }
        public int fpFontPx() { return 48; }
        public int styleColor(String token, int fallback) { return fallback; }
    }
    private static void write(File root, String name, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(root, name))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Object field(Object value, String name) throws Exception {
        java.lang.reflect.Field f = value.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(value);
    }
    private static void set(Object value, String name, Object item) throws Exception {
        java.lang.reflect.Field f = value.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(value, item);
    }
    private static void verifyDeskInteractions(File root) throws Exception {
        android.graphics.Typeface font = android.graphics.Typeface.DEFAULT;
        DeskHost host = new DeskHost() {
            public int deskModuleMask() { return 1; }
            public String[] deskOrder() { return new String[]{"bill"}; }
            public File filesDir() { return root; }
            public float scale() { return 1.2f; }
            public String menuPrefs(String group, String key, String fallback) { return fallback; }
            public boolean menuBool(String key, boolean fallback) { return fallback; }
            public int menuInt(String key, int fallback) { return fallback; }
            public int fpFontPx() { return 16; }
            public int styleColor(String token, int fallback) { return fallback; }
        };
        com.inkread.weekread.kernel.desk.DeskPage page = new com.inkread.weekread.kernel.desk.DeskPage(host, root);
        page.bind();
        Object section = field(page, "billSection");
        com.inkread.weekread.kernel.model.BillSheet bill = new com.inkread.weekread.kernel.model.BillSheet();
        bill.mode = "weekly";
        bill.periodStart = 1792368000L;
        bill.items = new java.util.ArrayList<com.inkread.weekread.kernel.BillItem>();
        com.inkread.weekread.kernel.BillItem item = new com.inkread.weekread.kernel.BillItem();
        item.bookId = "synthetic"; item.title = "Synthetic"; item.author = "Test";
        item.readTimeSec = 3600; item.marketPriceFen = 1000;
        bill.items.add(item);
        set(section, "bill", bill);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(480, 800, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        page.onAction(com.inkread.weekread.kernel.desk.DeskActions.BILL_TAB);
        Object view = field(section, "view");
        page.render(canvas, 480, 800, 0);
        check(view.equals(field(section, "view")), "render must preserve menu selection");
        check(field(section, "bill") == bill, "render must preserve selected period");
        set(section, "algoOpen", true);
        page.render(canvas, 480, 800, 0);
        check(Boolean.TRUE.equals(field(section, "algoOpen")), "render must preserve algorithm popup");
        java.lang.reflect.Method strip = section.getClass().getDeclaredMethod("algoDropItem", float.class, float.class, float.class, int.class);
        strip.setAccessible(true);
        System.out.println("CHECK popup geometry");
        float top = new com.inkread.weekread.kernel.desk.DeskRenderer(host).headerHeight(1.2f);
        android.graphics.RectF option = (android.graphics.RectF) strip.invoke(section, 480f, 1.2f, top, 1);
        System.out.println("CHECK hit");
        int action = page.hitTest(option.centerX(), option.centerY());
        System.out.println("ACTION value " + action + " top " + top + " rect " + option);
        check(action == com.inkread.weekread.kernel.desk.DeskActions.ALGO_BASE + 1, "popup hit selects cumulative");
        System.out.println("HIT " + action);
        page.onAction(action);
        System.out.println("ACTION done");
        check(page.data().paidAlgo().equals(com.inkread.weekread.kernel.desk.DeskPrefs.paidAlgoAt(1)), "algorithm consumer updated");
        page.render(canvas, 480, 800, 0);
        check(!Boolean.TRUE.equals(field(section, "algoOpen")), "choosing algorithm closes popup");
        com.inkread.weekread.kernel.desk.PricePad pad = (com.inkread.weekread.kernel.desk.PricePad) field(section, "pad");
        pad.open("synthetic", "Synthetic", 1000);
        page.render(canvas, 480, 800, 0);
        check(page.padOpen(), "render must preserve price pad");
        page.padPress(com.inkread.weekread.kernel.desk.PricePad.PK_CANCEL);
        page.render(canvas, 480, 800, 0);
        check(!page.padOpen(), "price pad cancellation");
        verifyFullBillPicture(page, section, bill, pad, top);
        bitmap.recycle();
        page.release();
        System.out.println("PASS desk interaction render regression: 8 assertions");
    }

    private static void verifyFullBillPicture(
            com.inkread.weekread.kernel.desk.DeskPage page, Object section,
            com.inkread.weekread.kernel.model.BillSheet bill,
            com.inkread.weekread.kernel.desk.PricePad pad, float top) throws Exception {
        bill.items.clear();
        for (int i = 0; i < 500; i++) {
            com.inkread.weekread.kernel.BillItem row = new com.inkread.weekread.kernel.BillItem();
            row.bookId = "picture-" + i; row.title = "Synthetic row " + i; row.author = "Test";
            row.readTimeSec = 3600; row.marketPriceFen = 1000;
            bill.items.add(row);
        }
        set(section, "view", com.inkread.weekread.kernel.desk.DeskPrefs.VIEW_READING);
        java.lang.reflect.Method invalidate = section.getClass().getDeclaredMethod("invalidateRows");
        invalidate.setAccessible(true);
        invalidate.invoke(section);
        int total = page.height(480);
        int body = (int) Math.ceil(total - top);
        check(body > 8192, "long bill exceeds old bitmap limit");
        android.graphics.Picture picture = new android.graphics.Picture();
        android.graphics.Canvas recording = picture.beginRecording(480, body);
        try {
            recording.translate(0, -top);
            page.render(recording, 480, total, 0);
        } finally { picture.endRecording(); }
        lastBillPicture = picture;
        check(picture.getHeight() == body, "picture retains full bill height");
        android.graphics.Bitmap tail = android.graphics.Bitmap.createBitmap(480, 800, android.graphics.Bitmap.Config.ARGB_8888);
        tail.eraseColor(android.graphics.Color.WHITE);
        android.graphics.Canvas tailCanvas = new android.graphics.Canvas(tail);
        tailCanvas.translate(0, -(body - 800));
        tailCanvas.drawPicture(picture);
        int ink = 0;
        for (int y = 0; y < 800; y++) for (int x = 0; x < 480; x++) {
            if (tail.getPixel(x, y) != android.graphics.Color.WHITE) ink++;
        }
        check(ink > 100, "picture plays bill beyond 8192px");
        tail.recycle();
        pad.open("picture-0", "Synthetic", 1000);
        int padTotal = page.height(480);
        android.graphics.RectF bounds = pad.panel(480f, padTotal, 1.2f);
        android.graphics.Picture withPad = new android.graphics.Picture();
        android.graphics.Canvas padCanvas = withPad.beginRecording(480, (int) Math.ceil(padTotal - top));
        try {
            padCanvas.translate(0, -top);
            page.render(padCanvas, 480, padTotal, 0);
        } finally { withPad.endRecording(); }
        check(page.padOpen(), "picture preserves open pad");
        check(Math.abs(bounds.height() - pad.panelHeight(1.2f)) < 1f, "actual panel geometry");
        boolean cancelFound = false;
        for (int y = (int) bounds.top; y <= (int) bounds.bottom; y += 2) {
            for (int x = (int) bounds.left; x <= (int) bounds.right; x += 2) {
                if (page.hitPad(x, y) == com.inkread.weekread.kernel.desk.PricePad.PK_CANCEL) cancelFound = true;
            }
        }
        check(cancelFound, "full-height pad cancellation hit");
        page.padPress(com.inkread.weekread.kernel.desk.PricePad.PK_CANCEL);
        check(!page.padOpen(), "long bill pad cancels");
        System.out.println("PASS full-height Picture and pad geometry: height=" + body + " tailInk=" + ink);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply an empty synthetic data directory");
        File root = new File(args[0]);
        if (root.exists()) throw new IllegalArgumentException("Probe directory must not exist");
        if (!root.mkdirs()) throw new IllegalStateException("Cannot create probe directory");
        write(root, "nt_index.json", "[{\"bookId\":\"synthetic\",\"title\":\"Synthetic\"}]");
        String marks = "{\"marks\":[{\"bookmarkId\":\"one\",\"markText\":\"first\",\"createTime\":1},"
                + "{\"bookmarkId\":\"two\",\"markText\":\"second\",\"createTime\":2}]}";
        write(root, "nt_synthetic.json", marks);
        System.out.println("START interactions");
        verifyDeskInteractions(root);
        DeskData data = new DeskData(null, root);
        write(root, "nt_index.json", "{\"books\":[{\"bookId\":\"synthetic\",\"centPrice\":1250},{\"bookId\":\"free\",\"free\":1},{\"bookId\":\"unknown\",\"centPrice\":0}]}");
        check(data.billFenOf("synthetic") == 1250, "notebook price reaches asset ledger");
        check(data.manualFen("synthetic") == -1, "automatic price must not become manual override");
        check(data.billFree("free"), "explicit notebook free flag");
        check(data.billFenOf("unknown") == -1 && !data.billFree("unknown"), "zero without free remains unknown");
        System.out.println("PASS notebook prices: 4 assertions");
        List<NoteItem> limited = data.notes(1);
        check(limited.size() == 1, "positive limit");
        limited.clear();
        check(data.notes(0).size() == 2, "caller list mutation must not alter cache");
        check(data.notes(-1).size() == 2, "negative limit");
        check(data.notes(10).size() == 2, "large limit");
        String key = data.notesKey();
        check("second".equals(data.excerptOf("synthetic")), "latest excerpt");
        check(data.markTexts("synthetic").size() == 2, "fingerprint marks");
        write(root, "nt_synthetic.json", "{\"marks\":[{\"bookmarkId\":\"three\",\"markText\":\"replacement\",\"createTime\":3}]}");
        check(data.notes(0).size() == 2, "page cache should survive file edit until invalidateAll");
        check("second".equals(data.excerptOf("synthetic")), "excerpt must share cache");
        check(data.markTexts("synthetic").size() == 2, "marks must share cache");
        check(key.equals(data.notesKey()), "version key must share cache");
        data.invalidateAll();
        check(data.notes(0).size() == 1, "invalidateAll must reload notes");
        check("replacement".equals(data.excerptOf("synthetic")), "excerpt refresh");
        check(data.markTexts("synthetic").size() == 1, "marks refresh");
        check(!key.equals(data.notesKey()), "version refresh");
        check(new DeskData(null, root).notes(0).size() == 1, "fresh page must see current data");
        DeskData noHost = new DeskData(null, root);
        check(noHost.hideBook("new"), "first hide without host");
        check(noHost.hiddenIds().contains("new"), "null host must retain in-memory hidden item");
        DeskData emptyHost = new DeskData(new HiddenHost(""), root);
        check(emptyHost.hideBook("new"), "first hide with empty persisted list");
        check(emptyHost.hiddenIds().contains("new"), "empty host must retain in-memory hidden item");
        String persisted = emptyHost.hiddenIds().iterator().next();
        check(new DeskData(new HiddenHost(persisted), root).hiddenIds().contains("new"), "first hidden snapshot survives recreation");
        DeskData existingHost = new DeskData(new HiddenHost("old, older"), root);
        existingHost.hideBook("new");
        check(existingHost.hiddenIds().size() == 3 && existingHost.hiddenIds().contains("new"), "persisted and new hidden items merge");
        check(existingHost.hiddenIds().contains("old") && existingHost.hiddenIds().contains("older"), "persisted hidden items retained");
        System.out.println("PASS: 22 real-kernel notes-cache/hidden assertions; synthetic data only");
    }
}
