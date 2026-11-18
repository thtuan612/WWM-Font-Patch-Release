package local.wwm.fontpatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hỏi GitHub bản release mới nhất của repo Phát hành, và so sánh số phiên bản. */
final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/thtuan612/WWM-Font-Patch-Release/releases/latest";

    /** version: số phiên bản (vd 1.1.5); url/sha256: APK và mã SHA-256 (null nếu không có hoặc không đáng tin). */
    static final class Release {
        String version, url, sha256;
    }

    private UpdateChecker() { }

    /** Ném lỗi khi không kết nối được hoặc không đọc được phiên bản. Chạy ở luồng nền. */
    static Release latest() throws Exception {
        String remote = null, url = null, sha = null;
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "WWM-FontPatch");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        }
        c.disconnect();
        JSONObject j = new JSONObject(bo.toString("UTF-8"));
        // phiên bản lấy từ tag release (v1.1.5 -> 1.1.5); APK là asset .apk đầu tiên (wwm-fontpatch-<ver>.apk)
        String tag = j.optString("tag_name", "").trim();
        if (tag.matches("[vV]?\\d+(\\.\\d+)*.*")) remote = tag.replaceFirst("^[vV]", "");
        JSONArray as = j.optJSONArray("assets");
        if (as != null) {
            for (int i = 0; i < as.length(); i++) {
                JSONObject a = as.getJSONObject(i);
                String an = a.optString("name");
                if (!an.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                if (remote == null) {
                    Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)+)\\.apk$").matcher(an);
                    if (m.find()) remote = m.group(1);
                }
                String du = a.optString("browser_download_url", "");
                if (UpdateGuard.isTrustedStartUrl(du)) {            // chỉ tải từ release của repo này
                    url = du;
                    sha = UpdateGuard.parseSha256(a.optString("digest", null));
                }
                break;
            }
        }
        if (remote == null) {                       // dự phòng: số phiên bản trong tên release
            Matcher m = Pattern.compile("v?(\\d+(?:\\.\\d+)+)\\s*$").matcher(j.optString("name"));
            if (m.find()) remote = m.group(1);
        }
        if (remote == null) throw new Exception("no version found");
        Release r = new Release();
        r.version = remote;
        r.url = url;
        r.sha256 = sha;
        return r;
    }

    static int[] verNums(String v) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)*)").matcher(v == null ? "" : v);
        if (!m.find()) return new int[0];
        String[] ps = m.group(1).split("\\.");
        int[] r = new int[ps.length];
        for (int i = 0; i < ps.length; i++) {
            try { r[i] = Integer.parseInt(ps[i]); } catch (Exception e) { r[i] = 0; }
        }
        return r;
    }

    /** > 0 nếu a mới hơn b (chỉ so phần số, bỏ hậu tố như _dev). */
    static int cmpVer(String a, String b) {
        int[] x = verNums(a);
        int[] y = verNums(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return p > q ? 1 : -1;
        }
        return 0;
    }
}
