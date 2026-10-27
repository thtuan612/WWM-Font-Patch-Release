import local.wwm.fontpatch.*;
import java.nio.file.*;
import java.util.*;

/** usage: RunPatch in.tsv out.tsv pkg font1 font2 font3 [forcedId]   (tsv: id<TAB>hex) */
public class RunPatch {
    static class Mem implements RowStore {
        final Map<String, byte[]> m = new LinkedHashMap<>();
        public List<String> hotfixIds() { List<String> l = new ArrayList<>(); for (String k : m.keySet()) if (k.startsWith("hotfix_")) l.add(k); return l; }
        public byte[] readRaw(String id) { byte[] b = m.get(id); return b == null ? new byte[0] : b; }
        public void writeRaw(String id, byte[] raw) { m.put(id, raw); }
    }
    static byte[] unhex(String h) { byte[] b = new byte[h.length() / 2]; for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16); return b; }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    public static void main(String[] a) throws Exception {
        Mem s = new Mem();
        for (String line : Files.readAllLines(Paths.get(a[0]))) { String[] p = line.split("\t"); s.m.put(p[0], p.length > 1 ? unhex(p[1]) : new byte[0]); }
        String pkg = a[2];
        String[] fonts = {a[3].equals("-") ? null : a[3], a[4].equals("-") ? null : a[4], a[5].equals("-") ? null : a[5]};
        String forced = a.length > 6 ? a[6] : null;
        System.out.println("candidates=" + HotfixEditor.candidates(s));
        String block = PatchModel.buildBlock(pkg, fonts);
        System.out.println("target=" + HotfixEditor.install(s, block, forced));
        // idempotency: second install must not duplicate the block
        HotfixEditor.install(s, block, forced);
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, byte[]> e : s.m.entrySet()) out.append(e.getKey()).append('\t').append(hex(e.getValue())).append('\n');
        Files.write(Paths.get(a[1]), out.toString().getBytes());
        System.out.println("uninstalled rows=" + HotfixEditor.uninstall(s));
        for (String id : s.hotfixIds()) if (!id.equals(HotfixEditor.META) && PatchModel.hasBlock(HotfixEditor.readLua(s, id))) throw new RuntimeException("block left in " + id);
        System.out.println("uninstall ok");
    }
}
