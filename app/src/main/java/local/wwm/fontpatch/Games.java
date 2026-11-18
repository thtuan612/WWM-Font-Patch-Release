package local.wwm.fontpatch;

import android.content.Context;

/** Các gói game được hỗ trợ và thông tin phiên bản. */
final class Games {
    static final String[] PKGS = {
            "com.netease.yysls", "com.netease.yyvn", "com.netease.yyslscn", "com.tencent.tmgp.yyslscn", "com.netease.yyslshmt"};
    /** Hiện trong bộ chọn game, cùng thứ tự với PKGS. */
    private static final String[] NAMES = {
            "Where Winds Meet", "Yến Vân Thập Lục Thanh", "燕云十六声", "燕云十六声 (Tencent)", "燕云十六声 (港澳台)"};

    private Games() { }

    static String name(String p) {
        for (int i = 0; i < PKGS.length; i++) if (PKGS[i].equals(p)) return NAMES[i];
        return p;
    }

    /** Mã vùng ngắn của từng gói game, hiện trên avatar khi chưa cài game. */
    static String region(String p) {
        if ("com.netease.yyvn".equals(p)) return "VN";
        if ("com.netease.yyslshmt".equals(p)) return "TW";
        if (p != null && p.contains("yyslscn")) return "CN";
        return "GL";
    }

    /** "v1.2.3" khi game đã cài, ngược lại null. */
    static String version(Context c, String p) {
        try {
            String v = c.getPackageManager().getPackageInfo(p, 0).versionName;
            return "v" + (v == null ? "?" : v);
        } catch (Exception e) {
            return null;
        }
    }

    /** Phiên bản của chính app, dạng "v1.1.5". */
    static String appVersion(Context c) {
        try {
            return "v" + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
