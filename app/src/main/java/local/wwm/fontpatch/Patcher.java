package local.wwm.fontpatch;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The whole install/uninstall/restore flow. Fully offline. */
final class Patcher {
    interface Logger { void log(String s); }

    static final String DB_REL = "LocalData/LocalDB/db_mp";

    private final Context ctx;
    private final Logger out;
    private final FontStore fonts;

    private final boolean autoStop;

    Patcher(Context ctx, Logger out, boolean autoStop) {
        this.ctx = ctx;
        this.out = out;
        this.autoStop = autoStop;
        this.fonts = new FontStore(ctx);
    }

    /** Stops the game only when it is really running (and the user allows it). */
    private void stopGame(String pkg, Backend b) {
        if (!autoStop) {
            out.log(Lang.t("Bỏ qua tắt game (đang tắt tuỳ chọn Tự tắt game)", "Skipping game stop (Auto-stop game is off)"));
            return;
        }
        try {
            Boolean running = b.isRunning(pkg);
            if (Boolean.FALSE.equals(running)) {
                out.log(Lang.t("Game không chạy — không cần tắt", "Game is not running — no need to stop it"));
                return;
            }
            b.forceStop(pkg);
            out.log(running == null ? Lang.t("Đã gửi lệnh tắt game", "Sent the stop command to the game")
                    : Lang.t("Đã tắt game đang chạy", "Stopped the running game"));
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        } catch (Exception e) {
            out.log(Lang.t("Không tắt được game: ", "Could not stop the game: ") + e.getMessage());
        }
    }

    /** Short preview of a long list so the log stays readable. */
    private static String brief(List<String> l) {
        int n = l.size();
        if (n <= 3) return n + " " + l;
        return n + " [" + l.get(0) + ", " + l.get(1) + ", " + l.get(2) + ", …]";
    }

    private File backupDir() {
        File d = new File(ctx.getFilesDir(), "backup");
        d.mkdirs();
        return d;
    }

    private SQLiteDatabase open(File f) {
        return SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
    }

    private static void copy(File a, File b) throws IOException {
        try (InputStream in = new FileInputStream(a); OutputStream o = new FileOutputStream(b)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        }
    }

    /** Pulls db_mp, makes a timestamped backup, returns the working copy. */
    private File pullDb(String pkg, Backend b, boolean backup) throws Exception {
        File stage = b.stageDir();
        File work = new File(stage, "db_mp_work");
        b.pull(pkg, DB_REL, work);
        out.log(Lang.t("Đã đọc db_mp (", "Read db_mp (") + work.length() + " byte)");
        for (String side : new String[]{"-wal", "-shm", "-journal"}) {
            try {
                if (b.exists(pkg, DB_REL + side)) {
                    throw new IllegalStateException(Lang.t(
                            "Không thể tiếp tục: db_mp" + side + " vẫn tồn tại. Hãy đóng hẳn game rồi thử lại.",
                            "Cannot continue: db_mp" + side + " still exists. Fully close the game and try again."));
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception ignored) {
                // Backend không hỗ trợ kiểm tra sidecar; tiếp tục để giữ tương thích.
            }
        }
        if (backup) {
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File bk = new File(backupDir(), pkg + "-" + ts + "-db_mp");
            copy(work, bk);
            out.log(Lang.t("Sao lưu: ", "Backup: ") + bk.getName());
        }
        return work;
    }

    private void cleanup(File work) {
        work.delete();
        new File(work.getPath() + "-journal").delete();
    }

    void install(String pkg, Backend b, String[] fontNames, String forcedId) throws Exception {
        String block = PatchModel.buildBlock(pkg, fontNames);
        if (block == null) throw new IllegalArgumentException(Lang.t("Chưa chọn font nào", "No font selected"));
        out.log(Lang.t("Phương thức: ", "Method: ") + b.name() + " | Game: " + pkg);
        stopGame(pkg, b);

        File stage = b.stageDir();
        Set<String> done = new HashSet<>();
        for (String name : fontNames) {
            if (name == null || name.isEmpty()) continue;
            String deployed = PatchModel.deployedName(name);
            if (!done.add(deployed)) continue;
            File f = fonts.materialize(name, stage);
            try {
                b.push(pkg, f, "LocalData/" + deployed);
                out.log(Lang.t("Đã chép font ", "Copied font ") + name + " → LocalData/" + deployed + " (" + f.length() + " byte)");
            } finally {
                f.delete();
            }
        }

        File work = pullDb(pkg, b, true);
        try {
            SQLiteDatabase db = open(work);
            try {
                List<String> cand = HotfixEditor.candidates(new SqlRowStore(db));
                out.log(Lang.t("Hotfix dùng được: ", "Usable hotfix: ") + brief(cand));
                String id = HotfixEditor.install(new SqlRowStore(db), block, forcedId);
                out.log(Lang.t("Đã gắn Lua patch font vào ", "Lua font patch attached to ") + id);
            } finally {
                db.close();
            }
            b.push(pkg, work, DB_REL);
            out.log(Lang.t("Đã ghi lại db_mp. Mở game để dùng font mới.", "db_mp written back. Open the game to use the new font."));
        } finally {
            cleanup(work);
        }
    }

    /** @return câu thay cho thông báo thành công mặc định khi không có gì để gỡ, ngược lại null. */
    String uninstall(String pkg, Backend b) throws Exception {
        out.log(Lang.t("Phương thức: ", "Method: ") + b.name() + " | Game: " + pkg);
        stopGame(pkg, b);
        File work = pullDb(pkg, b, true);
        try {
            SQLiteDatabase db = open(work);
            int n;
            try {
                n = HotfixEditor.uninstall(new SqlRowStore(db));
            } finally {
                db.close();
            }
            if (n == 0) {
                out.log(Lang.t("Không thấy patch font nào trong db_mp.", "No font patch found in db_mp."));
                return Lang.t("Không có patch font nào trong db_mp nên không có gì để gỡ.",
                        "There was no font patch in db_mp, so there was nothing to remove.");
            }
            b.push(pkg, work, DB_REL);
            out.log(Lang.t("Đã gỡ patch font khỏi " + n + " hotfix. (File font trong LocalData/ không bị xoá.)", "Font patch removed from " + n + " hotfix row(s). (Font files in LocalData/ were kept.)"));
            return null;
        } finally {
            cleanup(work);
        }
    }

    /** Gắn Lua ép FPS vào hotfix. Không đụng tới khối font. */
    void installFps(String pkg, Backend b, int fps, String forcedId) throws Exception {
        String block = PatchModel.buildFpsBlock(fps);
        out.log(Lang.t("Phương thức: ", "Method: ") + b.name() + " | Game: " + pkg + " | FPS: " + fps);
        stopGame(pkg, b);
        File work = pullDb(pkg, b, true);
        try {
            SQLiteDatabase db = open(work);
            try {
                String id = HotfixEditor.installFps(new SqlRowStore(db), block, forcedId);
                out.log(Lang.t("Đã gắn Lua mở khóa FPS (" + fps + ") vào ", "FPS unlock Lua (" + fps + ") attached to ") + id);
            } finally {
                db.close();
            }
            b.push(pkg, work, DB_REL);
            out.log(Lang.t("Đã ghi lại db_mp. Mở game để áp dụng.", "db_mp written back. Open the game to apply."));
        } finally {
            cleanup(work);
        }
    }

    /** @return câu thay cho thông báo thành công mặc định khi không có gì để gỡ, ngược lại null. */
    String uninstallFps(String pkg, Backend b) throws Exception {
        out.log(Lang.t("Phương thức: ", "Method: ") + b.name() + " | Game: " + pkg);
        stopGame(pkg, b);
        File work = pullDb(pkg, b, true);
        try {
            SQLiteDatabase db = open(work);
            int n;
            try {
                n = HotfixEditor.uninstallFps(new SqlRowStore(db));
            } finally {
                db.close();
            }
            if (n == 0) {
                out.log(Lang.t("Không thấy patch FPS nào trong db_mp.", "No FPS patch found in db_mp."));
                return Lang.t("Không có patch FPS nào trong db_mp nên không có gì để gỡ.",
                        "There was no FPS patch in db_mp, so there was nothing to remove.");
            }
            b.push(pkg, work, DB_REL);
            out.log(Lang.t("Đã gỡ patch FPS khỏi " + n + " hotfix. Game sẽ nghe lại cài đặt FPS của nó.",
                    "FPS patch removed from " + n + " hotfix row(s). The game follows its own FPS setting again."));
            return null;
        } finally {
            cleanup(work);
        }
    }

    /** true nếu db_mp (thường là một bản sao lưu) có hàng hotfix nào đang chứa patch font. */
    private boolean hasFontPatch(File dbFile) {
        try {
            SQLiteDatabase db = open(dbFile);
            try {
                RowStore s = new SqlRowStore(db);
                for (String id : s.hotfixIds()) {
                    if (id.equals(HotfixEditor.META)) continue;
                    try {
                        if (PatchModel.hasBlock(HotfixEditor.readLua(s, id))) return true;
                    } catch (Exception ignored) {   // một hàng đọc lỗi không được che các hàng còn lại
                    }
                }
            } finally {
                db.close();
            }
        } catch (Exception e) {
            out.log(Lang.t("Không kiểm tra được patch trong bản sao lưu: ", "Could not check the backup for a font patch: ") + e.getMessage());
        }
        return false;
    }

    /**
     * Puts the newest backup of this game's db_mp back.
     * @return câu cảnh báo thay cho thông báo mặc định khi bản sao lưu vẫn còn patch font, ngược lại null.
     */
    String restore(String pkg, Backend b) throws Exception {
        File[] fs = backupDir().listFiles();
        File best = null;
        if (fs != null) for (File f : fs) if (f.getName().startsWith(pkg + "-") && (best == null || f.getName().compareTo(best.getName()) > 0)) best = f;
        if (best == null) throw new IllegalStateException(Lang.t("Chưa có bản sao lưu nào cho ", "No backup yet for ") + pkg);
        // Bản sao lưu được tạo trước MỖI lần cài/gỡ, nên bản mới nhất có thể vẫn đang mang patch font.
        final boolean stillPatched = hasFontPatch(best);
        stopGame(pkg, b);
        File stage = b.stageDir();
        File tmp = new File(stage, "db_mp_restore");
        copy(best, tmp);
        tmp.setReadable(true, false);
        try {
            b.push(pkg, tmp, DB_REL);
        } finally {
            tmp.delete();
        }
        out.log(Lang.t("Đã khôi phục db_mp từ ", "Restored db_mp from ") + best.getName());
        if (!stillPatched) return null;
        out.log(Lang.t("CẢNH BÁO: bản sao lưu này vẫn có patch font.", "WARNING: this backup still contains a font patch."));
        return Lang.t("Đã khôi phục db_mp, nhưng bản sao lưu này vẫn còn patch font (nó được lưu trước lần cài/gỡ gần nhất). Bấm \"Gỡ patch\" nếu muốn bỏ.",
                "db_mp was restored, but this backup still contains a font patch (it was saved before the latest install/removal). Tap \"Remove patch\" to get rid of it.");
    }

    /** For the "list candidates" button. */
    void listCandidates(String pkg, Backend b) throws Exception {
        File work = pullDb(pkg, b, false);
        try {
            SQLiteDatabase db = open(work);
            try {
                RowStore s = new SqlRowStore(db);
                List<String> c = HotfixEditor.candidates(s);
                out.log(Lang.t("Tất cả hàng hotfix: ", "All hotfix rows: ") + s.hotfixIds().size());
                out.log(Lang.t("Ứng viên theo thứ tự thử: ", "Candidates in trial order: ") + brief(c));
                for (String id : s.hotfixIds()) {
                    String lua = HotfixEditor.readLua(s, id);
                    if (PatchModel.hasBlock(lua)) out.log(Lang.t("Đang có patch font ở: ", "Font patch present in: ") + id);
                    if (PatchModel.hasFpsBlock(lua)) out.log(Lang.t("Đang có patch FPS ở: ", "FPS patch present in: ") + id);
                }
            } finally {
                db.close();
            }
        } finally {
            cleanup(work);
        }
    }
}
