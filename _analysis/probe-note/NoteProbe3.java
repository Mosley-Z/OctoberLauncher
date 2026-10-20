import com.inkread.weekread.kernel.load.KernelDataLoader;
import java.io.File;
import java.lang.reflect.Method;

public final class NoteProbe3 {
    public static void main(String[] args) throws Exception {
        KernelDataLoader loader = new KernelDataLoader(new File(args[0]));
        Method m = KernelDataLoader.class.getDeclaredMethod("readMarks", String.class);
        m.setAccessible(true);
        Object r = m.invoke(loader, "31808219");
        System.out.println("readMarks=" + (r == null ? "null" : ((org.json.JSONArray) r).length()));
        Method j = KernelDataLoader.class.getDeclaredMethod("readJsonObject", String.class);
        j.setAccessible(true);
        Object o = j.invoke(loader, "nt_index.json");
        System.out.println("readJsonObject=" + (o == null ? "null" : o.getClass()));
        Method a = KernelDataLoader.class.getDeclaredMethod("readJsonArray", String.class);
        a.setAccessible(true);
        System.out.println("readJsonArray=" + a.invoke(loader, "nt_index.json"));
    }
}
