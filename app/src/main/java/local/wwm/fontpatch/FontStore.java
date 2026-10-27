package local.wwm.fontpatch;

import android.content.Context;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** .ttf files the user added (files/Fonts) plus any bundled in assets/fonts. Names are file names without ".ttf". */
final class FontStore {
    private final Context ctx;
    private final File dir;

    FontStore(Context ctx) {
        this.ctx = ctx;
        this.dir = new File(ctx.getFilesDir(), "Fonts");
        if (!dir.exists()) dir.mkdirs();
        File[] stale = dir.listFiles((d, n) -> n.startsWith(".import-"));     // file nhập dở dang lần trước
        if (stale != null) for (File s : stale) s.delete();
    }

    List<String> names() {
        List<String> l = new ArrayList<>();
        try {
            String[] a = ctx.getAssets().list("fonts");
            if (a != null) for (String s : a) if (s.toLowerCase().endsWith(".ttf")) l.add(s.substring(0, s.length() - 4));
        } catch (IOException ignored) {
        }
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) {
            String n = f.getName();
            if (n.toLowerCase().endsWith(".ttf")) {
                n = n.substring(0, n.length() - 4);
                if (!l.contains(n)) l.add(n);
            }
        }
        java.util.Collections.sort(l);
        return l;
    }

    void add(InputStream in, String fileName) throws IOException {
        // Luôn lưu đuôi chữ thường: phần còn lại của app dựng đường dẫn bằng name + ".ttf",
        // nên "ARIAL.TTF" lưu nguyên tên sẽ hiện trong danh sách nhưng không đọc/cài được.
        String fn = FontCheck.safeName(fileName);
        // Ghi ra file tạm, kiểm tra rồi mới đổi tên: file hỏng/quá lớn không bao giờ vào danh sách
        // và không làm hỏng font cùng tên đang dùng được.
        File tmp = File.createTempFile(".import-", ".tmp", dir);
        try {
            try (OutputStream out = new FileOutputStream(tmp)) {
                copy(in, out, FontCheck.MAX_BYTES);
            }
            FontCheck.validate(tmp);
            if (!tmp.renameTo(new File(dir, fn))) throw new IOException("cannot save " + fn);
        } finally {
            in.close();
            tmp.delete();       // không còn gì để xoá nếu đã đổi tên thành công
        }
    }

    /**
     * File người dùng đã thêm cho tên này. Tìm không phân biệt hoa thường để vẫn đọc được
     * các file đã lưu từ trước với đuôi ".TTF" (hệ thống file của Android phân biệt hoa thường).
     */
    private File userFile(String name) {
        File f = new File(dir, name + ".ttf");
        if (f.isFile()) return f;
        File[] fs = dir.listFiles();
        if (fs != null) for (File c : fs) if (c.getName().equalsIgnoreCase(name + ".ttf")) return c;
        return f;
    }

    /** Typeface for the preview line, or null when the file can't be loaded. */
    Typeface typeface(String name) {
        try {
            File user = userFile(name);
            if (user.isFile()) return Typeface.createFromFile(user);
            return Typeface.createFromAsset(ctx.getAssets(), "fonts/" + name + ".ttf");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Copies the font into {@code stage} (a folder the backend can read: the shell user used by Shizuku
     * cannot read this app's private files) and returns that copy.
     */
    File materialize(String name, File stage) throws IOException {
        File out = new File(stage, "font_" + name + ".ttf");
        File user = userFile(name);
        try (InputStream in = user.isFile() ? new FileInputStream(user) : ctx.getAssets().open("fonts/" + name + ".ttf");
             OutputStream os = new FileOutputStream(out)) {
            copy(in, os);
        }
        out.setReadable(true, false);
        out.setWritable(true, false);
        return out;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void copy(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException(Lang.t("Font quá lớn (tối đa 64 MB)", "Font too large (64 MB max)"));
            out.write(buf, 0, n);
        }
    }
}
