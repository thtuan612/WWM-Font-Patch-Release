package local.wwm.fontpatch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure Java. Which game font names exist per slot, and the Lua block we append to a hotfix. */
public final class PatchModel {
    public static final String BEGIN = "-- [FONTPATCH:BEGIN]";
    public static final String END = "-- [FONTPATCH:END]";
    public static final String FPS_BEGIN = "-- [FPSPATCH:BEGIN]";
    public static final String FPS_END = "-- [FPSPATCH:END]";
    /** Mức FPS cho chọn trong app. */
    public static final int[] FPS_CHOICES = {60, 90, 120};

    private static final String[] GLOBAL = {"AlegreyaSans-Regular.ttf", "AlegreyaSans-Medium.ttf", "MrsEavSmaCap.ttf"};
    private static final String[] CHINESE = {"FZSKBXKJW.TTF", "AaShiSongTi-2.ttf", "ZHJB-Xiangjiahong.TTF"};

    private PatchModel() {}

    /** Same rule as the original app: any package containing "yyslscn" uses the Chinese font names. */
    public static boolean isChinese(String pkg) {
        return pkg != null && pkg.contains("yyslscn");
    }

    public static String[] slotNames(String pkg) {
        return isChinese(pkg) ? CHINESE : GLOBAL;
    }

    /** All in-game font names that a slot overrides (slot 0 and 2 also cover extra aliases, as in the original). */
    public static List<String> slotTargets(String pkg, int slot) {
        List<String> l = new ArrayList<>();
        l.add(slotNames(pkg)[slot]);
        if (slot == 0) {
            l.add("FZShengSKSJW_Cu.TTF");
            l.add("fzht.ttf");
            l.add("FZZJ-LongYTJW.TTF");
        } else if (slot == 2) {
            l.add("ChillLongCangKaiShu_Bold.ttf");
        }
        return l;
    }

    /**
     * "My Font" -> "myfont.ttf" (same sanitising as the original).
     * Tên không còn ký tự a-z0-9 nào (vd. chữ Hán/Thái) trước đây ra ".ttf" rỗng và trùng nhau; nay dùng "font&lt;hash&gt;.ttf".
     */
    public static String deployedName(String fontName) {
        String s = fontName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (s.isEmpty()) s = "font" + Integer.toHexString(fontName.hashCode());
        return s + ".ttf";
    }

    private static String q(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** chosen[i] == null/empty means "leave slot i alone". Returns null when nothing is chosen. */
    public static String buildBlock(String pkg, String[] chosen) {
        StringBuilder calls = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (int slot = 0; slot < 3; slot++) {
            if (chosen[slot] == null || chosen[slot].isEmpty()) continue;
            String file = deployedName(chosen[slot]);
            for (String game : slotTargets(pkg, slot)) {
                if (seen.add(game)) calls.append("  ov(").append(q(game)).append(", ").append(q(file)).append(")\n");
            }
        }
        if (calls.length() == 0) return null;
        return BEGIN + "\n"
                + "pcall(function()\n"
                + "  local dir = MEngine.GetFileSystemBasePath('LocalData')\n"
                + "  local copied = {}\n"
                + "  local function ov(game, file)\n"
                + "    pcall(function()\n"
                + "      if not copied[file] then\n"
                + "        MPatch.CopyFileSync(dir .. '/' .. file, dir .. '/Fonts/' .. file)\n"
                + "        copied[file] = true\n"
                + "      end\n"
                + "      MUI.OverrideFontCache(game, file, 0)\n"
                + "    end)\n"
                + "  end\n"
                + calls
                + "end)\n"
                + END + "\n";
    }

    /**
     * Lua ép MEngine.SetFrameLimit luôn nhận giá trị {@code fps} (bỏ qua mức chọn trong game) và tắt vsync.
     * Dùng marker riêng nên sống chung được với khối font.
     */
    public static String buildFpsBlock(int fps) {
        return FPS_BEGIN + "\n"
                + "pcall(function()\n"
                + "  if MEngine and MEngine.SetFrameLimit then\n"
                + "    local orig = MEngine.SetFrameLimit\n"
                + "    MEngine.SetFrameLimit = function(v) return orig(" + fps + ") end\n"
                + "    orig(" + fps + ")\n"
                + "  end\n"
                + "  if MEngine and MEngine.EnableVsync then MEngine.EnableVsync(false) end\n"
                + "end)\n"
                + FPS_END + "\n";
    }

    public static boolean hasFpsBlock(String lua) {
        return lua != null && lua.contains(FPS_BEGIN);
    }

    public static String stripFpsBlocks(String lua) {
        return strip(lua, FPS_BEGIN, FPS_END);
    }

    public static String appendFpsBlock(String lua, String block) {
        return stripFpsBlocks(lua).trim() + "\n" + block;
    }

    public static boolean hasBlock(String lua) {
        return lua != null && lua.contains(BEGIN);
    }

    /** Removes every FONTPATCH block (non-greedy, so other patches in the same script survive). */
    public static String stripBlocks(String lua) {
        return strip(lua, BEGIN, END);
    }

    private static String strip(String lua, String begin, String end) {
        if (lua == null) return "";
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (true) {
            int b = lua.indexOf(begin, pos);
            if (b < 0) break;
            int e = lua.indexOf(end, b);
            if (e < 0) break; // malformed: keep the rest untouched
            sb.append(lua, pos, b);
            pos = e + end.length();
            if (pos < lua.length() && lua.charAt(pos) == '\n') pos++;
        }
        sb.append(lua.substring(pos));
        return sb.toString();
    }

    public static String appendBlock(String lua, String block) {
        String base = stripBlocks(lua).trim();
        return base + "\n" + block;
    }
}
