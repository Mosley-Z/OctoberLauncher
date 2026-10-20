import com.inkread.weekread.kernel.NoteItem;
import com.inkread.weekread.kernel.load.KernelDataLoader;
import java.io.File;
import java.util.List;

public final class NoteProbe {
    public static void main(String[] args) {
        KernelDataLoader loader = new KernelDataLoader(new File(args[0]));
        List<NoteItem> notes = loader.notes(5);
        System.out.println("count=" + notes.size());
        for (NoteItem n : notes) {
            System.out.println("bookId=" + n.bookId + " title=[" + n.title + "] author=[" + n.author
                    + "] chapterTitle=[" + n.chapterTitle + "] mark=[" + (n.markText == null ? "" : n.markText.substring(0, Math.min(12, n.markText.length()))) + "]");
        }
    }
}
