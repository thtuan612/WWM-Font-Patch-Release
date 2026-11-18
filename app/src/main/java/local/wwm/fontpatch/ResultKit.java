package local.wwm.fontpatch;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.util.DisplayMetrics;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Hộp thoại kết quả (kèm log) và popup kết quả kiểm tra cập nhật; xếp trên UiKit. */
abstract class ResultKit extends UiKit {
    static final StringBuilder logBuf = new StringBuilder();
    /** state: 0 = success, 1 = failure, 2 = could not start (e.g. no permission yet). */
    void showResult(String action, int state, String message, Hints.Hint hint) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView ic = new ImageView(this);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (state == 0) {
            ic.setImageResource(R.drawable.seal);
        } else {
            ic.setImageResource(state == 1 ? R.drawable.ic_close : R.drawable.ic_adjust);
            ic.setColorFilter(state == 1 ? Pal.SEAL_RED : pal.sub);
        }
        final int tone = state == 0 ? pal.accent : state == 1 ? Pal.SEAL_RED : pal.sub;
        // khung màu: xanh = thành công, đỏ = thất bại, xám = chưa thực hiện
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER_HORIZONTAL);
        banner.setBackground(rounded((tone & 0x00FFFFFF) | 0x24000000, 20, 0));
        banner.setPadding(dp(14), dp(14), dp(14), dp(14));
        banner.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(48)));
        String head = state == 0 ? t("Thành công", "Success") : state == 1 ? t("Thất bại", "Failed") : t("Chưa thực hiện", "Not done");
        TextView ht = tv(head, 20, tone, true);
        banner.addView(ht, lpm(WRAP, WRAP, 0, 8, 0, 0));
        banner.addView(tv(action, 12, pal.sub, false), lpm(WRAP, WRAP, 0, 2, 0, 0));
        TextView m = tv(message, 14, pal.text, false);
        m.setGravity(Gravity.CENTER);
        banner.addView(m, lpm(MATCH, WRAP, 0, 10, 0, 0));
        c.addView(banner, lpm(MATCH, WRAP, 0, 0, 0, 0));

        if (hint != null) {
            LinearLayout hb = new LinearLayout(this);
            hb.setOrientation(LinearLayout.VERTICAL);
            hb.setBackground(rounded(pal.mxField, 16, 0));
            hb.setPadding(dp(12), dp(10), dp(12), dp(10));
            hb.addView(tv(t("Nguyên nhân", "Cause"), 12, Pal.SEAL_RED, true));
            hb.addView(tv(hint.cause, 13, pal.text, false), lpm(MATCH, WRAP, 0, 2, 0, 0));
            hb.addView(tv(t("Cách xử lý", "How to fix"), 12, pal.accent, true), lpm(WRAP, WRAP, 0, 8, 0, 0));
            for (int i = 0; i < hint.fixes.size(); i++)
                hb.addView(tv((i + 1) + ". " + hint.fixes.get(i), 13, pal.text, false), lpm(MATCH, WRAP, 0, 3, 0, 0));
            c.addView(hb, lpm(MATCH, WRAP, 0, 10, 0, 0));
        }

        final String logText;
        synchronized (logBuf) { logText = logBuf.toString().trim(); }
        if (!logText.isEmpty()) {
            TextView lv = tv("", 11, pal.sub, false);
            lv.setText(colorLog(logText));
            lv.setTypeface(Typeface.MONOSPACE);
            lv.setTextIsSelectable(true);
            lv.setPadding(dp(12), dp(10), dp(12), dp(10));
            lv.setBackground(rounded(pal.mxField, 16, 0));
            int lines = logText.split("\n").length;
            ScrollView sv = scroll(lv);
            c.addView(sv, lpm(MATCH, lines > 6 ? dp(hint != null ? 110 : 190) : WRAP, 0, 12, 0, 0));
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        final Dialog[] ref = new Dialog[1];
        TextView copy = mxButton(t("Sao chép log", "Copy log"), false);
        copy.setOnClickListener(v -> copyLog(logText));
        TextView close = mxButton(t("Đóng", "Close"), true);
        close.setOnClickListener(v -> closeDialog(ref[0]));
        btns.addView(mxButtons(copy, close), new LinearLayout.LayoutParams(MATCH, WRAP));
        c.addView(btns, lpm(MATCH, WRAP, 0, 20, 0, 0));
        // dialog tiến trình vừa tắt nên không fade nền lần nữa (tránh nháy)
        ref[0] = makeDialog(c, true, false);
        ref[0].show();
        if (state == 0) Anim.stamp(ic, 140);
        else if (state == 1) Anim.shake(ic, dp(8));
        Anim.stagger(c, 1, 0f, dp(10), 160);
    }
    /** Tô đỏ dòng lỗi, vàng dòng cảnh báo trong log. */
    CharSequence colorLog(String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String line : text.split("\n")) {
            int st = sb.length();
            sb.append(line).append('\n');
            String u = line.toUpperCase(Locale.ROOT);
            int col = 0;
            if (u.contains("LỖI") || u.contains("ERROR") || (u.contains("KHÔNG ") && u.contains("ĐƯỢC")) || u.contains("CANNOT") || u.contains("COULD NOT")) col = Pal.SEAL_RED;
            else if (u.contains("CẢNH BÁO") || u.contains("WARNING")) col = 0xFFD99A1E;
            else if (u.contains("ĐÃ GHI LẠI") || u.contains("WRITTEN BACK") || u.contains("ĐÃ GỠ") || u.contains("ĐÃ KHÔI PHỤC") || u.contains("RESTORED") || u.contains("REMOVED")) col = pal.accent;
            if (col != 0) sb.setSpan(new ForegroundColorSpan(col), st, sb.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
    void copyLog(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("log", "WWM Font Patch " + Games.appVersion(this) + " | Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") | " + Build.MANUFACTURER + " " + Build.MODEL + "\n" + text));
        Toast.makeText(this, t("Đã sao chép log", "Log copied"), Toast.LENGTH_SHORT).show();
    }
    /**
     * Kết quả kiểm tra cập nhật: popup cửa sổ nhỏ bám ngay nút "Kiểm tra". Không có nút bấm (kiểm tra ngầm
     * lúc mở app) thì rơi về hộp thoại giữa màn hình.
     */
    void showUpdateResult(View anchor, String head, String msg, String okLabel, Runnable ok) {
        if (anchor == null || !anchor.isAttachedToWindow()) {
            infoDialog(head, msg, okLabel, ok);
            return;
        }
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(14), dp(20), dp(10));
        TextView title = tv(head, 17, pal.text, true);
        c.addView(title);
        c.addView(tv(msg, 14, pal.sub, false), lpm(MATCH, WRAP, 0, 6, 0, 0));
        final PopupWindow[] ref = new PopupWindow[1];
        TextView close = mxButton(ok != null ? t("Để sau", "Later") : t("Đóng", "Close"), ok == null);
        close.setOnClickListener(v -> Anim.dismiss(ref[0]));
        LinearLayout btns;
        if (ok != null) {
            TextView go = mxButton(okLabel, true);
            go.setOnClickListener(v -> { Anim.dismiss(ref[0]); ok.run(); });
            btns = mxButtons(close, go);
        } else {
            btns = mxButtons(close);
        }
        c.addView(btns, lpm(MATCH, WRAP, 0, 14, 0, 0));
        ref[0] = showMxPopup(anchor, c, 250);
    }
}
