import com.inkread.weekread.kernel.load.KernelDataLoader;
import java.io.File;

public final class NoteProbe2 {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        System.out.println("root exists=" + root.isDirectory() + " idx=" + new File(root, "nt_index.json").exists());
        KernelDataLoader loader = new KernelDataLoader(root);
        System.out.println("root()=" + loader.root());
        System.out.println("pref=" + loader.getPrefString("x"));
        System.out.println("notes0=" + loader.notes(0).size() + " notes1=" + loader.notes(1).size());
        System.out.println("shelf=" + loader.shelf().size());
    }
}
