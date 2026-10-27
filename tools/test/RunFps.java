import local.wwm.fontpatch.*;
import java.util.*;

/** FPS block: idempotent, independent from the font block, removable on its own. */
public class RunFps {
    static class Mem implements RowStore {
        final Map<String, byte[]> m = new LinkedHashMap<>();
        public List<String> hotfixIds() { List<String> l = new ArrayList<>(); for (String k : m.keySet()) if (k.startsWith("hotfix_")) l.add(k); return l; }
        public byte[] readRaw(String id) { byte[] b = m.get(id); return b == null ? new byte[0] : b; }
        public void writeRaw(String id, byte[] raw) { m.put(id, raw); }
    }
    static int count(String s, String needle) { int n = 0, i = 0; while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); } return n; }
    static void check(boolean ok, String msg) { if (!ok) throw new RuntimeException("FAIL: " + msg); }

    public static void main(String[] a) throws Exception {
        Mem s = new Mem();
        String base = "function f() return 1 end\n" + "local t = {}\n".repeat(12) + "-- other patch (kept)\nlocal keep = 1\n";
        HotfixEditor.writeLua(s, "hotfix_1003global", base);
        String id = "hotfix_1003global";

        HotfixEditor.installFps(s, PatchModel.buildFpsBlock(120), null);
        HotfixEditor.installFps(s, PatchModel.buildFpsBlock(120), null);        // twice: no duplicate
        String lua = HotfixEditor.readLua(s, id);
        check(count(lua, PatchModel.FPS_BEGIN) == 1 && count(lua, PatchModel.FPS_END) == 1, "fps idempotent");
        check(lua.contains("orig(120)") && lua.contains("EnableVsync(false)"), "fps body");
        check(lua.startsWith("function f()") && lua.contains("local keep = 1"), "original kept");

        HotfixEditor.install(s, PatchModel.buildBlock("com.netease.yysls", new String[]{"My Font", null, null}), null);
        lua = HotfixEditor.readLua(s, id);
        check(count(lua, PatchModel.FPS_BEGIN) == 1 && count(lua, PatchModel.BEGIN) == 1, "font install keeps fps");

        HotfixEditor.installFps(s, PatchModel.buildFpsBlock(60), null);         // change value
        lua = HotfixEditor.readLua(s, id);
        check(lua.contains("orig(60)") && !lua.contains("orig(120)") && count(lua, PatchModel.FPS_BEGIN) == 1, "fps replaced");
        check(count(lua, PatchModel.BEGIN) == 1, "fps install keeps font");

        check(HotfixEditor.uninstall(s) == 1, "font uninstall count");
        lua = HotfixEditor.readLua(s, id);
        check(!PatchModel.hasBlock(lua) && PatchModel.hasFpsBlock(lua), "font gone, fps stays");

        check(HotfixEditor.uninstallFps(s) == 1, "fps uninstall count");
        lua = HotfixEditor.readLua(s, id);
        check(!PatchModel.hasFpsBlock(lua) && lua.contains("local keep = 1"), "fps gone, original stays");
        check(HotfixEditor.uninstallFps(s) == 0, "nothing left");
        System.out.println("fps tests ok");
    }
}
