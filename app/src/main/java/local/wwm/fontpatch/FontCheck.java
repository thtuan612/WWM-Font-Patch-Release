package local.wwm.fontpatch;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Kiểm tra file font người dùng nhập và chuẩn hoá tên file. Thuần Java để tools/test chạy được. */
final class FontCheck {
    /** Trần dung lượng một file font nhập vào. */
    static final long MAX_BYTES = 64L * 1024 * 1024;

    private static final int TAG_TRUETYPE = 0x00010000;
    private static final int TAG_TRUE = 0x74727565;     // 'true'
    private static final int TAG_OTTO = 0x4F54544F;     // 'OTTO' (OpenType/CFF)

    private FontCheck() { }

    /**
     * Tên file lưu trong app: bỏ ký tự đường dẫn/điều khiển, luôn đuôi ".ttf" chữ thường,
     * phần tên rỗng (chỉ có dấu chấm/khoảng trắng) thì dùng "custom".
     */
    static String safeName(String displayName) {
        String n = displayName == null ? "" : displayName;
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            sb.append(c == '/' || c == '\\' || c < 0x20 || c == 0x7f ? '_' : c);
        }
        String s = sb.toString().trim();
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith(".ttf")) s = s.substring(0, s.length() - 4);
        s = s.trim();
        if (s.replace(".", "").isEmpty()) s = "custom";
        if (s.length() > 100) s = s.substring(0, 100);
        return s + ".ttf";
    }

    /** Ném IOException nếu f không phải font sfnt hợp lệ (header, bảng bắt buộc, offset trong phạm vi file) hoặc quá lớn. */
    static void validate(File f) throws IOException {
        long len = f.length();
        if (len > MAX_BYTES) throw new IOException(Lang.t("Font quá lớn (tối đa 64 MB)", "Font too large (64 MB max)"));
        if (len < 12 + 16) throw bad();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            int tag = in.readInt();
            if (tag != TAG_TRUETYPE && tag != TAG_TRUE && tag != TAG_OTTO) throw bad();
            int n = in.readUnsignedShort();
            in.readFully(new byte[6]);                      // searchRange, entrySelector, rangeShift
            if (n < 1 || n > 256 || 12L + 16L * n > len) throw bad();
            Set<String> tables = new HashSet<>();
            byte[] rec = new byte[16];
            for (int i = 0; i < n; i++) {
                in.readFully(rec);
                String name = new String(rec, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                long off = u32(rec, 8), size = u32(rec, 12);
                if (off + size > len) throw bad();          // bảng nằm ngoài file -> file cụt/hỏng
                tables.add(name);
            }
            boolean glyphs = (tables.contains("glyf") && tables.contains("loca"))
                    || tables.contains("CFF ") || tables.contains("CFF2");
            if (!tables.contains("head") || !tables.contains("maxp") || !tables.contains("cmap") || !glyphs) throw bad();
        } catch (java.io.EOFException e) {
            throw bad();
        }
    }

    private static long u32(byte[] b, int o) {
        return ((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFFL) << 16) | ((b[o + 2] & 0xFFL) << 8) | (b[o + 3] & 0xFFL);
    }

    private static IOException bad() {
        return new IOException(Lang.t("Không phải file font TTF hợp lệ", "Not a valid TTF font file"));
    }
}
