package local.wwm.fontpatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java. Chooses a hotfix row and appends/removes the font block. No network access. */
public final class HotfixEditor {
    public static final String META = "hotfix_metaglobal";
    private static final String SUFFIX = "global";
    private static final int MIN_LEN = 100;

    private HotfixEditor() {}

    public static String readLua(RowStore s, String id) throws Exception {
        return Codec.decodeString(Codec.xor(s.readRaw(id)));
    }

    public static void writeLua(RowStore s, String id, String lua) throws Exception {
        s.writeRaw(id, Codec.xor(Codec.encodeString(lua)));
    }

    /** Keys of hotfix_id_d in stored order, or null when the meta row can't be understood. */
    static List<String> metaKeys(RowStore s) {
        try {
            byte[] raw = s.readRaw(META);
            if (raw.length == 0) return null;
            Object v = Codec.decodeValue(Codec.xor(raw));
            if (v instanceof Map) {
                Object d = ((Map<?, ?>) v).get("hotfix_id_d");
                if (d instanceof Map) {
                    List<String> k = new ArrayList<>();
                    for (Object o : ((Map<?, ?>) d).keySet()) k.add(String.valueOf(o));
                    return k;
                }
            } else if (v instanceof String) {
                // original app also accepted a JSON string here
                Matcher m = Pattern.compile("\"hotfix_id_d\"\\s*:\\s*\\{([^}]*)\\}").matcher((String) v);
                if (m.find()) {
                    List<String> k = new ArrayList<>();
                    Matcher km = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(m.group(1));
                    while (km.find()) k.add(km.group(1));
                    return k;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean usable(RowStore s, String id) {
        try {
            byte[] raw = s.readRaw(id);
            if (raw.length <= MIN_LEN) return false;
            return Codec.decodeString(Codec.xor(raw)).contains("local ");
        } catch (Exception e) {
            return false;
        }
    }

    /** Usable hotfix rows in the order the original app's offline fallback would try them. */
    public static List<String> candidates(RowStore s) throws Exception {
        List<String> out = new ArrayList<>();
        List<String> keys = metaKeys(s);
        List<String> all = s.hotfixIds();
        if (keys != null) {
            for (String k : keys) {
                String id = "hotfix_" + k + SUFFIX;
                if (all.contains(id) && usable(s, id)) out.add(id);
            }
        }
        if (out.isEmpty()) { // meta missing/unreadable: scan everything
            List<String> sorted = new ArrayList<>(all);
            Collections.sort(sorted);
            for (String id : sorted) {
                if (!id.equals(META) && id.endsWith(SUFFIX) && usable(s, id)) out.add(id);
            }
        }
        return out;
    }

    /** Removes our block from every hotfix row that has one. Returns how many rows were changed. */
    public static int uninstall(RowStore s) throws Exception {
        return removeBlocks(s, false);
    }

    /** Same for the FPS block. */
    public static int uninstallFps(RowStore s) throws Exception {
        return removeBlocks(s, true);
    }

    private static int removeBlocks(RowStore s, boolean fps) throws Exception {
        int n = 0;
        for (String id : s.hotfixIds()) {
            if (id.equals(META)) continue;
            byte[] raw = s.readRaw(id);
            if (raw.length == 0) continue;
            String lua = Codec.decodeString(Codec.xor(raw));
            if (fps ? PatchModel.hasFpsBlock(lua) : PatchModel.hasBlock(lua)) {
                writeLua(s, id, (fps ? PatchModel.stripFpsBlocks(lua) : PatchModel.stripBlocks(lua)).trim());
                n++;
            }
        }
        return n;
    }

    /** Returns the hotfix id that now carries the block. forcedId may be null/empty for auto. */
    public static String install(RowStore s, String block, String forcedId) throws Exception {
        return put(s, block, forcedId, false);
    }

    /** Returns the hotfix id that now carries the FPS block. */
    public static String installFps(RowStore s, String block, String forcedId) throws Exception {
        return put(s, block, forcedId, true);
    }

    private static String put(RowStore s, String block, String forcedId, boolean fps) throws Exception {
        removeBlocks(s, fps);
        String target;
        if (forcedId != null && !forcedId.trim().isEmpty()) {
            target = forcedId.trim();
            if (!s.hotfixIds().contains(target)) throw new IllegalStateException(
                    Lang.t("Không có hàng " + target + " trong db_mp", "db_mp has no row " + target));
        } else {
            List<String> c = candidates(s);
            if (c.isEmpty()) throw new IllegalStateException(Lang.t(
                    "Không tìm thấy hotfix nào dùng được trong db_mp (hãy mở game cho tới khi vào được màn hình chính rồi thử lại)",
                    "No usable hotfix found in db_mp (open the game until it reaches the main screen, then try again)"));
            target = c.get(0);
        }
        String lua = readLua(s, target);
        writeLua(s, target, fps ? PatchModel.appendFpsBlock(lua, block) : PatchModel.appendBlock(lua, block));
        String back = readLua(s, target);
        if (!back.contains(fps ? PatchModel.FPS_BEGIN : PatchModel.BEGIN)) throw new IllegalStateException(
                Lang.t("Ghi hotfix xong nhưng đọc lại không thấy patch", "Hotfix written but the patch was not found on read-back"));
        return target;
    }
}
