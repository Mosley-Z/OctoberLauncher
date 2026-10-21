import com.inkread.weekread.kernel.NoteItem;
import com.inkread.weekread.kernel.desk.DeskData;
import com.inkread.weekread.kernel.desk.DeskHost;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Runs against real Android org.json via app_process; uses synthetic notes only. */
public final class KernelNotesCacheProbe {
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

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply an empty synthetic data directory");
        File root = new File(args[0]);
        if (root.exists()) throw new IllegalArgumentException("Probe directory must not exist");
        if (!root.mkdirs()) throw new IllegalStateException("Cannot create probe directory");
        write(root, "nt_index.json", "[{\"bookId\":\"synthetic\",\"title\":\"Synthetic\"}]");
        String marks = "{\"marks\":[{\"bookmarkId\":\"one\",\"markText\":\"first\",\"createTime\":1},"
                + "{\"bookmarkId\":\"two\",\"markText\":\"second\",\"createTime\":2}]}";
        write(root, "nt_synthetic.json", marks);
        DeskData data = new DeskData(null, root);
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
