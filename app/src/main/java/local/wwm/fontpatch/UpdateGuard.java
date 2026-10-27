package local.wwm.fontpatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Kiểm tra an toàn cho luồng tự cập nhật. Thuần Java (không phụ thuộc Android)
 * để tools/test chạy được: xác thực URL/redirect, giới hạn kích thước, SHA-256.
 */
final class UpdateGuard {
    /** Trần dung lượng APK tải về. */
    static final long MAX_APK_BYTES = 150L * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;

    private static final String HOST = "github.com";
    private static final String CDN_SUFFIX = ".githubusercontent.com";
    private static final String RELEASE_PATH = "/thtuan612/WWM-Font-Patch-Release/releases/download/";

    private UpdateGuard() { }

    private static URI parse(String u) {
        try {
            return u == null ? null : new URI(u);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean httpsNoUserInfo(URI u) {
        return u != null && "https".equalsIgnoreCase(u.getScheme())
                && u.getUserInfo() == null && u.getHost() != null
                && (u.getPort() == -1 || u.getPort() == 443);
    }

    /** URL khởi đầu: chỉ file .apk trong mục release của chính repo này trên github.com. */
    static boolean isTrustedStartUrl(String url) {
        URI u = parse(url);
        if (!httpsNoUserInfo(u)) return false;
        if (!HOST.equalsIgnoreCase(u.getHost())) return false;
        String p = u.getRawPath();
        if (p == null || p.length() <= RELEASE_PATH.length() || p.contains("..") || p.contains("%")) return false;
        return p.regionMatches(true, 0, RELEASE_PATH, 0, RELEASE_PATH.length()) && p.endsWith(".apk");
    }

    /** Mọi bước (kể cả redirect): bắt buộc https và host là github.com hoặc *.githubusercontent.com. */
    static boolean isTrustedHop(URL url) {
        URI u = url == null ? null : parse(url.toString());
        if (!httpsNoUserInfo(u)) return false;
        String h = u.getHost().toLowerCase(Locale.ROOT);
        return h.equals(HOST) || (h.endsWith(CDN_SUFFIX) && h.length() > CDN_SUFFIX.length());
    }

    /** "sha256:<64 hex>" (trường digest của GitHub) -> hex thường; không đúng định dạng thì null. */
    static String parseSha256(String digest) {
        if (digest == null) return null;
        String d = digest.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("sha256:")) d = d.substring(7);
        else return null;
        return d.matches("[0-9a-f]{64}") ? d : null;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token ngẫu nhiên dùng một lần để chỉ nhận broadcast do chính app gửi. */
    static String newToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return hex(b);
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Tải url về dst: tự theo redirect (tối đa MAX_REDIRECTS) nhưng mỗi bước phải qua `trusted`,
     * giới hạn maxBytes (cả khi server không báo Content-Length), và so SHA-256 với expectedSha256.
     * Lỗi bất kỳ -> xoá dst và ném IOException.
     */
    static long fetch(String url, File dst, long maxBytes, String expectedSha256,
                      Predicate<URL> trusted, IntConsumer progress) throws IOException {
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}"))
            throw new IOException(Lang.t("Thiếu mã SHA-256 để xác minh", "Missing SHA-256 to verify against"));
        HttpURLConnection h = null;
        try {
            URL cur = new URL(url);
            for (int hop = 0; ; hop++) {
                if (!trusted.test(cur)) throw new IOException(Lang.t("Địa chỉ tải không đáng tin cậy", "Untrusted download address"));
                h = (HttpURLConnection) cur.openConnection();
                h.setInstanceFollowRedirects(false);          // tự xử lý để kiểm tra từng bước
                h.setConnectTimeout(15000);
                h.setReadTimeout(20000);
                h.setRequestProperty("User-Agent", "WWM-FontPatch");
                int code = h.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = h.getHeaderField("Location");
                    h.disconnect();
                    h = null;
                    if (loc == null || hop >= MAX_REDIRECTS) throw new IOException(Lang.t("Chuyển hướng không hợp lệ", "Bad redirect"));
                    cur = new URL(cur, loc);
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                break;
            }
            long total = h.getContentLengthLong();
            if (total > maxBytes) throw new IOException(Lang.t("File quá lớn: ", "File too large: ") + total);
            MessageDigest md = sha256();
            long got = 0;
            int last = -1;
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    got += n;
                    if (got > maxBytes) throw new IOException(Lang.t("File quá lớn", "File too large"));
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                    if (total > 0 && progress != null) {
                        int pc = (int) (got * 100 / total);
                        if (pc != last) { last = pc; progress.accept(pc); }
                    }
                }
            }
            if (total > 0 && got != total)
                throw new IOException(Lang.t("Tải thiếu dữ liệu: ", "Incomplete download: ") + got + "/" + total);
            if (!MessageDigest.isEqual(md.digest(), unhex(expectedSha256)))
                throw new IOException(Lang.t("SHA-256 không khớp, đã huỷ", "SHA-256 mismatch, aborted"));
            return got;
        } catch (IOException | RuntimeException e) {
            dst.delete();
            throw e;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    private static byte[] unhex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
